package org.giuantomcat.tomcat;

import com.intellij.openapi.project.Project;
import org.giuantomcat.GiuanTomcatConstants;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Computes the per-configuration paths under the system temp dir.
 *
 * <p>The runtime root folder is named after a hash of the project location and the run
 * configuration name, so two projects sharing a configuration with the same name never
 * collide and reuse each other's generated instance.
 */
public final class GiuanTomcatPaths {

  private static final String DEFAULT_CONFIG_NAME = "run-config";
  private static final int HASH_LENGTH = 16;

  private GiuanTomcatPaths() {
  }

  public static File runtimeRoot(Project project, String configName) {
    return new File(System.getProperty("java.io.tmpdir"),
        GiuanTomcatConstants.TMP_ROOT_NAME + File.separator + hash(project, configName));
  }

  public static File catalinaBase(Project project, String configName) {
    return new File(runtimeRoot(project, configName),
        GiuanTomcatConstants.CATALINA_BASE_DIR_NAME);
  }

  public static File mergedRoot(Project project, String configName) {
    return new File(runtimeRoot(project, configName), GiuanTomcatConstants.MERGED_DIR_NAME);
  }

  private static String hash(Project project, String configName) {
    String name = configName == null || configName.trim().isEmpty()
        ? DEFAULT_CONFIG_NAME : configName.trim();
    String input = project.getLocationHash() + "|" + name;
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] bytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
      StringBuilder hex = new StringBuilder(HASH_LENGTH);
      for (byte b : bytes) {
        hex.append(String.format("%02x", b));
        if (hex.length() >= HASH_LENGTH) {
          break;
        }
      }
      return hex.toString();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 not available", e);
    }
  }
}
