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
 * <p>The runtime root folder is named after a hash of the project location and the configuration
 * runtime key (a stable per-configuration id, see
 * {@code GiuanTomcatRunConfiguration#getRuntimeKey()}). The key does not depend on the
 * configuration name, so renaming a configuration keeps its instance and two configurations with
 * the same name never collide.
 */
public final class GiuanTomcatPaths {

  private static final String DEFAULT_RUNTIME_KEY = "run-config";
  private static final int HASH_LENGTH = 16;

  private GiuanTomcatPaths() {
  }

  public static File runtimeRoot(Project project, String runtimeKey) {
    return new File(System.getProperty("java.io.tmpdir"),
        GiuanTomcatConstants.TMP_ROOT_NAME + File.separator + hash(project, runtimeKey));
  }

  public static File catalinaBase(Project project, String runtimeKey) {
    return new File(runtimeRoot(project, runtimeKey),
        GiuanTomcatConstants.CATALINA_BASE_DIR_NAME);
  }

  public static File mergedRoot(Project project, String runtimeKey) {
    return new File(runtimeRoot(project, runtimeKey), GiuanTomcatConstants.MERGED_DIR_NAME);
  }

  private static String hash(Project project, String runtimeKey) {
    String name = runtimeKey == null || runtimeKey.trim().isEmpty()
        ? DEFAULT_RUNTIME_KEY : runtimeKey.trim();
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
