package org.giuantomcat.toolWindow.api;

import com.intellij.util.io.HttpRequests;
import org.giuantomcat.GiuanTomcatConstants;
import org.giuantomcat.toolWindow.model.TomcatApplication;
import org.giuantomcat.toolWindow.model.TomcatInstance;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Client for the Tomcat Manager text API ({@code /manager/text}).
 *
 * <p>List of supported commands: {@code list}, {@code start}, {@code stop}, {@code reload},
 * {@code undeploy} (GET) and {@code deploy} (PUT upload of a local WAR). Authentication is HTTP
 * Basic with a user holding the {@code manager-script} role.
 */
public final class TomcatManagerClient {

  private static final String COMMAND_LIST = "/list";
  private static final String COMMAND_DEPLOY = "/deploy";
  private static final String COMMAND_START = "/start";
  private static final String COMMAND_STOP = "/stop";
  private static final String COMMAND_RELOAD = "/reload";
  private static final String COMMAND_UNDEPLOY = "/undeploy";
  private static final String FAIL_PREFIX = "FAIL - ";
  private static final int BUFFER_SIZE = 8192;

  private final String myBaseUrl;
  private final String myAuthorization;

  public TomcatManagerClient(TomcatInstance instance, String managerPassword) {
    myBaseUrl = buildBaseUrl(instance);
    myAuthorization = basicAuth(instance.managerUsername, managerPassword == null ? "" : managerPassword);
  }

  @NotNull
  public List<TomcatApplication> list() throws TomcatManagerException {
    String output = get(COMMAND_LIST);
    return parseList(output);
  }

  public void start(String contextPath) throws TomcatManagerException {
    get(command(COMMAND_START, contextPath));
  }

  public void stop(String contextPath) throws TomcatManagerException {
    get(command(COMMAND_STOP, contextPath));
  }

  public void reload(String contextPath) throws TomcatManagerException {
    get(command(COMMAND_RELOAD, contextPath));
  }

  public void undeploy(String contextPath) throws TomcatManagerException {
    get(command(COMMAND_UNDEPLOY, contextPath));
  }

  public void deploy(String contextPath, File war) throws TomcatManagerException {
    upload(war, command(COMMAND_DEPLOY, contextPath));
  }

  // ---- HTTP ----

  private String get(String path) throws TomcatManagerException {
    try {
      String response = HttpRequests.request(myBaseUrl + path)
          .accept("text/plain")
          .connectTimeout(GiuanTomcatConstants.MANAGER_TIMEOUT_MS)
          .readTimeout(GiuanTomcatConstants.MANAGER_TIMEOUT_MS)
          .tuner(connection -> connection.setRequestProperty("Authorization", myAuthorization))
          .connect(request -> request.readString());
      if (response == null) {
        throw new TomcatManagerException("Empty response from Tomcat Manager.");
      }
      if (response.startsWith(FAIL_PREFIX)) {
        throw new TomcatManagerException(response.substring(FAIL_PREFIX.length()).trim());
      }
      return response;
    } catch (TomcatManagerException e) {
      throw e;
    } catch (IOException e) {
      throw new TomcatManagerException("Cannot reach Tomcat Manager at " + myBaseUrl + path
          + ": " + e.getMessage(), e);
    }
  }

  private void upload(File war, String path) throws TomcatManagerException {
    HttpURLConnection connection = null;
    try {
      URL url = URI.create(myBaseUrl + path).toURL();
      connection = (HttpURLConnection) url.openConnection();
      connection.setRequestMethod("PUT");
      connection.setRequestProperty("Authorization", myAuthorization);
      connection.setRequestProperty("Content-Type", "application/octet-stream");
      connection.setRequestProperty("Content-Length", String.valueOf(war.length()));
      connection.setDoOutput(true);
      connection.setConnectTimeout(GiuanTomcatConstants.MANAGER_TIMEOUT_MS);
      connection.setReadTimeout(GiuanTomcatConstants.MANAGER_TIMEOUT_MS);

      try (FileInputStream in = new FileInputStream(war);
           java.io.OutputStream out = connection.getOutputStream()) {
        byte[] buffer = new byte[BUFFER_SIZE];
        int read;
        while ((read = in.read(buffer)) != -1) {
          out.write(buffer, 0, read);
        }
      }

      int code = connection.getResponseCode();
      InputStream stream = code >= 400 ? connection.getErrorStream() : connection.getInputStream();
      String response = readAll(stream);
      if (code >= 400) {
        throw new TomcatManagerException("Tomcat Manager returned HTTP " + code
            + (response.isEmpty() ? "" : ": " + response));
      }
      if (response.startsWith(FAIL_PREFIX)) {
        throw new TomcatManagerException(response.substring(FAIL_PREFIX.length()).trim());
      }
    } catch (TomcatManagerException e) {
      throw e;
    } catch (IOException | IllegalArgumentException e) {
      throw new TomcatManagerException("Deploy failed: " + e.getMessage(), e);
    } finally {
      if (connection != null) {
        connection.disconnect();
      }
    }
  }

  private static String readAll(InputStream stream) throws IOException {
    if (stream == null) {
      return "";
    }
    try (InputStream in = stream;
         java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
      byte[] buffer = new byte[BUFFER_SIZE];
      int read;
      while ((read = in.read(buffer)) != -1) {
        out.write(buffer, 0, read);
      }
      return out.toString(StandardCharsets.UTF_8.name());
    }
  }

  // ---- parsing ----

  private static List<TomcatApplication> parseList(String output) {
    List<TomcatApplication> applications = new ArrayList<>();
    String[] lines = output.split("\\r?\\n");
    for (String line : lines) {
      String trimmed = line.trim();
      if (trimmed.isEmpty() || trimmed.startsWith("OK -")) {
        continue;
      }
      String[] parts = trimmed.split(":");
      if (parts.length < 3) {
        continue;
      }
      String contextPath = decode(parts[0]);
      boolean running = "running".equalsIgnoreCase(parts[1]);
      int sessions = parseInt(parts[2]);
      String displayName = parts.length > 3 ? parts[3] : "";
      applications.add(new TomcatApplication(contextPath, running, sessions, displayName));
    }
    return applications;
  }

  // ---- helpers ----

  private static String buildBaseUrl(TomcatInstance instance) {
    String scheme = instance.https ? "https" : "http";
    String path = instance.managerPath == null || instance.managerPath.isEmpty()
        ? GiuanTomcatConstants.DEFAULT_MANAGER_PATH
        : instance.managerPath;
    String base = scheme + "://" + instance.host + ":" + instance.httpPort + path;
    return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
  }

  private static String basicAuth(String username, String password) {
    String value = username + ":" + password;
    return "Basic " + Base64.getEncoder()
        .encodeToString(value.getBytes(StandardCharsets.UTF_8));
  }

  private static String command(String action, String contextPath) {
    return action + "?path=" + encodeContextPath(contextPath);
  }

  private static String encodeContextPath(String contextPath) {
    return URLEncoder.encode(contextPath, StandardCharsets.UTF_8)
        .replace("+", "%20")
        .replace("%2F", "/");
  }

  private static String decode(String value) {
    try {
      return URLDecoder.decode(value, StandardCharsets.UTF_8);
    } catch (IllegalArgumentException e) {
      return value;
    }
  }

  private static int parseInt(String value) {
    try {
      return Integer.parseInt(value.trim());
    } catch (NumberFormatException e) {
      return 0;
    }
  }
}
