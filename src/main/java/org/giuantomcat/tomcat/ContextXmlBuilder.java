package org.giuantomcat.tomcat;

import java.util.Set;

public final class ContextXmlBuilder {

  private ContextXmlBuilder() {
  }

  public static String build(String webContent, String mergedClassesDir, String mergedLibDir,
                             Set<String> skippedJarNames) {
    StringBuilder sb = new StringBuilder();
    sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
    boolean skip = skippedJarNames != null && !skippedJarNames.isEmpty();
    sb.append("<Context docBase=\"").append(escapeXml(webContent)).append("\"");
    if (skip) {
      sb.append(" reloadable=\"false\"");
      sb.append(" containerSciFilter=\"org\\.apache\\.tomcat\\.websocket\\.server\\.WsSci\"");
    }
    sb.append(">\n");
    if (skip) {
      String skipNames = escapeXml(String.join(",", skippedJarNames));
      sb.append("  <JarScanner")
          .append(" scanClassPath=\"false\"")
          .append(" scanBootstrapClassPath=\"false\"")
          .append(" scanAllDirectories=\"false\"")
          .append(" scanAllFiles=\"false\">\n");
      sb.append("    <JarScanFilter pluggabilitySkip=\"").append(skipNames)
          .append("\" tldSkip=\"").append(skipNames).append("\"/>\n");
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
