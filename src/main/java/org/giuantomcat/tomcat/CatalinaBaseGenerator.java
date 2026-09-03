package org.giuantomcat.tomcat;

import org.giuantomcat.tomcat.ClasspathResolver.Classpath;
import org.giuantomcat.tomcat.ResourceConsolidator.Merged;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public final class CatalinaBaseGenerator {

  private static final String[] DEFAULT_CONF_FILES = {
      "web.xml",
      "catalina.properties",
      "tomcat-users.xml",
      "logging.properties"
  };

  private CatalinaBaseGenerator() {
  }

  public static void generate(String catalinaHome, String catalinaBase,
                              String webContent, String contextPath,
                              String httpPort, String shutdownPort,
                              boolean skipAnnotationScan,
                              Classpath classpath) throws IOException {
    File base = new File(catalinaBase);
    mkdirs(new File(base, "conf/Catalina/localhost"));
    mkdirs(new File(base, "logs"));
    mkdirs(new File(base, "work"));
    mkdirs(new File(base, "temp"));
    mkdirs(new File(base, "webapps"));

    copyDefaultConfFiles(catalinaHome, base);

    writeFile(new File(new File(base, "conf"), "server.xml"),
        buildServerXml(httpPort, shutdownPort));

    Merged merged = ResourceConsolidator.consolidate(catalinaBase, classpath);

    File contextFile = new File(new File(new File(base, "conf"), "Catalina/localhost"),
        contextFileName(contextPath));
    writeFile(contextFile,
        ContextXmlBuilder.build(webContent, merged.classesDir, merged.libDir,
            classpath.skippedJarNames));

    applySkipAnnotationScan(webContent, skipAnnotationScan);
  }

  private static final String SKIP_MARKER = "<!--[GiuanTomcat skip:annotation-scan]-->";
  private static final String METADATA_ATTR = " metadata-complete=\"true\"";
  private static final String LOG_PREFIX = "[GiuanTomcat] skip annotation scan: ";

  private static void applySkipAnnotationScan(String webContent, boolean skip) {
    File webXml = new File(new File(webContent, "WEB-INF"), "web.xml");
    if (!webXml.isFile()) {
      System.out.println(LOG_PREFIX + "WEB-INF/web.xml non trovato in " + webContent
          + ", nessuna modifica applicata.");
      return;
    }
    try {
      String content = new String(Files.readAllBytes(webXml.toPath()), StandardCharsets.UTF_8);
      String patched = skip ? addSkipAnnotationMarkers(content) : removeSkipAnnotationMarkers(content);
      if (patched != null && !patched.equals(content)) {
        Files.write(webXml.toPath(), patched.getBytes(StandardCharsets.UTF_8));
        System.out.println(LOG_PREFIX + (skip
            ? "aggiunto metadata-complete=\"true\" e <absolute-ordering/>"
            : "rimosso metadata-complete=\"true\" e <absolute-ordering/> aggiunti dal plugin")
            + " a " + webXml.getAbsolutePath());
      }
    } catch (IOException e) {
      System.out.println(LOG_PREFIX + "errore durante la modifica di " + webXml.getAbsolutePath()
          + ": " + e.getMessage());
    }
  }

  private static String addSkipAnnotationMarkers(String content) {
    int start = content.toLowerCase(java.util.Locale.ROOT).indexOf("<web-app");
    if (start < 0) {
      System.out.println(LOG_PREFIX + "<web-app> non trovato, nessuna modifica applicata.");
      return content;
    }
    int endTag = content.indexOf('>', start);
    if (endTag < 0) {
      return content;
    }
    String patched = content;
    boolean metadataAdded = false;
    String lowerOpen = content.substring(start, endTag).toLowerCase(java.util.Locale.ROOT);
    if (!lowerOpen.contains("metadata-complete")) {
      patched = patched.substring(0, start)
          + patched.substring(start, endTag) + METADATA_ATTR
          + patched.substring(endTag);
      endTag += METADATA_ATTR.length();
      metadataAdded = true;
    }
    if (patched.contains(SKIP_MARKER)) {
      return patched;
    }
    boolean hasOrdering = patched.toLowerCase(java.util.Locale.ROOT)
        .indexOf("<absolute-ordering", endTag) >= 0;
    if (!hasOrdering) {
      patched = patched.substring(0, endTag + 1)
          + "\n    " + SKIP_MARKER + "\n    <absolute-ordering/>"
          + patched.substring(endTag + 1);
    } else if (metadataAdded) {
      patched = patched.substring(0, endTag + 1)
          + "\n    " + SKIP_MARKER
          + patched.substring(endTag + 1);
    }
    return patched;
  }

  private static String removeSkipAnnotationMarkers(String content) {
    int marker = content.indexOf(SKIP_MARKER);
    if (marker < 0) {
      return content;
    }
    int lineStart = content.lastIndexOf('\n', marker);
    int cut = lineStart < 0 ? marker : lineStart;
    int after;
    int ordering = content.indexOf("<absolute-ordering", marker);
    if (ordering >= 0) {
      int orderingEnd = content.indexOf('>', ordering);
      after = orderingEnd < 0 ? marker + SKIP_MARKER.length() : orderingEnd + 1;
    } else {
      after = marker + SKIP_MARKER.length();
    }
    String patched = content.substring(0, cut) + content.substring(after);

    int start = patched.toLowerCase(java.util.Locale.ROOT).indexOf("<web-app");
    if (start >= 0) {
      int endTag = patched.indexOf('>', start);
      if (endTag >= 0) {
        String openTag = patched.substring(start, endTag);
        if (openTag.endsWith(METADATA_ATTR)) {
          patched = patched.substring(0, start)
              + openTag.substring(0, openTag.length() - METADATA_ATTR.length())
              + patched.substring(endTag);
        }
      }
    }
    return patched;
  }

  static String contextFileName(String contextPath) {
    String path = contextPath == null ? "" : contextPath.trim();
    if (path.isEmpty() || "/".equals(path)) {
      return "ROOT.xml";
    }
    if (path.startsWith("/")) {
      path = path.substring(1);
    }
    return path.endsWith(".xml") ? path : path + ".xml";
  }

  private static void copyDefaultConfFiles(String catalinaHome, File base) throws IOException {
    File homeConf = new File(catalinaHome, "conf");
    File baseConf = new File(base, "conf");
    for (String name : DEFAULT_CONF_FILES) {
      File src = new File(homeConf, name);
      File dst = new File(baseConf, name);
      if (src.isFile() && !dst.exists()) {
        Files.copy(src.toPath(), dst.toPath(), StandardCopyOption.REPLACE_EXISTING);
      }
    }
  }

  private static String buildServerXml(String httpPort, String shutdownPort) {
    return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
        "<Server port=\"" + shutdownPort + "\" shutdown=\"SHUTDOWN\">\n" +
        "  <Listener className=\"org.apache.catalina.startup.VersionLoggerListener\"/>\n" +
        "  <Listener className=\"org.apache.catalina.core.AprLifecycleListener\" SSLEngine=\"on\"/>\n" +
        "  <Listener className=\"org.apache.catalina.core.JreMemoryLeakPreventionListener\"/>\n" +
        "  <Listener className=\"org.apache.catalina.core.ThreadLocalLeakPreventionListener\"/>\n" +
        "  <GlobalNamingResources>\n" +
        "    <Resource name=\"UserDatabase\" auth=\"Container\"\n" +
        "              type=\"org.apache.catalina.UserDatabase\"\n" +
        "              description=\"User database that can be updated and saved\"\n" +
        "              factory=\"org.apache.catalina.users.MemoryUserDatabaseFactory\"\n" +
        "              pathname=\"conf/tomcat-users.xml\"/>\n" +
        "  </GlobalNamingResources>\n" +
        "  <Service name=\"Catalina\">\n" +
        "    <Connector port=\"" + httpPort + "\" protocol=\"HTTP/1.1\"\n" +
        "               connectionTimeout=\"20000\" redirectPort=\"8443\"/>\n" +
        "    <Engine name=\"Catalina\" defaultHost=\"localhost\">\n" +
        "      <Realm className=\"org.apache.catalina.realm.LockOutRealm\">\n" +
        "        <Realm className=\"org.apache.catalina.realm.UserDatabaseRealm\" resourceName=\"UserDatabase\"/>\n" +
        "      </Realm>\n" +
        "      <Host name=\"localhost\" appBase=\"webapps\" unpackWARs=\"true\" autoDeploy=\"true\">\n" +
        "      </Host>\n" +
        "    </Engine>\n" +
        "  </Service>\n" +
        "</Server>\n";
  }

  private static void mkdirs(File dir) {
    if (!dir.exists()) {
      dir.mkdirs();
    }
  }

  private static void writeFile(File file, String content) throws IOException {
    Files.write(file.toPath(), content.getBytes(StandardCharsets.UTF_8));
  }
}
