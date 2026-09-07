package org.giuantomcat.toolWindow;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.ContentFactory;

public final class GiuanTomcatToolWindowFactory implements ToolWindowFactory {

  @Override
  public void createToolWindowContent(Project project, ToolWindow toolWindow) {
    ServerManagerPanel panel = new ServerManagerPanel(project, toolWindow);
    toolWindow.getContentManager().addContent(
        ContentFactory.getInstance().createContent(panel, "", false));
  }
}
