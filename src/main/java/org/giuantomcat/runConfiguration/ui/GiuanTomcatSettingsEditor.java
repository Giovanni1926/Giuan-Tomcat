package org.giuantomcat.runConfiguration.ui;

import com.intellij.application.options.ModuleListCellRenderer;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.ui.CollectionListModel;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.FormBuilder;
import org.giuantomcat.runConfiguration.runner.GiuanTomcatRunConfiguration;
import org.jetbrains.annotations.NotNull;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.DropMode;
import javax.swing.TransferHandler;
import javax.swing.JList;
import javax.swing.JButton;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class GiuanTomcatSettingsEditor extends SettingsEditor<GiuanTomcatRunConfiguration> {

  private final JPanel myPanel;
  private final Project myProject;

  private final TextFieldWithBrowseButton catalinaHomeField;
  private final TextFieldWithBrowseButton catalinaBaseField;
  private final TextFieldWithBrowseButton webContentField;
  private final JTextField contextPathField;
  private final JTextField httpPortField;
  private final JTextField shutdownPortField;

  private final JBCheckBox hotSwapEnabledCheckBox;
  private final JButton hotSwapConfigureButton;
  private String dcevmJdkPath = "";
  private String hotswapAgentPath = "";

  private final CollectionListModel<Module> chosenModel;
  private final CollectionListModel<Module> availableModel;
  private final JBList<Module> chosenList;
  private final JBList<Module> availableList;
  private JBList<Module> myDragSource;

  public GiuanTomcatSettingsEditor(Project project) {
    myProject = project;

    catalinaHomeField = new TextFieldWithBrowseButton();
    catalinaHomeField.addBrowseFolderListener(null,
        FileChooserDescriptorFactory.createSingleFolderDescriptor()
            .withTitle("Select CATALINA_HOME (Tomcat installation)"));

    catalinaBaseField = new TextFieldWithBrowseButton();
    catalinaBaseField.addBrowseFolderListener(null,
        FileChooserDescriptorFactory.createSingleFolderDescriptor()
            .withTitle("Select CATALINA_BASE (Tomcat instance)"));

    webContentField = new TextFieldWithBrowseButton();
    webContentField.addBrowseFolderListener(null,
        FileChooserDescriptorFactory.createSingleFolderDescriptor()
            .withTitle("Select Web Content (docBase)"));

    contextPathField = new JTextField("/myapp");
    httpPortField = new JTextField("8080");
    shutdownPortField = new JTextField("8005");

    hotSwapEnabledCheckBox = new JBCheckBox("Enable HotSwap (DCEVM + hotswap-agent)");
    hotSwapEnabledCheckBox.addActionListener(e -> {
      if (hotSwapEnabledCheckBox.isSelected() && !openHotSwapConfigDialog()) {
        hotSwapEnabledCheckBox.setSelected(false);
      }
    });

    hotSwapConfigureButton = new JButton("Configure...");
    hotSwapConfigureButton.addActionListener(e -> openHotSwapConfigDialog());

    chosenModel = new CollectionListModel<>();
    chosenList = new JBList<>(chosenModel);
    configureList(chosenList);

    availableModel = new CollectionListModel<>();
    availableList = new JBList<>(availableModel);
    configureList(availableList);

    myPanel = FormBuilder.createFormBuilder()
        .addLabeledComponent("CATALINA_HOME", catalinaHomeField)
        .addLabeledComponent("CATALINA_BASE", catalinaBaseField)
        .addLabeledComponent("Web content (docBase)", webContentField)
        .addLabeledComponent("Context path", contextPathField)
        .addLabeledComponent("HTTP port", httpPortField)
        .addLabeledComponent("Shutdown port", shutdownPortField)
        .addComponent(createHotSwapPanel())
        .addComponent(createModulesPanel())
        .getPanel();
  }

  private JPanel createHotSwapPanel() {
    JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
    panel.add(hotSwapEnabledCheckBox);
    panel.add(hotSwapConfigureButton);
    return panel;
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

  private void configureList(JBList<Module> list) {
    list.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
    list.setCellRenderer(new ModuleListCellRenderer());
    list.setDragEnabled(true);
    list.setDropMode(DropMode.INSERT);
    list.setTransferHandler(new ModuleListTransferHandler(list));
  }

  private JPanel createModulesPanel() {
    JPanel panel = new JPanel(new GridLayout(1, 2, 8, 0));
    panel.add(createListPanel("Selected modules", chosenList));
    panel.add(createListPanel("Available modules", availableList));
    return panel;
  }

  private JPanel createListPanel(String title, JBList<Module> list) {
    JPanel panel = new JPanel(new BorderLayout());
    panel.add(new JLabel(title), BorderLayout.NORTH);
    panel.add(new JBScrollPane(list), BorderLayout.CENTER);
    return panel;
  }

  private Module findModule(String name) {
    for (Module module : ModuleManager.getInstance(myProject).getModules()) {
      if (module.getName().equals(name)) {
        return module;
      }
    }
    return null;
  }

  @Override
  protected void resetEditorFrom(@NotNull GiuanTomcatRunConfiguration configuration) {
    catalinaHomeField.setText(configuration.getCatalinaHome());
    catalinaBaseField.setText(configuration.getCatalinaBase());
    webContentField.setText(configuration.getWebContent());
    contextPathField.setText(configuration.getContextPath());
    httpPortField.setText(configuration.getHttpPort());
    shutdownPortField.setText(configuration.getShutdownPort());

    hotSwapEnabledCheckBox.setSelected(configuration.isHotSwapEnabled());
    dcevmJdkPath = configuration.getDcevmJdkPath() == null ? "" : configuration.getDcevmJdkPath();
    hotswapAgentPath =
        configuration.getHotswapAgentPath() == null ? "" : configuration.getHotswapAgentPath();

    Set<String> selectedNames = configuration.getModuleNames();
    List<Module> chosen = new ArrayList<>();
    List<Module> available = new ArrayList<>();
    for (Module module : ModuleManager.getInstance(myProject).getModules()) {
      if (selectedNames.contains(module.getName())) {
        chosen.add(module);
      } else {
        available.add(module);
      }
    }
    chosenModel.replaceAll(chosen);
    availableModel.replaceAll(available);
  }

  @Override
  protected void applyEditorTo(@NotNull GiuanTomcatRunConfiguration configuration) {
    configuration.setCatalinaHome(catalinaHomeField.getText());
    configuration.setCatalinaBase(catalinaBaseField.getText());
    configuration.setWebContent(webContentField.getText());
    configuration.setContextPath(contextPathField.getText());
    configuration.setHttpPort(httpPortField.getText());
    configuration.setShutdownPort(shutdownPortField.getText());

    configuration.setHotSwapEnabled(hotSwapEnabledCheckBox.isSelected());
    configuration.setDcevmJdkPath(dcevmJdkPath);
    configuration.setHotswapAgentPath(hotswapAgentPath);

    Set<String> selectedNames = new LinkedHashSet<>();
    for (Module module : chosenModel.getItems()) {
      selectedNames.add(module.getName());
    }
    configuration.setModuleNames(selectedNames);
  }

  @NotNull
  @Override
  protected JComponent createEditor() {
    return myPanel;
  }

  private final class ModuleListTransferHandler extends TransferHandler {
    private final JBList<Module> mySourceList;
    private final List<String> myDraggedNames = new ArrayList<>();

    private ModuleListTransferHandler(JBList<Module> sourceList) {
      mySourceList = sourceList;
    }

    @Override
    public int getSourceActions(JComponent c) {
      return MOVE;
    }

    @Override
    protected Transferable createTransferable(JComponent c) {
      myDraggedNames.clear();
      for (Module module : mySourceList.getSelectedValuesList()) {
        myDraggedNames.add(module.getName());
      }
      myDragSource = mySourceList;
      return new StringSelection(String.join("\n", myDraggedNames));
    }

    @Override
    public boolean canImport(TransferSupport support) {
      if (!support.isDataFlavorSupported(DataFlavor.stringFlavor)) {
        return false;
      }
      return support.getComponent() != myDragSource;
    }

    @Override
    public boolean importData(TransferSupport support) {
      if (!canImport(support)) {
        return false;
      }
      try {
        String data = (String) support.getTransferable().getTransferData(DataFlavor.stringFlavor);
        @SuppressWarnings("unchecked")
        CollectionListModel<Module> targetModel =
            (CollectionListModel<Module>) ((JList<?>) support.getComponent()).getModel();
        for (String name : data.split("\n")) {
          Module module = findModule(name);
          if (module != null && !targetModel.contains(module)) {
            targetModel.add(module);
          }
        }
        return true;
      } catch (Exception e) {
        return false;
      }
    }

    @Override
    protected void exportDone(JComponent source, Transferable data, int action) {
      if (action == MOVE && myDragSource != null) {
        @SuppressWarnings("unchecked")
        CollectionListModel<Module> model =
            (CollectionListModel<Module>) myDragSource.getModel();
        for (String name : myDraggedNames) {
          Module module = findModule(name);
          if (module != null) {
            model.remove(module);
          }
        }
      }
      myDraggedNames.clear();
      myDragSource = null;
    }
  }
}
