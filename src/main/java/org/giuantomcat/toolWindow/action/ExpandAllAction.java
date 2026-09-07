package org.giuantomcat.toolWindow.action;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import org.giuantomcat.toolWindow.ServerManagerPanel;
import org.giuantomcat.toolWindow.TomcatToolWindowIcons;
import org.jetbrains.annotations.NotNull;

/** Toolbar action that expands every instance entry. */
public final class ExpandAllAction extends AnAction {

  private final ServerManagerPanel myPanel;

  public ExpandAllAction(ServerManagerPanel panel) {
    super("Expand All", "Expand all instances", TomcatToolWindowIcons.EXPAND_ALL);
    myPanel = panel;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setEnabled(myPanel.hasInstances());
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    myPanel.expandAll();
  }
}
