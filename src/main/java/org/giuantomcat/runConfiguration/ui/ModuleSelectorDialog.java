package org.giuantomcat.runConfiguration.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;
import java.util.Set;

/** Modal popup to select the modules and configure the granular 3-column skips. */
public class ModuleSelectorDialog extends DialogWrapper {

  private final ClasspathModulesPanel myPanel;

  public ModuleSelectorDialog(Project project, Set<String> moduleNames, Set<String> skipTokens) {
    super(true);
    setTitle("Application modules (classpath & scan skips)");
    myPanel = new ClasspathModulesPanel(project);
    myPanel.setState(moduleNames, skipTokens);
    setResizable(true);
    init();
  }

  public Set<String> getModuleNames() {
    return myPanel.getState().moduleNames;
  }

  public Set<String> getSkipTokens() {
    return myPanel.getState().skipTokens;
  }

  @Override
  protected @Nullable JComponent createCenterPanel() {
    return myPanel.getComponent();
  }

  @Override
  public @Nullable JComponent getPreferredFocusedComponent() {
    return myPanel.getAvailableTree();
  }
}
