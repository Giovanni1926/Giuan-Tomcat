package org.giuantomcat.toolWindow;

import com.intellij.icons.AllIcons;
import com.intellij.ui.IconManager;

import javax.swing.Icon;

/** Centralized icons for the server-manager tool window (see {@code STYLE.md}). */
public final class TomcatToolWindowIcons {

  private TomcatToolWindowIcons() {
  }

  public static final Icon INSTANCE = icon("/icons/tomcat.svg");
  public static final Icon GROUP = AllIcons.Nodes.Folder;

  public static final Icon REFRESH = AllIcons.Actions.Refresh;
  public static final Icon EXPAND_ALL = AllIcons.Actions.Expandall;
  public static final Icon COLLAPSE_ALL = AllIcons.Actions.Collapseall;

  public static final Icon ADD = AllIcons.General.Add;
  public static final Icon EDIT = AllIcons.Actions.EditSource;
  public static final Icon REMOVE = AllIcons.General.Remove;

  public static final Icon DEPLOY = AllIcons.Actions.Upload;
  public static final Icon LOGS = AllIcons.Debugger.Console;

  public static final Icon APP_START = AllIcons.Actions.Resume;
  public static final Icon APP_STOP = AllIcons.Actions.Suspend;
  public static final Icon APP_RELOAD = AllIcons.Actions.Refresh;
  public static final Icon APP_UNDEPLOY = AllIcons.General.Remove;

  public static final Icon ERROR = AllIcons.General.Error;

  private static Icon icon(String path) {
    return IconManager.getInstance().getIcon(path, TomcatToolWindowIcons.class);
  }
}
