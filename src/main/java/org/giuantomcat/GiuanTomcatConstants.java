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
