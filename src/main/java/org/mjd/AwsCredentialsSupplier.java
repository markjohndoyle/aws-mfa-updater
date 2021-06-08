package org.mjd;

import software.amazon.awssdk.services.sts.model.Credentials;

@FunctionalInterface
public interface AwsCredentialsSupplier {

  Credentials get(String region, String mfa, int sessionDurationSeconds);
}
