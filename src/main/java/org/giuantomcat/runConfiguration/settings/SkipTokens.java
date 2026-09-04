package org.giuantomcat.runConfiguration.settings;

import java.util.Objects;
import java.util.Set;

/**
 * Persisted model of the per-jar granular skip. Each entry is a token shaped
 * {@code <module>|<entry>|tld} or {@code <module>|<entry>|pluggable}, where the <em>entry</em> is the
 * file name of a dependency jar. The annotation scan (classes) skip is NOT per-module but
 * global: it is the external {@code skipAnnotationScan} checkbox of the options.
 */
public final class SkipTokens {

  public static final String SEPARATOR = "|";

  public static final String FLAG_TLD = "tld";
  public static final String FLAG_PLUGGABLE = "pluggable";

  private SkipTokens() {
  }

  public static String token(String module, String entry, String flag) {
    return module + SEPARATOR + entry + SEPARATOR + flag;
  }

  public static String jarToken(String module, String jarName, String flag) {
    return token(module, jarName, flag);
  }

  public static boolean isJarSkippedTld(Set<String> tokens, String module, String jarName) {
    return tokens.contains(token(module, jarName, FLAG_TLD));
  }

  public static boolean isJarSkippedPluggable(Set<String> tokens, String module, String jarName) {
    return tokens.contains(token(module, jarName, FLAG_PLUGGABLE));
  }

  /** Decoded token (malformed or unknown entries → {@code null}). */
  public record Parsed(String module, String entry, String flag) {

    public static Parsed of(String token) {
      if (token == null) {
        return null;
      }
      String[] parts = token.split("\\|", -1);
      if (parts.length != 3 || parts[0].isEmpty() || parts[1].isEmpty() || parts[2].isEmpty()) {
        return null;
      }
      if (!Objects.equals(parts[2], FLAG_TLD)
          && !Objects.equals(parts[2], FLAG_PLUGGABLE)) {
        return null;
      }
      return new Parsed(parts[0], parts[1], parts[2]);
    }
  }
}
