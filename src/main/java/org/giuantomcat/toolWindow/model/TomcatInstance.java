package org.giuantomcat.toolWindow.model;

import org.giuantomcat.GiuanTomcatConstants;

/**
 * A remote Tomcat instance managed through the Tomcat Manager API.
 *
 * <p>The instance is persisted as plain public fields so that
 * {@code com.intellij.util.xmlb.XmlSerializer} can serialize it without extra annotations. Secret
 * values (passwords/passphrases) are intentionally <b>not</b> stored here: they live in
 * {@code PasswordSafe}, keyed by {@link #id}.
 */
public final class TomcatInstance {

  public String id = "";
  public String name = "";
  public String host = "";
  public int httpPort = GiuanTomcatConstants.DEFAULT_HTTP_PORT_INT;
  public boolean https = false;
  public String managerPath = GiuanTomcatConstants.DEFAULT_MANAGER_PATH;
  public String managerUsername = "";

  /** Id of the {@code TomcatGroup} this instance belongs to; empty means "ungrouped". */
  public String groupId = "";

  public String sshHost = "";
  public int sshPort = GiuanTomcatConstants.DEFAULT_SSH_PORT;
  public String sshUser = "";
  public SshAuthMethod sshAuth = SshAuthMethod.PASSWORD;
  public String sshKeyPath = "";
  public String logFile = GiuanTomcatConstants.DEFAULT_LOG_FILE;
  public boolean sshEnabled = false;

  /** Whether the optional SSH log tailing has been configured for this instance. */
  public boolean hasSsh() {
    return sshEnabled;
  }

  public String displayName() {
    return name == null || name.trim().isEmpty() ? host : name;
  }

  public String endpointLabel() {
    return (https ? "https" : "http") + "://" + host + ":" + httpPort;
  }

  public String sshLabel() {
    String target = sshHost == null || sshHost.trim().isEmpty() ? host : sshHost;
    return "ssh://" + (sshUser == null ? "" : sshUser + "@") + target + ":" + sshPort;
  }
}
