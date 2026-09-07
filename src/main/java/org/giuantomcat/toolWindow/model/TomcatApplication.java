package org.giuantomcat.toolWindow.model;

/**
 * A deployed web application reported by the Tomcat Manager {@code list} command.
 *
 * <p>Runtime-only data: it is never persisted, only produced by parsing the Manager API output.
 */
public final class TomcatApplication {

  private final String contextPath;
  private final boolean running;
  private final int sessions;
  private final String displayName;

  public TomcatApplication(String contextPath, boolean running, int sessions, String displayName) {
    this.contextPath = contextPath;
    this.running = running;
    this.sessions = sessions;
    this.displayName = displayName;
  }

  public String getContextPath() {
    return contextPath;
  }

  public boolean isRunning() {
    return running;
  }

  public int getSessions() {
    return sessions;
  }

  public String getDisplayName() {
    return displayName;
  }
}
