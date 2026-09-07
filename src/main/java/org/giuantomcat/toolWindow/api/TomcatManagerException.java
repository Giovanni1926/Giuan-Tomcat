package org.giuantomcat.toolWindow.api;

/** Raised when a Tomcat Manager API call fails (transport error or {@code FAIL - ...} response). */
public final class TomcatManagerException extends Exception {

  public TomcatManagerException(String message) {
    super(message);
  }

  public TomcatManagerException(String message, Throwable cause) {
    super(message, cause);
  }
}
