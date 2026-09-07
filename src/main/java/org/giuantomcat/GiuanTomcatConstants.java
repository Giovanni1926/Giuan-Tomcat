package org.giuantomcat;

/**
 * Shared constants for the Giuan Tomcat plugin.
 *
 * <p>Centralizes default configuration values and cross-cutting path/marker names so that no
 * literal is repeated across classes (see {@code STYLE.md}).
 */
public final class GiuanTomcatConstants {

  private GiuanTomcatConstants() {
  }

  /** Default context path used for a new run configuration. */
  public static final String DEFAULT_CONTEXT_PATH = "/myapp";

  /** Default HTTP port used for a new run configuration. */
  public static final String DEFAULT_HTTP_PORT = "8080";

  /** Default shutdown port used for a new run configuration. */
  public static final String DEFAULT_SHUTDOWN_PORT = "8005";

  /** Default HTTP port of a managed remote Tomcat instance (integer form). */
  public static final int DEFAULT_HTTP_PORT_INT = 8080;

  /** Default SSH port of a managed remote Tomcat instance. */
  public static final int DEFAULT_SSH_PORT = 22;

  /** Default Tomcat Manager text-API path relative to the instance host/port. */
  public static final String DEFAULT_MANAGER_PATH = "/manager/text";

  /** Default remote log file tailed over SSH (relative to the SSH working directory). */
  public static final String DEFAULT_LOG_FILE = "logs/catalina.out";

  /** Timeout (ms) for Tomcat Manager HTTP calls. */
  public static final int MANAGER_TIMEOUT_MS = 15_000;

  /** Timeout (ms) for SSH connect/authentication. */
  public static final int SSH_TIMEOUT_MS = 30_000;

  /** Number of lines prefixed by the remote {@code tail} when streaming logs. */
  public static final int LOG_TAIL_LINES = 200;

  /** PasswordSafe service prefix keyed by instance id. */
  public static final String CREDENTIAL_SERVICE_PREFIX = "giuan-tomcat:instance:";

  /** PasswordSafe key for the Tomcat Manager password. */
  public static final String CREDENTIAL_KEY_MANAGER = "manager";

  /** PasswordSafe key for the SSH password. */
  public static final String CREDENTIAL_KEY_SSH_PASSWORD = "ssh-password";

  /** PasswordSafe key for the SSH private-key passphrase. */
  public static final String CREDENTIAL_KEY_SSH_PASSPHRASE = "ssh-passphrase";

  /** File name of the application-level instances store under the IDE config folder. */
  public static final String INSTANCES_STORAGE_FILE = "giuan-tomcat-instances.xml";

  /** Name of the root folder under the system temp dir holding the generated environment. */
  public static final String TMP_ROOT_NAME = "giuan-tomcat";

  /** Name of the folder inside the runtime root holding the consolidated resources. */
  public static final String MERGED_DIR_NAME = "giuan-merged";

  /** Name of the folder inside the runtime root holding the generated CATALINA_BASE. */
  public static final String CATALINA_BASE_DIR_NAME = "catalina-base";

  /** Name of the manifest folder written by {@code ResourceConsolidator} inside the merged root. */
  public static final String MERGED_MANIFEST_NAME = "manifest";

  /** Header line marking a manifest written by the plugin. */
  public static final String MANIFEST_HEADER = "giuan-tomcat-merged-manifest";

  /** Version of the manifest format written by the plugin. */
  public static final String MANIFEST_VERSION = "1";

  /** Marker comment injected in {@code WEB-INF/web.xml} to locate plugin-added blocks. */
  public static final String SKIP_MARKER = "<!--[GiuanTomcat skip:annotation-scan]-->";

  /** Attribute appended to the {@code <web-app>} open tag to disable annotation scanning. */
  public static final String METADATA_ATTR = " metadata-complete=\"true\"";
}
