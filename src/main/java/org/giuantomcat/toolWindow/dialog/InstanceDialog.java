package org.giuantomcat.toolWindow.dialog;

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.FormBuilder;
import org.giuantomcat.GiuanTomcatConstants;
import org.giuantomcat.toolWindow.model.SshAuthMethod;
import org.giuantomcat.toolWindow.model.TomcatInstance;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import java.io.File;
import java.util.UUID;

/**
 * Add/edit dialog for a managed Tomcat instance: Manager endpoint, credentials and SSH log
 * settings. Secret values are returned to the caller so that they can be stored in
 * {@code PasswordSafe}.
 */
public final class InstanceDialog extends DialogWrapper {

  private final JTextField nameField = new JTextField();
  private final JTextField hostField = new JTextField();
  private final JTextField httpPortField = new JTextField(Integer.toString(GiuanTomcatConstants.DEFAULT_HTTP_PORT_INT));
  private final javax.swing.JCheckBox httpsCheckBox = new javax.swing.JCheckBox("Use HTTPS");
  private final JTextField managerPathField = new JTextField(GiuanTomcatConstants.DEFAULT_MANAGER_PATH);
  private final JTextField managerUserField = new JTextField();
  private final JPasswordField managerPasswordField = new JPasswordField();

  private final com.intellij.ui.components.JBCheckBox sshEnabledCheckBox =
      new com.intellij.ui.components.JBCheckBox("Enable SSH (remote logs)");
  private final JTextField sshHostField = new JTextField();
  private final JTextField sshPortField = new JTextField(Integer.toString(GiuanTomcatConstants.DEFAULT_SSH_PORT));
  private final JTextField sshUserField = new JTextField();
  private final JComboBox<SshAuthMethod> sshAuthCombo = new JComboBox<>(SshAuthMethod.values());
  private final JPasswordField sshPasswordField = new JPasswordField();
  private final TextFieldWithBrowseButton sshKeyField = new TextFieldWithBrowseButton();
  private final JPasswordField sshPassphraseField = new JPasswordField();
  private final JTextField logFileField = new JTextField(GiuanTomcatConstants.DEFAULT_LOG_FILE);

  private final TomcatInstance myExisting;
  private TomcatInstance myResult;

  public InstanceDialog(@Nullable TomcatInstance existing,
                        @Nullable String managerPassword,
                        @Nullable String sshPassword,
                        @Nullable String sshPassphrase) {
    super(true);
    myExisting = existing;

    setTitle(existing == null ? "Add Tomcat Instance" : "Edit Tomcat Instance");

    sshAuthCombo.addActionListener(e -> updateAuthFields());
    sshEnabledCheckBox.addActionListener(e -> updateEnabledState());

    sshKeyField.addBrowseFolderListener(null,
        FileChooserDescriptorFactory.createSingleFileDescriptor()
            .withTitle("Select SSH private key"));

    if (existing != null) {
      nameField.setText(existing.name);
      hostField.setText(existing.host);
      httpPortField.setText(Integer.toString(existing.httpPort));
      httpsCheckBox.setSelected(existing.https);
      managerPathField.setText(existing.managerPath);
      managerUserField.setText(existing.managerUsername);
      managerPasswordField.setText(managerPassword == null ? "" : managerPassword);

      sshEnabledCheckBox.setSelected(existing.sshEnabled);
      sshHostField.setText(existing.sshHost);
      sshPortField.setText(Integer.toString(existing.sshPort));
      sshUserField.setText(existing.sshUser);
      sshAuthCombo.setSelectedItem(existing.sshAuth);
      sshPasswordField.setText(sshPassword == null ? "" : sshPassword);
      sshKeyField.setText(existing.sshKeyPath);
      sshPassphraseField.setText(sshPassphrase == null ? "" : sshPassphrase);
      logFileField.setText(existing.logFile);
    }

    updateEnabledState();
    updateAuthFields();
    init();
  }

  @Nullable
  public TomcatInstance getInstance() {
    return myResult;
  }

  @Nullable
  public String getManagerPassword() {
    return emptyToNull(new String(managerPasswordField.getPassword()));
  }

  @Nullable
  public String getSshPassword() {
    return emptyToNull(new String(sshPasswordField.getPassword()));
  }

  @Nullable
  public String getSshPassphrase() {
    return emptyToNull(new String(sshPassphraseField.getPassword()));
  }

  @Override
  protected @Nullable JComponent createCenterPanel() {
    JBLabel managerSection = new JBLabel("Tomcat Manager");
    managerSection.setFont(managerSection.getFont().deriveFont(java.awt.Font.BOLD));
    JBLabel sshSection = new JBLabel("SSH logs");
    sshSection.setFont(sshSection.getFont().deriveFont(java.awt.Font.BOLD));

    return FormBuilder.createFormBuilder()
        .addComponent(managerSection)
        .addLabeledComponent("Name", nameField)
        .addLabeledComponent("Host", hostField)
        .addLabeledComponent("HTTP port", httpPortField)
        .addLabeledComponent("", httpsCheckBox)
        .addLabeledComponent("Manager path", managerPathField)
        .addLabeledComponent("Manager username", managerUserField)
        .addLabeledComponent("Manager password", managerPasswordField)
        .addVerticalGap(8)
        .addComponent(sshSection)
        .addComponent(sshEnabledCheckBox)
        .addLabeledComponent("SSH host", sshHostField)
        .addLabeledComponent("SSH port", sshPortField)
        .addLabeledComponent("SSH username", sshUserField)
        .addLabeledComponent("Authentication", sshAuthCombo)
        .addLabeledComponent("SSH password", sshPasswordField)
        .addLabeledComponent("Private key", sshKeyField)
        .addLabeledComponent("Key passphrase", sshPassphraseField)
        .addLabeledComponent("Log file", logFileField)
        .getPanel();
  }

