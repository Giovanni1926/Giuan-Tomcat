package org.giuantomcat.toolWindow.action;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import org.giuantomcat.toolWindow.ServerManagerPanel;
import org.jetbrains.annotations.NotNull;

/** Toolbar action that refreshes the snapshot of every configured instance. */
public final class RefreshAction extends AnAction {

  private final ServerManagerPanel myPanel;

  public RefreshAction(ServerManagerPanel panel) {
    super("Refresh", "Refresh all Tomcat instances", org.giuantomcat.toolWindow.TomcatToolWindowIcons.REFRESH);
    myPanel = panel;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setEnabled(myPanel.hasInstances());
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    myPanel.refreshAll();
  }
}
