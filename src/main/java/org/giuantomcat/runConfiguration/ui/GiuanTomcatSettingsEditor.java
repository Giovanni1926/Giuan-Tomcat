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
import javax.swing.JCheckBox;
import javax.swing.ListCellRenderer;
import javax.swing.UIManager;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
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

  private final Set<String> modulesSkipJarScan = new LinkedHashSet<>();
  private static final int CHECKBOX_COLUMN_WIDTH = 24;

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
    chosenList.setCellRenderer(new ModuleSkipCellRenderer());
    chosenList.addMouseListener(new MouseAdapter() {
      @Override
      public void mousePressed(MouseEvent e) {
        if (e.getX() <= CHECKBOX_COLUMN_WIDTH) {
          int index = chosenList.locationToIndex(e.getPoint());
          if (index >= 0 && index < chosenList.getModel().getSize()) {
            Module module = chosenList.getModel().getElementAt(index);
            toggleSkipJarScan(module);
            e.consume();
          }
        }
      }
    });

    availableModel = new CollectionListModel<>();
    availableList = new JBList<>(availableModel);
    configureList(availableList);

    chosenList.setToolTipText(
        "Tick the leftmost box of a module to skip Tomcat's startup scan "
            + "(dependency jars + tag libraries) of that module's dependencies for faster startup.");

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
    JPanel description = new JPanel(new BorderLayout());
    JLabel desc = new JLabel(
        "<html>Modules selected as the application classpath: compiled classes are mounted on "
            + "<b>/WEB-INF/classes</b> and dependency jars on <b>/WEB-INF/lib</b>.</html>");
    description.add(desc, BorderLayout.WEST);

    JPanel lists = new JPanel(new GridLayout(1, 2, 8, 0));
    lists.add(createListPanel(
        "Selected modules (source of app classpath)",
        "<html>Tick the <b>Skip scan</b> box to skip Tomcat's startup scan of this module's "
            + "dependency jars &amp; taglibs (faster start).</html>",
        true, chosenList));
    lists.add(createListPanel(
        "Available modules (drag left to add to classpath)",
        "", false, availableList));

    JPanel wrapper = new JPanel(new BorderLayout(0, 6));
    wrapper.add(description, BorderLayout.NORTH);
    wrapper.add(lists, BorderLayout.CENTER);
    return wrapper;
  }

  private JPanel createListPanel(String title, String hint, boolean hasSkipColumn,
                                 JBList<Module> list) {
    JPanel panel = new JPanel(new BorderLayout(0, 4));

    JPanel north = new JPanel(new BorderLayout(0, 2));
    JLabel titleLabel = new JLabel(title);
    north.add(titleLabel, BorderLayout.NORTH);
    if (!hint.isEmpty()) {
      JLabel hintLabel = new JLabel(hint);
      hintLabel.setFont(hintLabel.getFont().deriveFont(Font.PLAIN, 10f));
      hintLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
      north.add(hintLabel, BorderLayout.CENTER);
    }
    if (hasSkipColumn) {
      JLabel legend = new JLabel("      Skip scan");
      legend.setFont(legend.getFont().deriveFont(Font.BOLD, 10f));
      legend.setForeground(UIManager.getColor("Label.foreground"));
      north.add(legend, BorderLayout.SOUTH);
    }
    panel.add(north, BorderLayout.NORTH);
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

    modulesSkipJarScan.clear();
    modulesSkipJarScan.addAll(configuration.getModulesSkipJarScan());

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

    configuration.setModulesSkipJarScan(new LinkedHashSet<>(modulesSkipJarScan));
  }

  @NotNull
  @Override
  protected JComponent createEditor() {
    return myPanel;
  }

  private void toggleSkipJarScan(Module module) {
    if (!modulesSkipJarScan.add(module.getName())) {
      modulesSkipJarScan.remove(module.getName());
    }
    chosenList.repaint();
  }

  private final class ModuleSkipCellRenderer extends JPanel
      implements ListCellRenderer<Module> {
    private final JCheckBox myCheckBox = new JCheckBox();
    private final JLabel myLabel = new JLabel();

    private ModuleSkipCellRenderer() {
      super(new BorderLayout());
      myCheckBox.setOpaque(false);
      add(myCheckBox, BorderLayout.WEST);
      add(myLabel, BorderLayout.CENTER);
      setOpaque(true);
    }

    @Override
    public Component getListCellRendererComponent(JList<? extends Module> list,
                                                  Module value, int index,
                                                  boolean isSelected, boolean cellHasFocus) {
      myCheckBox.setSelected(modulesSkipJarScan.contains(value.getName()));
      myLabel.setText(value.getName());
      myLabel.setForeground(isSelected ? list.getSelectionForeground() : list.getForeground());
      setBackground(isSelected ? list.getSelectionBackground() : list.getBackground());
      return this;
    }
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
          if (myDragSource == chosenList) {
            modulesSkipJarScan.remove(name);
          }
        }
      }
      myDraggedNames.clear();
      myDragSource = null;
    }
  }
}