  @Override
  protected @Nullable ValidationInfo doValidate() {
    if (trimmed(hostField.getText()).isEmpty()) {
      return new ValidationInfo("Host is required.", hostField);
    }
    if (parsePort(httpPortField.getText()) < 0) {
      return new ValidationInfo("HTTP port must be a valid number.", httpPortField);
    }
    if (trimmed(managerUserField.getText()).isEmpty()) {
      return new ValidationInfo("Manager username is required.", managerUserField);
    }
    if (sshEnabledCheckBox.isSelected()) {
      if (parsePort(sshPortField.getText()) < 0) {
        return new ValidationInfo("SSH port must be a valid number.", sshPortField);
      }
      if (trimmed(sshUserField.getText()).isEmpty()) {
        return new ValidationInfo("SSH username is required.", sshUserField);
      }
      if (sshAuthCombo.getSelectedItem() == SshAuthMethod.KEY
          && (trimmed(sshKeyField.getText()).isEmpty() || !new File(sshKeyField.getText()).isFile())) {
        return new ValidationInfo("A valid SSH private key file is required.", sshKeyField);
      }
    }
    return super.doValidate();
  }

  @Override
  protected void doOKAction() {
    myResult = buildInstance();
    super.doOKAction();
  }

  @Override
  public @Nullable JComponent getPreferredFocusedComponent() {
    return hostField;
  }

  private TomcatInstance buildInstance() {
    TomcatInstance instance = myExisting == null ? new TomcatInstance() : copy(myExisting);
    if (myExisting == null) {
      instance.id = UUID.randomUUID().toString();
    }
    instance.name = trimmed(nameField.getText());
    instance.host = trimmed(hostField.getText());
    instance.httpPort = parsePort(httpPortField.getText());
    instance.https = httpsCheckBox.isSelected();
    instance.managerPath = trimmedOrDefault(managerPathField.getText(), GiuanTomcatConstants.DEFAULT_MANAGER_PATH);
    instance.managerUsername = trimmed(managerUserField.getText());

    String sshHost = trimmed(sshHostField.getText());
    instance.sshHost = sshHost.isEmpty() ? instance.host : sshHost;
    instance.sshPort = parsePort(sshPortField.getText());
    instance.sshUser = trimmed(sshUserField.getText());
    instance.sshAuth = (SshAuthMethod) sshAuthCombo.getSelectedItem();
    instance.sshKeyPath = trimmed(sshKeyField.getText());
    instance.logFile = trimmedOrDefault(logFileField.getText(), GiuanTomcatConstants.DEFAULT_LOG_FILE);
    instance.sshEnabled = sshEnabledCheckBox.isSelected();
    if (!instance.sshEnabled) {
      instance.sshUser = "";
    }
    return instance;
  }

  private static TomcatInstance copy(TomcatInstance source) {
    TomcatInstance instance = new TomcatInstance();
    instance.id = source.id;
    instance.name = source.name;
    instance.host = source.host;
    instance.httpPort = source.httpPort;
    instance.https = source.https;
    instance.managerPath = source.managerPath;
    instance.managerUsername = source.managerUsername;
    instance.sshEnabled = source.sshEnabled;
    instance.sshHost = source.sshHost;
    instance.sshPort = source.sshPort;
    instance.sshUser = source.sshUser;
    instance.sshAuth = source.sshAuth;
    instance.sshKeyPath = source.sshKeyPath;
    instance.logFile = source.logFile;
    return instance;
  }

  private void updateEnabledState() {
    boolean enabled = sshEnabledCheckBox.isSelected();
    sshHostField.setEnabled(enabled);
    sshPortField.setEnabled(enabled);
    sshUserField.setEnabled(enabled);
    sshAuthCombo.setEnabled(enabled);
    sshPasswordField.setEnabled(enabled);
    sshKeyField.setEnabled(enabled);
    sshPassphraseField.setEnabled(enabled);
    logFileField.setEnabled(enabled);
    if (enabled) {
      updateAuthFields();
    }
  }

  private void updateAuthFields() {
    if (!sshEnabledCheckBox.isSelected()) {
      sshPasswordField.setEnabled(false);
      sshKeyField.setEnabled(false);
      sshPassphraseField.setEnabled(false);
      return;
    }
    boolean key = sshAuthCombo.getSelectedItem() == SshAuthMethod.KEY;
    sshPasswordField.setEnabled(!key);
    sshKeyField.setEnabled(key);
    sshPassphraseField.setEnabled(key);
  }

  private static int parsePort(String value) {
    try {
      int port = Integer.parseInt(trimmed(value));
      return port >= 0 && port <= 65535 ? port : -1;
    } catch (NumberFormatException e) {
      return -1;
    }
  }

  private static String trimmed(String value) {
    return value == null ? "" : value.trim();
  }

  private static String trimmedOrDefault(String value, String fallback) {
    String trimmed = trimmed(value);
    return trimmed.isEmpty() ? fallback : trimmed;
  }

  private static @Nullable String emptyToNull(@Nullable String value) {
    return value == null || value.isEmpty() ? null : value;
  }
}
