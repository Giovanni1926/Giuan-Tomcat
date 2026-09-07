package org.giuantomcat.toolWindow.action;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import org.giuantomcat.toolWindow.ServerManagerPanel;
import org.giuantomcat.toolWindow.TomcatToolWindowIcons;
import org.jetbrains.annotations.NotNull;

/** Toolbar action that collapses every instance entry. */
public final class CollapseAllAction extends AnAction {

  private final ServerManagerPanel myPanel;

  public CollapseAllAction(ServerManagerPanel panel) {
    super("Collapse All", "Collapse all instances", TomcatToolWindowIcons.COLLAPSE_ALL);
    myPanel = panel;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setEnabled(myPanel.hasInstances());
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    myPanel.collapseAll();
  }
}
