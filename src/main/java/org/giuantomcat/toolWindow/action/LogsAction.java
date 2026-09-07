package org.giuantomcat.toolWindow.action;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import org.giuantomcat.toolWindow.ServerManagerPanel;
import org.giuantomcat.toolWindow.TomcatToolWindowIcons;
import org.jetbrains.annotations.NotNull;

/** Toolbar action that opens an SSH log console for the currently selected instance. */
public final class LogsAction extends AnAction {

  private final ServerManagerPanel myPanel;

  public LogsAction(ServerManagerPanel panel) {
    super("Logs", "Open SSH logs of the selected instance", TomcatToolWindowIcons.LOGS);
    myPanel = panel;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setEnabled(myPanel.hasLogsAvailable());
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    myPanel.openLogs();
  }
}
