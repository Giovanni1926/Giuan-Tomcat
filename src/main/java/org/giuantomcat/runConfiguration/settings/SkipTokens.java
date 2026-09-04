package org.giuantomcat.runConfiguration.settings;

import java.util.Objects;
import java.util.Set;

/**
 * Modello persistito del skip granulare per-jar. Ogni voce è un token nella forma
 * {@code <module>|<entry>|tld} oppure {@code <module>|<entry>|pluggable}, dove l'<em>entry</em> è il
 * nome file di un jar di dipendenza. Lo skip dell'annotation scan (classes) NON è per-modulo ma
 * globale: è la spunta esterna {@code skipAnnotationScan} delle opzioni.
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

  /** Token decodificato (voci malformate o sconosciute → {@code null}). */
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
