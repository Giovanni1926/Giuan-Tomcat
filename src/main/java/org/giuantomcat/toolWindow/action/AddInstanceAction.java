package org.giuantomcat.toolWindow.action;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import org.giuantomcat.toolWindow.ServerManagerPanel;
import org.giuantomcat.toolWindow.TomcatToolWindowIcons;
import org.jetbrains.annotations.NotNull;

/** Toolbar action that opens the "Add instance" dialog. */
public final class AddInstanceAction extends AnAction {

  private final ServerManagerPanel myPanel;

  public AddInstanceAction(ServerManagerPanel panel) {
    super("Add Instance", "Add a new Tomcat instance", TomcatToolWindowIcons.ADD);
    myPanel = panel;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setEnabled(true);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    myPanel.addInstance();
  }
}
