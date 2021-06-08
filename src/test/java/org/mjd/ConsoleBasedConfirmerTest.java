package org.mjd;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.sts.model.Credentials;

final class ConsoleBasedConfirmerTest {

  private final Instant testInstant = Instant.now();

  @Test
  void whenGivenNominalArgsUpdaterShouldCreateCorrectEntriesInCredentialsFile() throws Exception {
    var testCreds = Credentials.builder()
                               .accessKeyId("verySecureAccessKey")
                               .secretAccessKey("verySecretAccessKey")
                               .sessionToken("ReadyRoomSession")
                               .expiration(testInstant)
                               .build();
    var fakeCredsPath = Paths.get(ClassLoader.getSystemResource("fakeCredentials").toURI());

    var credentialsIniUpdater = new CredentialsIniUpdater(fakeCredsPath);

    credentialsIniUpdater.update(testCreds, "PicardsProfile", "captain", "space", "mfa");

    verifyCredentials(fakeCredsPath);

  }

  private void verifyCredentials(java.nio.file.Path fakeCredsPath) throws IOException {
    var strings = Files.readAllLines(fakeCredsPath);
    assertThat(strings).contains("[space]");
    assertThat(strings).contains("aws_access_key_id = verySecureAccessKey");
    assertThat(strings).contains("aws_secret_access_key = verySecretAccessKey");
    assertThat(strings).contains("aws_session_token = ReadyRoomSession");
    assertThat(strings).contains("expires = " + testInstant);

    assertThat(strings).contains("[PicardsProfile]");
    assertThat(strings).contains("role_arn = captain");
    assertThat(strings).contains("region = mfa");
    assertThat(strings).contains("source_profile = space");
  }

}