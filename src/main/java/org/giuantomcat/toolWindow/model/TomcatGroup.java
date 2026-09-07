package org.giuantomcat.toolWindow.model;

/**
 * A named group used to organise the managed Tomcat instances in the server-manager tool window
 * (e.g. every Tomcat running on the same machine).
 *
 * <p>The group is persisted as plain public fields so that
 * {@code com.intellij.util.xmlb.XmlSerializer} can serialize it without extra annotations.
 */
public final class TomcatGroup {

  public String id = "";
  public String name = "";

  public String displayName() {
    return name;
  }
}
