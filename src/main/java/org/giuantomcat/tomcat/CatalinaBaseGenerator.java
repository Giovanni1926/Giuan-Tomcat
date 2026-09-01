package org.giuantomcat.tomcat;

import org.giuantomcat.tomcat.ClasspathResolver.Classpath;

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

    File contextFile = new File(new File(new File(base, "conf"), "Catalina/localhost"),
        contextFileName(contextPath));
    writeFile(contextFile,
        ContextXmlBuilder.build(webContent, classpath.classesDirs, classpath.libJars));
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
