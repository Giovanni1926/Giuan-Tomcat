package org.giuantomcat.runConfiguration.ui;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.ui.CollectionListModel;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.JBUI;
import org.giuantomcat.runConfiguration.runner.GiuanTomcatRunConfiguration;
import org.jetbrains.annotations.NotNull;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.TransferHandler;
import javax.swing.UIManager;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

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
  private final JBCheckBox skipAnnotationScanCheckBox;
  private String dcevmJdkPath = "";
  private String hotswapAgentPath = "";

  private final CollectionListModel<Module> chosenModel;
  private final JBList<Module> chosenList;

  private final JBCheckBox skipAllCheckBox;
  private final Tree availableTree;
  private final DefaultTreeModel availableTreeModel;

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

    skipAnnotationScanCheckBox = new JBCheckBox(
        "Skip Servlet annotation scan (adds metadata-complete + absolute-ordering to WEB-INF/web.xml)");
    skipAnnotationScanCheckBox.setToolTipText(
        "<html>Speeds up startup on large apps by disabling Servlet 3.0 annotation scanning "
            + "(@WebServlet/@WebFilter/...) and web-fragment/SCI discovery.<br>"
            + "On run, if enabled, it writes <b>metadata-complete=\"true\"</b> into "
            + "<b>&lt;web-app&gt;</b> and inserts an empty <b>&lt;absolute-ordering/&gt;</b> in the "
            + "docBase WEB-INF/web.xml. If later disabled, the plugin <b>removes</b> the pieces it "
            + "added (tracked by a marker comment); pre-existing elements are left untouched.</html>");

    hotSwapConfigureButton = new JButton("Configure...");
    hotSwapConfigureButton.addActionListener(e -> openHotSwapConfigDialog());

    chosenModel = new CollectionListModel<>();
    chosenList = new JBList<>(chosenModel);
    chosenList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
    chosenList.setCellRenderer(new ModuleSkipCellRenderer());
    chosenList.setDragEnabled(true);
    chosenList.setDropMode(javax.swing.DropMode.INSERT);
    chosenList.setTransferHandler(new ChosenTransferHandler());
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
    chosenList.setToolTipText(
        "Tick the leftmost box of a module to skip Tomcat's startup scan "
            + "(dependency jars + tag libraries) of that module's dependencies for faster startup. "
            + "For the context it sets reloadable=false and containerSciFilter, disables "
            + "classpath/bootstrap/all-directory/all-file scanning and lists those jars in "
            + "pluggabilitySkip/tldSkip of the generated context.xml.");

    availableTreeModel = new DefaultTreeModel(new DefaultMutableTreeNode());
    availableTree = new Tree(availableTreeModel);
    availableTree.setRootVisible(false);
    availableTree.setShowsRootHandles(true);

    availableTree.getSelectionModel()
        .setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
    availableTree.setCellRenderer(new FolderModuleRenderer());
    availableTree.setDragEnabled(true);
    availableTree.setDropMode(javax.swing.DropMode.ON);
    availableTree.setTransferHandler(new AvailableTreeTransferHandler());
    availableTree.setToolTipText(
        "Available modules grouped by folder. Double-click (or drag) a module to move it to the "
            + "Selected list.");
    availableTree.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        if (e.getClickCount() == 2) {
          TreePath path = availableTree.getPathForLocation(e.getX(), e.getY());
          if (path != null && path.getLastPathComponent() instanceof DefaultMutableTreeNode node) {
            Object userObject = node.getUserObject();
            if (userObject instanceof Module module) {
              addToChosen(module);
            }
          }
        }
      }
    });

    skipAllCheckBox = new JBCheckBox("Skip scan all");
    skipAllCheckBox.setToolTipText(
        "Check/uncheck the skip-scan flag for every module currently in the Selected list.");
    skipAllCheckBox.addActionListener(e -> {
      for (Module module : chosenModel.getItems()) {
        if (skipAllCheckBox.isSelected()) {
          modulesSkipJarScan.add(module.getName());
        } else {
          modulesSkipJarScan.remove(module.getName());
        }
      }
      chosenList.repaint();
    });

    myPanel = FormBuilder.createFormBuilder()
        .addLabeledComponent("CATALINA_HOME", catalinaHomeField)
        .addLabeledComponent("CATALINA_BASE", catalinaBaseField)
        .addLabeledComponent("Web content (docBase)", webContentField)
        .addLabeledComponent("Context path", contextPathField)
        .addLabeledComponent("HTTP port", httpPortField)
        .addLabeledComponent("Shutdown port", shutdownPortField)
        .addComponent(createHotSwapPanel())
        .addComponent(skipAnnotationScanCheckBox)
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

  private JPanel createModulesPanel() {
    JPanel description = new JPanel(new BorderLayout());
    JLabel desc = new JLabel(
        "<html>Modules selected as the application classpath: compiled classes are mounted on "
            + "<b>/WEB-INF/classes</b> and dependency jars on <b>/WEB-INF/lib</b>.</html>");
    description.add(desc, BorderLayout.WEST);

    Splitter splitter = new Splitter(true, 0.5f);
    splitter.setFirstComponent(createSelectedPanel());
    splitter.setSecondComponent(createAvailablePanel());

    JPanel wrapper = new JPanel(new BorderLayout(0, 6));
    wrapper.add(description, BorderLayout.NORTH);
    wrapper.add(splitter, BorderLayout.CENTER);
    return wrapper;
  }

  private JPanel createSelectedPanel() {
    JPanel panel = new JPanel(new BorderLayout(0, 4));
    JPanel north = new JPanel(new BorderLayout(0, 2));
    JLabel title = new JLabel("Selected modules (source of app classpath)");
    north.add(title, BorderLayout.NORTH);
    JLabel hint = new JLabel(
        "<html>Tick the <b>Skip scan</b> box to skip Tomcat's startup scan (pluggability/SCI "
            + "&amp; TLD) of this module's dependency jars (faster start).</html>");
    hint.setFont(hint.getFont().deriveFont(Font.PLAIN, 10f));
    hint.setForeground(UIManager.getColor("Label.disabledForeground"));
    north.add(hint, BorderLayout.CENTER);
    JPanel skipHeader = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
    skipHeader.add(skipAllCheckBox);
    north.add(skipHeader, BorderLayout.SOUTH);
    panel.add(north, BorderLayout.NORTH);
    JBScrollPane scroll = new JBScrollPane(chosenList);
    scroll.setPreferredSize(JBUI.size(280, 160));
    panel.add(scroll, BorderLayout.CENTER);
    return panel;
  }

  private JPanel createAvailablePanel() {
    JPanel panel = new JPanel(new BorderLayout(0, 4));
    JPanel north = new JPanel(new BorderLayout(0, 2));
    JLabel title = new JLabel("Available modules");
    north.add(title, BorderLayout.NORTH);
    JLabel hint = new JLabel(
        "<html>Grouped by folder. <b>Double-click</b> or <b>drag</b> a module to add it to the "
            + "Selected list; drag a Selected module here to remove it.</html>");
    hint.setFont(hint.getFont().deriveFont(Font.PLAIN, 10f));
    hint.setForeground(UIManager.getColor("Label.disabledForeground"));
    north.add(hint, BorderLayout.CENTER);
    panel.add(north, BorderLayout.NORTH);
    JBScrollPane scroll = new JBScrollPane(availableTree);
    scroll.setPreferredSize(JBUI.size(280, 160));
    panel.add(scroll, BorderLayout.CENTER);
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
    skipAnnotationScanCheckBox.setSelected(configuration.isSkipAnnotationScan());
    dcevmJdkPath = configuration.getDcevmJdkPath() == null ? "" : configuration.getDcevmJdkPath();
    hotswapAgentPath =
        configuration.getHotswapAgentPath() == null ? "" : configuration.getHotswapAgentPath();

    modulesSkipJarScan.clear();
    modulesSkipJarScan.addAll(configuration.getModulesSkipJarScan());

    Set<String> selectedNames = configuration.getModuleNames();
    List<Module> chosen = new ArrayList<>();
    for (Module module : ModuleManager.getInstance(myProject).getModules()) {
      if (selectedNames.contains(module.getName())) {
        chosen.add(module);
      }
    }
    chosenModel.replaceAll(chosen);
    rebuildAvailableTree();
    syncSkipAllCheckBox();
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
    configuration.setSkipAnnotationScan(skipAnnotationScanCheckBox.isSelected());
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
    return new JBScrollPane(myPanel);
  }

  private void addToChosen(Module module) {
    if (module == null || chosenModel.getItems().contains(module)) {
      return;
    }
    chosenModel.add(module);
    rebuildAvailableTree();
    syncSkipAllCheckBox();
    chosenList.repaint();
  }

  private void removeFromChosen(String moduleName) {
    Module module = findModule(moduleName);
    if (module != null && chosenModel.getItems().contains(module)) {
      chosenModel.remove(module);
    }
    modulesSkipJarScan.remove(moduleName);
    rebuildAvailableTree();
    syncSkipAllCheckBox();
    chosenList.repaint();
  }

  private void toggleSkipJarScan(Module module) {
    if (!modulesSkipJarScan.add(module.getName())) {
      modulesSkipJarScan.remove(module.getName());
    }
    syncSkipAllCheckBox();
    chosenList.repaint();
  }

  private void syncSkipAllCheckBox() {
    List<Module> items = chosenModel.getItems();
    if (items.isEmpty()) {
      skipAllCheckBox.setSelected(false);
      return;
    }
    boolean all = true;
    for (Module module : items) {
      if (!modulesSkipJarScan.contains(module.getName())) {
        all = false;
        break;
      }
    }
    skipAllCheckBox.setSelected(all);
  }

  private List<Module> availableModules() {
    Set<String> chosenNames = new LinkedHashSet<>();
    for (Module module : chosenModel.getItems()) {
      chosenNames.add(module.getName());
    }
    List<Module> available = new ArrayList<>();
    for (Module module : ModuleManager.getInstance(myProject).getModules()) {
      if (!chosenNames.contains(module.getName())) {
        available.add(module);
      }
    }
    return available;
  }

  private void rebuildAvailableTree() {
    List<Module> available = availableModules();
    availableTreeModel.setRoot(buildTree(available));
    expandAllRows();
  }

  private void expandAllRows() {
    Object root = availableTreeModel.getRoot();
    if (root instanceof DefaultMutableTreeNode node) {
      expandNode(node);
    }
  }

  private void expandNode(DefaultMutableTreeNode node) {
    availableTree.expandPath(new TreePath(node.getPath()));
    Enumeration<TreeNode> children = node.children();
    while (children.hasMoreElements()) {
      DefaultMutableTreeNode child = (DefaultMutableTreeNode) children.nextElement();
      if (!child.isLeaf()) {
        expandNode(child);
      }
    }
  }

  private DefaultMutableTreeNode buildTree(List<Module> modules) {
    DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode();
    if (modules.isEmpty()) {
      return rootNode;
    }
    List<List<String>> absolute = new ArrayList<>();
    for (Module module : modules) {
      absolute.add(absoluteSegments(module));
    }
    List<String> common = commonPrefix(absolute);
    FolderNode root = new FolderNode(null);
    for (int i = 0; i < modules.size(); i++) {
      List<String> rel = relative(absolute.get(i), common);
      FolderNode current = root;
      for (String segment : rel) {
        current = current.children.computeIfAbsent(segment, FolderNode::new);
      }
      current.module = modules.get(i);
    }
    if (root.module != null && root.children.isEmpty()) {
      rootNode.add(new DefaultMutableTreeNode(root.module));
    }
    fillFolder(rootNode, root);
    return rootNode;
  }

  private void fillFolder(DefaultMutableTreeNode parent, FolderNode folder) {
    for (FolderNode child : folder.children.values()) {
      if (child.module != null && child.children.isEmpty()) {
        parent.add(new DefaultMutableTreeNode(child.module));
      } else {
        DefaultMutableTreeNode childNode = new DefaultMutableTreeNode(child.name);
        parent.add(childNode);
        fillFolder(childNode, child);
      }
    }
  }

  private List<String> absoluteSegments(Module module) {
    ModuleRootManager manager = ModuleRootManager.getInstance(module);
    for (com.intellij.openapi.vfs.VirtualFile root : manager.getContentRoots()) {
      String path = root.getPath();
      if (path != null && !path.isEmpty()) {
        List<String> segments = splitPath(path);
        if (!segments.isEmpty()) {
          return segments;
        }
      }
    }
    return new ArrayList<>(List.of(module.getName()));
  }

  private static List<String> splitPath(String path) {
    List<String> result = new ArrayList<>();
    for (String part : path.replace('\\', '/').split("/")) {
      if (!part.isEmpty()) {
        result.add(part);
      }
    }
    return result;
  }

  private static List<String> commonPrefix(List<List<String>> paths) {
    List<String> first = paths.get(0);
    int common = first.size();
    for (List<String> path : paths) {
      common = Math.min(common, path.size());
      for (int i = 0; i < common; i++) {
        if (!first.get(i).equals(path.get(i))) {
          common = i;
          break;
        }
      }
    }
    return new ArrayList<>(first.subList(0, common));
  }

  private static List<String> relative(List<String> absolute, List<String> common) {
    return new ArrayList<>(absolute.subList(common.size(), absolute.size()));
  }

  private static final class FolderNode {
    private final String name;
    private final Map<String, FolderNode> children = new TreeMap<>();
    private Module module;

    private FolderNode(String name) {
      this.name = name;
    }
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

  private final class FolderModuleRenderer extends ColoredTreeCellRenderer {
    @Override
    public void customizeCellRenderer(JTree tree, Object value, boolean selected,
                                      boolean expanded, boolean leaf, int row,
                                      boolean hasFocus) {
      if (!(value instanceof DefaultMutableTreeNode node)) {
        return;
      }
      Object userObject = node.getUserObject();
      if (userObject instanceof Module module) {
        setIcon(AllIcons.Nodes.Module);
        append(module.getName(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
      } else if (userObject instanceof String folderName) {
        setIcon(AllIcons.Nodes.Folder);
        append(folderName, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
      }
    }
  }

  private final class ChosenTransferHandler extends TransferHandler {
    private final List<String> myDraggedNames = new ArrayList<>();
    private boolean myDraggingFromChosen;

    @Override
    public int getSourceActions(JComponent c) {
      return MOVE;
    }

    @Override
    protected Transferable createTransferable(JComponent c) {
      myDraggedNames.clear();
      for (Module module : chosenList.getSelectedValuesList()) {
        myDraggedNames.add(module.getName());
      }
      if (myDraggedNames.isEmpty()) {
        return null;
      }
      myDraggingFromChosen = true;
      return new StringSelection(String.join("\n", myDraggedNames));
    }

    @Override
    public boolean canImport(TransferSupport support) {
      if (!support.isDataFlavorSupported(DataFlavor.stringFlavor)) {
        return false;
      }
      return !myDraggingFromChosen;
    }

    @Override
    public boolean importData(TransferSupport support) {
      if (!canImport(support)) {
        return false;
      }
      try {
        String data = (String) support.getTransferable().getTransferData(DataFlavor.stringFlavor);
        for (String name : data.split("\n")) {
          addToChosen(findModule(name));
        }
        return true;
      } catch (Exception e) {
        return false;
      }
    }

    @Override
    protected void exportDone(JComponent source, Transferable data, int action) {
      if (action == MOVE && myDraggingFromChosen) {
        for (String name : myDraggedNames) {
          removeFromChosen(name);
        }
      }
      myDraggedNames.clear();
      myDraggingFromChosen = false;
    }
  }

  private final class AvailableTreeTransferHandler extends TransferHandler {
    private boolean myDraggingFromTree;

    @Override
    public int getSourceActions(JComponent c) {
      if (selectedTreeModule() == null) {
        return NONE;
      }
      return MOVE;
    }

    @Override
    protected Transferable createTransferable(JComponent c) {
      Module module = selectedTreeModule();
      if (module == null) {
        return null;
      }
      myDraggingFromTree = true;
      return new StringSelection(module.getName());
    }

    @Override
    public boolean canImport(TransferSupport support) {
      if (!support.isDataFlavorSupported(DataFlavor.stringFlavor)) {
        return false;
      }
      return !myDraggingFromTree;
    }

    @Override
    public boolean importData(TransferSupport support) {
      if (!canImport(support)) {
        return false;
      }
      try {
        String data = (String) support.getTransferable().getTransferData(DataFlavor.stringFlavor);
        for (String name : data.split("\n")) {
          removeFromChosen(name);
        }
        return true;
      } catch (Exception e) {
        return false;
      }
    }

    @Override
    protected void exportDone(JComponent source, Transferable data, int action) {
      myDraggingFromTree = false;
    }

    private Module selectedTreeModule() {
      TreePath path = availableTree.getSelectionPath();
      if (path != null && path.getLastPathComponent() instanceof DefaultMutableTreeNode node
          && node.getUserObject() instanceof Module module) {
        return module;
      }
      return null;
    }
  }
}
