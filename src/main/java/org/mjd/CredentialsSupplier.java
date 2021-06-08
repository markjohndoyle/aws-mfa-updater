package org.mjd;

import java.io.Console;
import javax.inject.Singleton;
import picocli.CommandLine.Help.Ansi;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.iam.IamClient;
import software.amazon.awssdk.services.iam.model.ListMfaDevicesResponse;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.sts.model.Credentials;
import software.amazon.awssdk.services.sts.model.GetSessionTokenRequest;

@Singleton
final class CredentialsSupplier implements AwsCredentialsSupplier {

  private static final Console console = System.console();

  @Override
  public Credentials get(String region, String mfa, int sessionDurationSeconds) {
    try (var stsClient = StsClient.builder().region(Region.of(region)).httpClient(ApacheHttpClient.builder().build()).build();
         var iamClient = IamClient.builder().region(Region.AWS_GLOBAL).build()) {
      return getSessionTokenCredentials(stsClient, iamClient, mfa, sessionDurationSeconds);
    }
  }

  private Credentials getSessionTokenCredentials(StsClient stsClient, IamClient iamClient, String mfa, int sessionDuration) {
    var mfaSerial = getMfaSerialNumber(iamClient.listMFADevices());
    System.out.println(Ansi.AUTO.string("@|bold,cyan 🔑 Updating MFA session for serial: " + mfaSerial + "|@"));

    var sessionTokenRequest = GetSessionTokenRequest.builder()
                                                    .serialNumber(mfaSerial)
                                                    .tokenCode(mfa)
                                                    .durationSeconds(sessionDuration)
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
        // Assuming it's an ordered list here ^
        return mfaDevice.serialNumber();
      } else {
        return mfaDevices.mfaDevices().get(0).serialNumber();
      }
    }
    throw new IllegalArgumentException("No MFA devices registered.");
  }
}
