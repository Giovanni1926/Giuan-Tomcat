package org.giuantomcat.toolWindow.action;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import org.giuantomcat.toolWindow.ServerManagerPanel;
import org.giuantomcat.toolWindow.TomcatToolWindowIcons;
import org.jetbrains.annotations.NotNull;

/** Toolbar action that deploys a new application to the currently selected instance. */
public final class DeployAction extends AnAction {

  private final ServerManagerPanel myPanel;

  public DeployAction(ServerManagerPanel panel) {
    super("Deploy", "Deploy a new application to the selected instance", TomcatToolWindowIcons.DEPLOY);
    myPanel = panel;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setEnabled(myPanel.hasSelection());
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    myPanel.deploy();
  }
}
