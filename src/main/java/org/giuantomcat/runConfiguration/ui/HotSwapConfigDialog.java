package org.giuantomcat.runConfiguration.ui;

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.util.ui.FormBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;
import javax.swing.JPanel;
import java.io.File;

public class HotSwapConfigDialog extends DialogWrapper {

  private final TextFieldWithBrowseButton myDcevmJdkField;
  private final TextFieldWithBrowseButton myHotswapAgentField;

  public HotSwapConfigDialog(@Nullable String dcevmJdkPath,
                             @Nullable String hotswapAgentPath) {
    super(true);
    setTitle("HotSwap Configuration (DCEVM + hotswap-agent)");

    myDcevmJdkField = new TextFieldWithBrowseButton();
    myDcevmJdkField.setText(dcevmJdkPath == null ? "" : dcevmJdkPath);
    myDcevmJdkField.addBrowseFolderListener(null,
        FileChooserDescriptorFactory.createSingleFolderDescriptor()
            .withTitle("Select DCEVM JDK (Java 8)"));
    myDcevmJdkField.getTextField().setToolTipText(
        "A JDK 8 with DCEVM already installed as altjvm.");

    myHotswapAgentField = new TextFieldWithBrowseButton();
    myHotswapAgentField.setText(hotswapAgentPath == null ? "" : hotswapAgentPath);
    myHotswapAgentField.addBrowseFolderListener(null,
        FileChooserDescriptorFactory.createSingleFileDescriptor("jar")
            .withTitle("Select hotswap-agent.jar"));
    myHotswapAgentField.getTextField().setToolTipText(
        "Download hotswap-agent.jar from https://github.com/HotswapProjects/HotswapAgent/releases");

    init();
  }

  @Nullable
  public String getDcevmJdkPath() {
    return trimmedOrNull(myDcevmJdkField.getText());
  }

  @Nullable
  public String getHotswapAgentPath() {
    return trimmedOrNull(myHotswapAgentField.getText());
  }

  @Override
  protected @Nullable JComponent createCenterPanel() {
    return FormBuilder.createFormBuilder()
        .addLabeledComponent("DCEVM JDK", myDcevmJdkField)
        .addLabeledComponent("hotswap-agent.jar", myHotswapAgentField)
        .getPanel();
  }

  @Override
  protected @Nullable ValidationInfo doValidate() {
    String jdkPath = getDcevmJdkPath();
    if (jdkPath != null && !isValidJdkHome(jdkPath)) {
      return new ValidationInfo("The selected directory is not a valid JDK (bin/java missing).",
          myDcevmJdkField);
    }
    String agentPath = getHotswapAgentPath();
    if (agentPath != null && !new File(agentPath).isFile()) {
      return new ValidationInfo("The selected file is not a valid jar.", myHotswapAgentField);
    }
    return super.doValidate();
  }

  @Override
  public @Nullable JComponent getPreferredFocusedComponent() {
    return myDcevmJdkField.getTextField();
  }

  private static boolean isValidJdkHome(String home) {
    File bin = new File(home, "bin");
    return new File(bin, "java").isFile() || new File(bin, "java.exe").isFile();
  }

  private static @Nullable String trimmedOrNull(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }
}
