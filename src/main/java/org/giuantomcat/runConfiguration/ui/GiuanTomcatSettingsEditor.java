package org.giuantomcat.runConfiguration.ui;

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.FormBuilder;
import org.giuantomcat.GiuanTomcatConstants;
import org.giuantomcat.runConfiguration.runner.GiuanTomcatRunConfiguration;
import org.jetbrains.annotations.NotNull;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.LinkedHashSet;
import java.util.Set;

public class GiuanTomcatSettingsEditor extends SettingsEditor<GiuanTomcatRunConfiguration> {

  private static final String MODULES_LABEL = "Modules";
  private static final String SKIP_ANNOTATION_TEXT =
      "Skip Servlet annotation scan (adds metadata-complete + absolute-ordering to WEB-INF/web.xml)";
  private static final String SKIP_ANNOTATION_TOOLTIP =
      "<html>Speeds up startup on large apps by disabling Servlet 3.0 annotation scanning "
          + "(@WebServlet/@WebFilter/...) and web-fragment/SCI discovery.<br>"
          + "On run, if enabled, it writes <b>metadata-complete=\"true\"</b> into "
          + "<b>&lt;web-app&gt;</b> and inserts an empty <b>&lt;absolute-ordering/&gt;</b> in the "
          + "docBase WEB-INF/web.xml. If later disabled, the plugin <b>removes</b> the pieces it "
          + "added (tracked by a marker comment); pre-existing elements are left untouched.</html>";

  private final JPanel myPanel;
  private final Project myProject;

  private final TextFieldWithBrowseButton catalinaHomeField;
  private final TextFieldWithBrowseButton webContentField;
  private final JTextField contextPathField;
  private final JTextField httpPortField;
  private final JTextField shutdownPortField;

  private final JBCheckBox hotSwapEnabledCheckBox;
  private final JButton hotSwapConfigureButton;
  private final JBCheckBox skipAnnotationScanCheckBox;

  private final JBLabel modulesSummary;
  private final JButton modulesConfigureButton;

  private Set<String> moduleNames = new LinkedHashSet<>();
  private Set<String> jarSkipTokens = new LinkedHashSet<>();
  private String dcevmJdkPath = "";
  private String hotswapAgentPath = "";

  public GiuanTomcatSettingsEditor(Project project) {
    myProject = project;

    catalinaHomeField = new TextFieldWithBrowseButton();
    catalinaHomeField.addBrowseFolderListener(null,
        FileChooserDescriptorFactory.createSingleFolderDescriptor()
            .withTitle("Select CATALINA_HOME (Tomcat installation)"));

    webContentField = new TextFieldWithBrowseButton();
    webContentField.addBrowseFolderListener(null,
        FileChooserDescriptorFactory.createSingleFolderDescriptor()
            .withTitle("Select Web Content (docBase)"));

    contextPathField = new JTextField(GiuanTomcatConstants.DEFAULT_CONTEXT_PATH);
    httpPortField = new JTextField(GiuanTomcatConstants.DEFAULT_HTTP_PORT);
    shutdownPortField = new JTextField(GiuanTomcatConstants.DEFAULT_SHUTDOWN_PORT);

    hotSwapEnabledCheckBox = new JBCheckBox("Enable HotSwap (DCEVM + hotswap-agent)");
    hotSwapEnabledCheckBox.addActionListener(e -> {
      if (hotSwapEnabledCheckBox.isSelected() && !openHotSwapConfigDialog()) {
        hotSwapEnabledCheckBox.setSelected(false);
      }
    });

    hotSwapConfigureButton = new JButton("Configure...");
    hotSwapConfigureButton.addActionListener(e -> openHotSwapConfigDialog());

    skipAnnotationScanCheckBox = new JBCheckBox(SKIP_ANNOTATION_TEXT);
    skipAnnotationScanCheckBox.setToolTipText(SKIP_ANNOTATION_TOOLTIP);

    modulesSummary = new JBLabel();
    modulesConfigureButton = new JButton("Configure...");
    modulesConfigureButton.addActionListener(e -> openModuleSelectorDialog());

    myPanel = FormBuilder.createFormBuilder()
        .addLabeledComponent("CATALINA_HOME", catalinaHomeField)
        .addLabeledComponent("Web content (docBase)", webContentField)
        .addLabeledComponent("Context path", contextPathField)
        .addLabeledComponent("HTTP port", httpPortField)
        .addLabeledComponent("Shutdown port", shutdownPortField)
        .addComponent(skipAnnotationScanCheckBox)
        .addLabeledComponent(MODULES_LABEL, createModulesSummaryPanel())
        .addComponent(createHotSwapPanel())
        .getPanel();
  }

