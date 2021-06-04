package org.mjd;

import io.quarkus.runtime.annotations.RegisterForReflection;
import java.io.Console;
import java.io.IOException;
import java.nio.file.Paths;
import java.time.Instant;
import org.ini4j.Wini;
import org.ini4j.spi.IniBuilder;
import org.ini4j.spi.IniFormatter;
import org.ini4j.spi.IniParser;
import picocli.CommandLine;
import picocli.CommandLine.Help.Ansi;
import picocli.CommandLine.Option;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.iam.IamClient;
import software.amazon.awssdk.services.iam.model.ListMfaDevicesResponse;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.sts.model.Credentials;
import software.amazon.awssdk.services.sts.model.GetSessionTokenRequest;

@CommandLine.Command
public final class App implements Runnable {

  private final Console console = System.console();

  @Option(names = {"-m", "--mfa"}, description = "MFA security code", required = true, interactive = true, arity = "0..1", prompt = "Enter MFA code")
  String mfa;

  @Option(names = {"-l", "--linkedRole"}, description = "The role ARN to link to the new MFA session.", required = true, interactive = true, arity = "0..1")
  String roleArn;

  @Option(names = {"-d", "--duration"}, description = "Duration of the session", defaultValue = "43200")
  int sessionDurationSeconds;

  @Option(names = {"-q", "--quiet" }, negatable = true)
  boolean quiet;

  @Option(names = {"-r", "--region"}, defaultValue = "eu-central-1")
  String region;

  @Option(names = {"-s", "--section"}, description = "The name of the section to use in the credentials file.", defaultValue = "mfa")
  String mfaSectionName;

  @Option(names = {"-lp", "--linkedProfile"}, description = "The name of the linked profile section to create in the credentials file.", defaultValue = "sg1")
  String linkedProfileName;

  @Override
  public void run() {
    System.out.println("AWS MFA credentials updater");
    confirmOkToEditCredentials();
    update(credentials());
  }

  private void confirmOkToEditCredentials() {
    if (!quiet) {
      System.out.println(
          Ansi.AUTO
              .string("@|bold,red,blink ⚠ This will update your credentials files. Do you wish to continue? What have got to lose? [Y/n] |@"));
      var answer = console.readLine();
      if (!(answer.isEmpty() || answer.equalsIgnoreCase("Y"))) {
        System.out.println("¯\\_(ツ)_/¯");
        System.exit(0);
      }
    }
  }

  private Credentials credentials() {
    try (var stsClient = StsClient.builder().region(Region.of(region)).httpClient(ApacheHttpClient.builder().build()).build();
         var iamClient = IamClient.builder().region(Region.AWS_GLOBAL).build()) {
      return getSessionTokenCredentials(stsClient, iamClient);
    }
  }

  private void update(Credentials credentials) {
    try {
      var credentialsIni = new Wini(Paths.get(System.getProperty("user.home"), ".aws", "credentials").toFile());
      updateMfaSection(credentialsIni, credentials);
      updateLinkedSection(credentialsIni);
      saveUpdates(credentialsIni);
      System.out.println(Ansi.AUTO.string("@|bold,green ✓ Successfully updated credentials.|@"));
      System.out.println(Ansi.AUTO.string("@|bold,cyan 🛈 You can now use profile '" + linkedProfileName  + "' to access your aws services as '" + roleArn + "'|@"));
      System.out.println(Ansi.AUTO.string("@|bold,cyan For example 'aws s3 ls --profile " + linkedProfileName + "'|@"));
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  private Credentials getSessionTokenCredentials(StsClient stsClient, IamClient iamClient) {
    var mfaSerial = getMfaSerialNumber(iamClient.listMFADevices());
    System.out.println(Ansi.AUTO.string("@|bold,cyan 🔑 Updating MFA session for serial: " + mfaSerial + "|@"));

    var sessionTokenRequest = GetSessionTokenRequest.builder()
                                                    .serialNumber(mfaSerial)
                                                    .tokenCode(mfa)
                                                    .durationSeconds(sessionDurationSeconds)
                                                    .build();
    return stsClient.getSessionToken(sessionTokenRequest).credentials();
  }

  private String getMfaSerialNumber(ListMfaDevicesResponse mfaDevices) {
    if (mfaDevices.hasMfaDevices()) {
      if (mfaDevices.mfaDevices().size() > 1) {
        System.out.println("You have multiple registered MFA devices, please select one:");
        var index = 0;
        for (var device : mfaDevices.mfaDevices()) {
          System.out.println(index + ": " + device.serialNumber());
          index++;
        }
        var id = Integer.parseInt(console.readLine());
        var mfaDevice = mfaDevices.mfaDevices().get(id);
        // Assuming it's an order list here ^
        return mfaDevice.serialNumber();
      } else {
        return mfaDevices.mfaDevices().get(0).serialNumber();
      }
    }
    throw new IllegalArgumentException("No MFA devices registered.");
  }

  private void updateMfaSection(Wini credentialsIni, Credentials credentials) {
    var mfaSection = credentialsIni.get(mfaSectionName);
    if(mfaSection == null) {
      System.out.println("🛈 No MFA section detected; will create one called '" + mfaSectionName + "'");
      mfaSection = credentialsIni.add(mfaSectionName);
    }
    mfaSection.put("# ⚙ Generated by AWS MFA updater at", Instant.now());
    mfaSection.put("aws_access_key_id", credentials.accessKeyId());
    mfaSection.put("aws_secret_access_key", credentials.secretAccessKey());
    mfaSection.put("aws_session_token", credentials.sessionToken());
    mfaSection.put("expires", credentials.expiration());
    System.out.println(Ansi.AUTO.string("@|bold,cyan 🔄 Added/Updated MFA section '" + mfaSectionName + "' in " + credentialsIni.getFile().getPath() + "|@"));
  }

  private void updateLinkedSection(Wini credentialsIni) {
    var linkedSection = credentialsIni.get(linkedProfileName);
    if (linkedSection == null) {
      System.out.println("🛈 No linked profile section detected; will create one...");
      linkedSection = credentialsIni.add(linkedProfileName);
    }
    linkedSection.put("# ⚙ Generated by AWS MFA updater at", Instant.now());
    linkedSection.put("role_arn", roleArn);
    linkedSection.put("region", region);
    linkedSection.put("source_profile", mfaSectionName);
    System.out.println(Ansi.AUTO.string("@|bold,cyan 🔄 Added/Updated linked profile '" + linkedProfileName + "' in " + credentialsIni.getFile().getPath() + "|@"));
  }

  private void saveUpdates(Wini credentialsIni) throws IOException {
    credentialsIni.store();
  }

  @RegisterForReflection(targets = {IniParser.class, IniBuilder.class, IniFormatter.class })
  public class IniParserReflectionRegister { }
}

