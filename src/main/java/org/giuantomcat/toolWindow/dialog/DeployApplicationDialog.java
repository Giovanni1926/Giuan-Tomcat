package org.giuantomcat.toolWindow.dialog;

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.util.ui.FormBuilder;
import org.giuantomcat.GiuanTomcatConstants;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;
import javax.swing.JTextField;
import java.io.File;

/** Dialog to deploy a new web application: a local WAR uploaded under the chosen context path. */
public final class DeployApplicationDialog extends DialogWrapper {

  private final JTextField contextPathField =
      new JTextField(GiuanTomcatConstants.DEFAULT_CONTEXT_PATH);
  private final TextFieldWithBrowseButton warField = new TextFieldWithBrowseButton();

  public DeployApplicationDialog() {
    super(true);
    setTitle("Deploy Application");

    warField.addBrowseFolderListener(null,
        FileChooserDescriptorFactory.createSingleFileDescriptor("war")
            .withTitle("Select WAR file"));

    init();
  }

  @NotNull
  public String getContextPath() {
    String path = contextPathField.getText().trim();
    return path.startsWith("/") ? path : "/" + path;
  }

  @Nullable
  public File getWarFile() {
    String path = warField.getText().trim();
    return path.isEmpty() ? null : new File(path);
  }

  @Override
  protected @Nullable JComponent createCenterPanel() {
    return FormBuilder.createFormBuilder()
        .addLabeledComponent("Context path", contextPathField)
        .addLabeledComponent("WAR file", warField)
        .getPanel();
  }

  @Override
  protected @Nullable ValidationInfo doValidate() {
    if (contextPathField.getText().trim().isEmpty()) {
      return new ValidationInfo("Context path is required.", contextPathField);
    }
    File war = getWarFile();
    if (war == null || !war.isFile()) {
      return new ValidationInfo("A valid WAR file is required.", warField);
    }
    return super.doValidate();
  }

  @Override
  public @Nullable JComponent getPreferredFocusedComponent() {
    return contextPathField;
  }
}
