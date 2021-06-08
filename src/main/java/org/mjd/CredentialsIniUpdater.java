package org.mjd;

import java.io.IOException;
import java.nio.file.Paths;
import java.time.Instant;
import javax.inject.Singleton;
import org.ini4j.Wini;
import picocli.CommandLine.Help.Ansi;
import software.amazon.awssdk.services.sts.model.Credentials;

@Singleton
final class CredentialsIniUpdater implements AwsCredentialsUpdater {

  public static final String GENERATED_COMMENT_KEY = "# ⚙ Generated by AWS MFA updater at";

  private final Wini credentialsIni;

  CredentialsIniUpdater() throws IOException {
    credentialsIni = new Wini(Paths.get(System.getProperty("user.home"), ".aws", "credentials").toFile());
  }

  @Override
  public void update(Credentials credentials, String linkedProfileName, String roleArn, String mfaSectionName, String region) {
    try {
      updateMfaSection(credentialsIni, credentials, mfaSectionName);
      updateLinkedSection(credentialsIni, linkedProfileName, roleArn, region, mfaSectionName);
      saveUpdates(credentialsIni);
      System.out.println(Ansi.AUTO.string("@|bold,green ✓ Successfully updated credentials.|@"));
      System.out.println(Ansi.AUTO.string("@|bold,cyan 🛈 You can now use profile '" + linkedProfileName  + "' to access your aws services as '" + roleArn + "'|@"));
      System.out.println(Ansi.AUTO.string("@|bold,cyan For example 'aws s3 ls --profile " + linkedProfileName + "'|@"));
      System.out.println(Ansi.AUTO.string("@|bold,cyan This expires at " + credentials.expiration() + "'|@"));
    } catch (IOException e) {
      System.err.println(e.getMessage());
      throw new IllegalArgumentException();
    }
  }

  private void updateMfaSection(Wini credentialsIni, Credentials credentials, String mfaSectionName) {
    var mfaSection = credentialsIni.get(mfaSectionName);
    if(mfaSection == null) {
      System.out.println("🛈 No MFA section detected; will create one called '" + mfaSectionName + "'");
      mfaSection = credentialsIni.add(mfaSectionName);
    }
    mfaSection.remove(GENERATED_COMMENT_KEY);
    mfaSection.put(GENERATED_COMMENT_KEY, Instant.now().toString());
    mfaSection.put("aws_access_key_id", credentials.accessKeyId());
    mfaSection.put("aws_secret_access_key", credentials.secretAccessKey());
    mfaSection.put("aws_session_token", credentials.sessionToken());
    mfaSection.put("expires", credentials.expiration());
    System.out.println(Ansi.AUTO.string("@|bold,cyan 🔄 Added/Updated MFA section '" + mfaSectionName + "' in " + credentialsIni.getFile().getPath() + "|@"));
  }

  private void updateLinkedSection(Wini credentialsIni, String linkedProfileName, String roleArn, String region, String mfaSectionName) {
    var linkedSection = credentialsIni.get(linkedProfileName);
    if (linkedSection == null) {
      System.out.println("🛈 No linked profile section detected; will create one...");
      linkedSection = credentialsIni.add(linkedProfileName);
    }
    linkedSection.remove(GENERATED_COMMENT_KEY);
    linkedSection.put(GENERATED_COMMENT_KEY, Instant.now().toString());
    linkedSection.put("role_arn", roleArn);
    linkedSection.put("region", region);
    linkedSection.put("source_profile", mfaSectionName);
    System.out.println(Ansi.AUTO.string("@|bold,cyan 🔄 Added/Updated linked profile '" + linkedProfileName + "' in " + credentialsIni.getFile().getPath() + "|@"));
  }

  private void saveUpdates(Wini credentialsIni) throws IOException {
    credentialsIni.store();
  }
}