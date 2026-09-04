package org.giuantomcat.tomcat;

import java.util.Set;

public final class ContextXmlBuilder {

  private ContextXmlBuilder() {
  }

  public static String build(String webContent, String mergedClassesDir, String mergedLibDir,
                             Set<String> skippedTldJarNames,
                             Set<String> skippedPluggabilityJarNames) {
    Set<String> tldSkip = skippedTldJarNames == null ? Set.of() : skippedTldJarNames;
    Set<String> pluggabilitySkip =
        skippedPluggabilityJarNames == null ? Set.of() : skippedPluggabilityJarNames;
    boolean skip = !tldSkip.isEmpty() || !pluggabilitySkip.isEmpty();

    StringBuilder sb = new StringBuilder();
    sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
    sb.append("<Context docBase=\"").append(escapeXml(webContent)).append("\"");
    if (skip) {
      sb.append(" reloadable=\"false\"");
      sb.append(" containerSciFilter=\"org\\.apache\\.tomcat\\.websocket\\.server\\.WsSci\"");
    }
    sb.append(">\n");
    if (skip) {
      sb.append("  <JarScanner")
          .append(" scanClassPath=\"false\"")
          .append(" scanBootstrapClassPath=\"false\"")
          .append(" scanAllDirectories=\"false\"")
          .append(" scanAllFiles=\"false\">\n");
      sb.append("    <JarScanFilter");
      if (!pluggabilitySkip.isEmpty()) {
        sb.append(" pluggabilitySkip=\"")
            .append(escapeXml(String.join(",", pluggabilitySkip))).append("\"");
      }
      if (!tldSkip.isEmpty()) {
        sb.append(" tldSkip=\"").append(escapeXml(String.join(",", tldSkip))).append("\"");
      }
      sb.append("/>\n");
      sb.append("  </JarScanner>\n");
    }
    sb.append("  <Resources>\n");

    if (mergedClassesDir != null) {
      sb.append("    <PreResources className=\"org.apache.catalina.webresources.DirResourceSet\"\n");
      sb.append("                   base=\"").append(escapeXml(mergedClassesDir))
          .append("\" webAppMount=\"/WEB-INF/classes\"/>\n");
    }

    if (mergedLibDir != null) {
      sb.append("    <PreResources className=\"org.apache.catalina.webresources.DirResourceSet\"\n");
      sb.append("                   base=\"").append(escapeXml(mergedLibDir))
          .append("\" webAppMount=\"/WEB-INF/lib\"/>\n");
    }

    sb.append("  </Resources>\n");
    sb.append("</Context>\n");
    return sb.toString();
  }

  private static String escapeXml(String value) {
    if (value == null) {
      return "";
    }
    return value
        .replace("&", "&amp;")
        .replace("\"", "&quot;")
        .replace("<", "&lt;")
        .replace(">", "&gt;");
  }
}
