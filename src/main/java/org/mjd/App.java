package org.mjd;

import io.quarkus.runtime.annotations.RegisterForReflection;
import java.util.function.Function;
import org.ini4j.spi.IniBuilder;
import org.ini4j.spi.IniFormatter;
import org.ini4j.spi.IniParser;
import picocli.CommandLine;
import picocli.CommandLine.Help.Ansi;
import picocli.CommandLine.Option;
import software.amazon.awssdk.core.exception.SdkException;

@CommandLine.Command
public final class App implements Runnable {

  private final Function<String, Boolean> confirmationFunction;

  private final AwsCredentialsUpdater credentialsUpdater;

  private final AwsCredentialsSupplier credentialsSupplier;

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

  @Option(names = {"-lp", "--linkedProfile"}, description = "The name of the linked profile section to create in the credentials file.", defaultValue = "secured")
  String linkedProfileName;

  public App(Function<String, Boolean> confirmationFunction, AwsCredentialsUpdater credentialsUpdater, AwsCredentialsSupplier credentialsSupplier) {
    this.confirmationFunction = confirmationFunction;
    this.credentialsUpdater = credentialsUpdater;
    this.credentialsSupplier = credentialsSupplier;
  }


  @Override
  public void run() {
    System.out.println("AWS MFA credentials updater");
    try {
      confirmOkToEditCredentials();
      credentialsUpdater.update(credentialsSupplier.get(region, mfa, sessionDurationSeconds), linkedProfileName, roleArn, mfaSectionName, region);
    } catch (SdkException ex) {
      throw new IllegalStateException(ex.getMessage());
    }
  }

  private void confirmOkToEditCredentials() {
    if (!quiet) {
      if(!confirmationFunction.apply(Ansi.AUTO
          .string("@|bold,red,blink ⚠ This will update your credentials files. Do you wish to continue? What have got to lose? [Y/n] |@"))) {
        throw new IllegalStateException("¯\\_(ツ)_/¯ --- Not allowed to edit the credentials!");
      }
    }
  }

  @RegisterForReflection(targets = {IniParser.class, IniBuilder.class, IniFormatter.class })
  public class IniParserReflectionRegister {
    // GraalVM reflection config using Quarkus class.
  }
}

