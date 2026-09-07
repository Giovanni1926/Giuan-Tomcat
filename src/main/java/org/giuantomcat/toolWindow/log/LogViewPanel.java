package org.giuantomcat.toolWindow.log;

import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import org.giuantomcat.toolWindow.api.SshLogClient.TailHandle;
import org.jetbrains.annotations.NotNull;

import javax.swing.JComponent;

/**
 * A console tab streaming the remote Tomcat log over SSH. Cancels the tail when disposed (e.g.
 * when the user closes the tab).
 */
public final class LogViewPanel implements Disposable {

  private final ConsoleView myConsole;
  private TailHandle myTail;

  public LogViewPanel(Project project) {
    myConsole = TextConsoleBuilderFactory.getInstance().createBuilder(project).getConsole();
  }

  public JComponent getComponent() {
    return myConsole.getComponent();
  }

  public void setTail(TailHandle tail) {
    myTail = tail;
  }

  public void appendLine(String line) {
    myConsole.print(line + "\n", ConsoleViewContentType.NORMAL_OUTPUT);
  }

  public void appendError(String message) {
    myConsole.print(message + "\n", ConsoleViewContentType.ERROR_OUTPUT);
  }

  public void clear() {
    myConsole.clear();
  }

  @Override
  public void dispose() {
    if (myTail != null) {
      myTail.close();
      myTail = null;
    }
    myConsole.dispose();
  }

  @NotNull
  public ConsoleView getConsole() {
    return myConsole;
  }
}
