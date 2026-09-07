package org.giuantomcat.toolWindow.action;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import org.giuantomcat.toolWindow.ServerManagerPanel;
import org.giuantomcat.toolWindow.TomcatToolWindowIcons;
import org.jetbrains.annotations.NotNull;

/** Toolbar action that edits the currently selected instance. */
public final class EditInstanceAction extends AnAction {

  private final ServerManagerPanel myPanel;

  public EditInstanceAction(ServerManagerPanel panel) {
    super("Edit Instance", "Edit the selected Tomcat instance", TomcatToolWindowIcons.EDIT);
    myPanel = panel;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setEnabled(myPanel.hasSelection());
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    myPanel.editInstance();
  }
}
