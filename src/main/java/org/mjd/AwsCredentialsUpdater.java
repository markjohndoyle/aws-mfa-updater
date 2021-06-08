package org.mjd;

import software.amazon.awssdk.services.sts.model.Credentials;

public interface AwsCredentialsUpdater {

  void update(Credentials credentials, String linkedProfileName, String roleArn, String mfaSectionName, String region);
}
