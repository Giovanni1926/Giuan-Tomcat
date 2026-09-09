package org.giuantomcat.runConfiguration.settings;

import java.util.*;

/**
 * Fixed catalogue and persisted model of the additional {@code <Connector>} attributes of the
 * generated {@code server.xml}.
 *
 * <p>Each entry is persisted as a token shaped {@code <name>=<value>}. The separator is a single
 * {@code '='} (split on the first occurrence) because the values of the relaxed-character
 * attributes ({@code relaxedPathChars}/{@code relaxedQueryChars}) legitimately contain the
 * {@code '|'} used elsewhere by {@link SkipTokens}. Only attributes from the fixed catalogue can
 * be written, so an attribute value never contains {@code '='}.
 */
public final class ConnectorProperties {

  public static final String SEPARATOR = "=";

  /** A selectable attribute of the HTTP/1.1 {@code <Connector>}. */
  public record Entry(String name, String suggestedValue) {
  }

  /**
   * Fixed catalogue shown in the dropdown. {@code port}, {@code protocol} and {@code redirectPort}
   * are intentionally absent because the plugin already manages them.
   */
  public static final List<Entry> CATALOG = List.of(
      new Entry("URIEncoding", "UTF-8"),
      new Entry("relaxedPathChars", "[]{}^`\"<>\\"),
      new Entry("relaxedQueryChars", "[]{}^`\"<>|\\"),
      new Entry("useBodyEncodingForURI", "false"),
      new Entry("connectionTimeout", "20000"),
      new Entry("maxThreads", "200"),
      new Entry("minSpareThreads", "10"),
      new Entry("maxConnections", "8192"),
      new Entry("acceptCount", "100"),
      new Entry("maxHttpHeaderSize", "8192"),
      new Entry("maxPostSize", "2097152"),
      new Entry("maxParameterCount", "10000"),
      new Entry("compression", "off"),
      new Entry("enableLookups", "false"),
      new Entry("encodedSolidusHandling", "reject"),
      new Entry("encodedReverseSolidusHandling", "decode"));

  private ConnectorProperties() {
  }

  /** The catalogue entry with the given name, or {@code null}. */
  public static Entry byName(String name) {
    if (name == null) {
      return null;
    }
    for (Entry entry : CATALOG) {
      if (entry.name().equals(name)) {
        return entry;
      }
    }
    return null;
  }

  public static String token(String name, String value) {
    return name + SEPARATOR + value;
  }

  /** Decodes a token into a {@code name=value} pair (malformed or unknown entries → {@code null}). */
  public record Parsed(String name, String value) {

    public static Parsed of(String token) {
      if (token == null) {
        return null;
      }
      int sep = token.indexOf(SEPARATOR);
      if (sep <= 0 || sep == token.length() - 1) {
        return null;
      }
      String name = token.substring(0, sep);
      String value = token.substring(sep + 1);
      if (byName(name) == null || value.isEmpty()) {
        return null;
      }
      return new Parsed(name, value);
    }
  }

  /** Decodes the stored tokens, keeping only valid catalogue entries in deterministic order. */
  public static Map<String, String> decode(Set<String> tokens) {
    Map<String, String> result = new LinkedHashMap<>();
    if (tokens == null) {
      return result;
    }
    // Rebuild following the catalogue order so the output is stable regardless of the set order.
    for (Entry entry : CATALOG) {
      for (String token : tokens) {
        Parsed parsed = Parsed.of(token);
        if (parsed != null && parsed.name().equals(entry.name())) {
          result.put(parsed.name(), parsed.value());
          break;
        }
      }
    }
    return result;
  }

  /** Ordered catalogue names, for the dropdown. */
  public static List<String> names() {
    List<String> names = new ArrayList<>(CATALOG.size());
    for (Entry entry : CATALOG) {
      names.add(entry.name());
    }
    return names;
  }
}
