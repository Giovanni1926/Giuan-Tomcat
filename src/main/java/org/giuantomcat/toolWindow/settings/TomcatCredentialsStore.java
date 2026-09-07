package org.giuantomcat.toolWindow.settings;

import com.intellij.credentialStore.CredentialAttributes;
import com.intellij.ide.passwordSafe.PasswordSafe;
import org.giuantomcat.GiuanTomcatConstants;
import org.jetbrains.annotations.Nullable;

/**
 * Reads and writes the secrets of a single instance from/to {@code PasswordSafe}.
 *
 * <p>Passwords are never logged nor kept in memory longer than needed; each secret is stored under
 * a dedicated key scoped by the instance id.
 */
public final class TomcatCredentialsStore {

  private final String myInstanceId;

  public TomcatCredentialsStore(String instanceId) {
    myInstanceId = instanceId;
  }

  @Nullable
  public String getManagerPassword() {
    return read(GiuanTomcatConstants.CREDENTIAL_KEY_MANAGER);
  }

  public void setManagerPassword(@Nullable String password) {
    write(GiuanTomcatConstants.CREDENTIAL_KEY_MANAGER, password);
  }

  @Nullable
  public String getSshPassword() {
    return read(GiuanTomcatConstants.CREDENTIAL_KEY_SSH_PASSWORD);
  }

  public void setSshPassword(@Nullable String password) {
    write(GiuanTomcatConstants.CREDENTIAL_KEY_SSH_PASSWORD, password);
  }

  @Nullable
  public String getSshPassphrase() {
    return read(GiuanTomcatConstants.CREDENTIAL_KEY_SSH_PASSPHRASE);
  }

  public void setSshPassphrase(@Nullable String passphrase) {
    write(GiuanTomcatConstants.CREDENTIAL_KEY_SSH_PASSPHRASE, passphrase);
  }

  private CredentialAttributes attributes(String key) {
    return new CredentialAttributes(
        GiuanTomcatConstants.CREDENTIAL_SERVICE_PREFIX + myInstanceId + ":" + key);
  }

  @Nullable
  private String read(String key) {
    return PasswordSafe.getInstance().getPassword(attributes(key));
  }

  private void write(String key, @Nullable String value) {
    PasswordSafe.getInstance().setPassword(attributes(key), value);
  }
}
