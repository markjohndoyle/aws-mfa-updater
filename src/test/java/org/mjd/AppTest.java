package org.mjd;

import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import picocli.CommandLine;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.sts.model.Credentials;

@ExtendWith(MockitoExtension.class)
final class AppTest {

  @Mock private Function<String, Boolean> mockConfirmFunc;
  @Mock private AwsCredentialsSupplier mockCredSupplier;
  @Mock private AwsCredentialsUpdater mockCredUpdater;
  private final Credentials testCredentials = Credentials.builder().build();

  @Test
  void appUpdatesCredentialsWithArgumentsPassedIn() {
    when(mockConfirmFunc.apply(anyString())).thenReturn(true);
    when(mockCredSupplier.get(anyString(), anyString(), anyInt())).thenReturn(testCredentials);

    var updaterApp = createAppWithArgs("-m 123456 -d 4200 -r theMoon -l aws:blah:123/Admin".split(" "));
    updaterApp.run();

    verify(mockCredUpdater).update(eq(testCredentials), eq("secured"), eq("aws:blah:123/Admin"), anyString(), anyString());
  }

  @Test
  void appThrowsIllegalStateWhenConfirmationDenied() {
    when(mockConfirmFunc.apply(anyString())).thenReturn(false);

    var updaterApp = createAppWithArgs("-m 123456 -d 4200 -r theMoon -l aws:blah:123/Admin".split(" "));

    assertThatIllegalStateException().isThrownBy(updaterApp::run);
  }

  @Test
  void appThrowsIllegalStateWhenCredentialsUpdaterFails() {
    when(mockConfirmFunc.apply(anyString())).thenReturn(true);
    when(mockCredSupplier.get(anyString(), anyString(), anyInt())).thenThrow(SdkException.builder().build());

    var updaterApp = createAppWithArgs("-m 123456 -d 4200 -r theMoon -l aws:blah:123/Admin".split(" "));

    assertThatIllegalStateException().isThrownBy(updaterApp::run);
  }

  private App createAppWithArgs(String... args) {
    var updaterApp = new App(mockConfirmFunc, mockCredUpdater, mockCredSupplier);
    new CommandLine(updaterApp).parseArgs(args);
    return updaterApp;
  }
}