  private JPanel createModulesSummaryPanel() {
    JPanel panel = new JPanel(new BorderLayout(6, 0));
    panel.add(modulesSummary, BorderLayout.CENTER);
    panel.add(modulesConfigureButton, BorderLayout.EAST);
    return panel;
  }

  private JPanel createHotSwapPanel() {
    JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
    panel.add(hotSwapEnabledCheckBox);
    panel.add(hotSwapConfigureButton);
    return panel;
  }

  private void openModuleSelectorDialog() {
    ModuleSelectorDialog dialog = new ModuleSelectorDialog(myProject, moduleNames, jarSkipTokens);
    if (!dialog.showAndGet()) {
      return;
    }
    moduleNames = new LinkedHashSet<>(dialog.getModuleNames());
    jarSkipTokens = new LinkedHashSet<>(dialog.getSkipTokens());
    updateModulesSummary();
  }

  private void updateModulesSummary() {
    modulesSummary.setText(modulesSummaryText());
    modulesSummary.setToolTipText(modulesSummaryToolTip());
  }

  private String modulesSummaryText() {
    int count = moduleNames.size();
    return count == 0 ? "No module selected" : count + " module" + (count == 1 ? "" : "s") + " selected";
  }

  private String modulesSummaryToolTip() {
    return moduleNames.isEmpty() ? null : String.join(", ", moduleNames);
  }

  private boolean openHotSwapConfigDialog() {
    HotSwapConfigDialog dialog = new HotSwapConfigDialog(
        dcevmJdkPath.isEmpty() ? null : dcevmJdkPath,
        hotswapAgentPath.isEmpty() ? null : hotswapAgentPath);
    if (!dialog.showAndGet()) {
      return false;
    }
    dcevmJdkPath = dialog.getDcevmJdkPath() == null ? "" : dialog.getDcevmJdkPath();
    hotswapAgentPath = dialog.getHotswapAgentPath() == null ? "" : dialog.getHotswapAgentPath();
    return true;
  }

  @Override
  protected void resetEditorFrom(@NotNull GiuanTomcatRunConfiguration configuration) {
    catalinaHomeField.setText(configuration.getCatalinaHome());
    webContentField.setText(configuration.getWebContent());
    contextPathField.setText(configuration.getContextPath());
    httpPortField.setText(configuration.getHttpPort());
    shutdownPortField.setText(configuration.getShutdownPort());

    hotSwapEnabledCheckBox.setSelected(configuration.isHotSwapEnabled());
    skipAnnotationScanCheckBox.setSelected(configuration.isSkipAnnotationScan());
    dcevmJdkPath = configuration.getDcevmJdkPath() == null ? "" : configuration.getDcevmJdkPath();
    hotswapAgentPath =
        configuration.getHotswapAgentPath() == null ? "" : configuration.getHotswapAgentPath();

    moduleNames = new LinkedHashSet<>(configuration.getModuleNames());
    jarSkipTokens = new LinkedHashSet<>(configuration.getJarSkipTokens());
    updateModulesSummary();
  }

  @Override
  protected void applyEditorTo(@NotNull GiuanTomcatRunConfiguration configuration) {
    configuration.setCatalinaHome(catalinaHomeField.getText());
    configuration.setWebContent(webContentField.getText());
    configuration.setContextPath(contextPathField.getText());
    configuration.setHttpPort(httpPortField.getText());
    configuration.setShutdownPort(shutdownPortField.getText());

    configuration.setHotSwapEnabled(hotSwapEnabledCheckBox.isSelected());
    configuration.setSkipAnnotationScan(skipAnnotationScanCheckBox.isSelected());
    configuration.setDcevmJdkPath(dcevmJdkPath);
    configuration.setHotswapAgentPath(hotswapAgentPath);

    configuration.setModuleNames(moduleNames);
    configuration.setJarSkipTokens(jarSkipTokens);
  }

  @NotNull
  @Override
  protected JComponent createEditor() {
    return new JBScrollPane(myPanel);
  }
}
