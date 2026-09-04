package org.giuantomcat.runConfiguration.ui;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Splitter;
import com.intellij.ui.SearchTextField;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.table.JBTable;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ui.JBUI;
import org.giuantomcat.runConfiguration.settings.SkipTokens;
import org.giuantomcat.tomcat.ModuleDependencies;

import javax.swing.AbstractAction;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.KeyStroke;
import javax.swing.TransferHandler;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** 3-column classpath panel: Available / Selected / Manage skips. */
public final class ClasspathModulesPanel {

  public static final class State {
    public final Set<String> moduleNames;
    public final Set<String> skipTokens;

    public State(Set<String> moduleNames, Set<String> skipTokens) {
      this.moduleNames = moduleNames;
      this.skipTokens = skipTokens;
    }
  }

  private static final int COL_ENTRY = 0;
  private static final int COL_TLD = 1;
  private static final int COL_PLUGGABLE = 2;

  private final ClasspathModulesController myController;
  private final JPanel myPanel;

  private final Tree myAvailableTree = ModuleTreeBuilder.createTree();
  private final Tree mySelectedTree = ModuleTreeBuilder.createTree();

  private final DefaultTableModel mySkipModel = new DefaultTableModel(
      new Object[]{"Entry", "Skip TLD", "Skip pluggable"}, 0) {
    @Override
    public boolean isCellEditable(int row, int column) {
      return false;
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
      return columnIndex == COL_ENTRY ? String.class : Boolean.class;
    }
  };
  private final JBTable mySkipTable = new JBTable(mySkipModel);
  private final JLabel mySkipModuleLabel = new JLabel(" ");
  private final JLabel myNoModuleLabel = new JLabel("Select a module in the middle list.");
  private final JCheckBox myTldAllCheck = new JCheckBox("Select all: Skip TLD");
  private final JCheckBox myPluggableAllCheck = new JCheckBox("Select all: Skip pluggable");
  private final SearchTextField mySearchField = new SearchTextField();
  private List<String> myCurrentJarNames = new ArrayList<>();

  private static final String SEARCH_ACTION = "giuan.skipSearch";

  public ClasspathModulesPanel(Project project) {
    myController = new ClasspathModulesController(project);
    configureSkipTable();
    myPanel = buildPanel();
    registerSearchShortcut();
  }

  public JComponent getComponent() {
    return myPanel;
  }

  public Tree getAvailableTree() {
    return myAvailableTree;
  }

  public void setState(Set<String> moduleNames, Set<String> skipTokens) {
    myController.loadState(moduleNames, skipTokens);
    refreshTrees();
    refreshSkipArea();
  }

  public State getState() {
    return new State(myController.selectedNames(), myController.skipTokens());
  }

  // ---- layout ----

  private JPanel buildPanel() {
    configureAvailableTree();
    configureSelectedTree();

    JPanel rightRegion = new JPanel(new BorderLayout(2, 0));
    rightRegion.add(createRail(), BorderLayout.WEST);
    Splitter innerSplitter = new Splitter(false, 0.45f);
    innerSplitter.setFirstComponent(createModuleColumn("Selected modules (app classpath)",
        mySelectedTree,
        "<html><b>Double-click</b> or <b>&#10005;</b> to remove; select one to manage skips.</html>",
        220));
    innerSplitter.setSecondComponent(createSkipPanel());
    rightRegion.add(innerSplitter, BorderLayout.CENTER);

    Splitter outerSplitter = new Splitter(false, 0.30f);
    outerSplitter.setFirstComponent(createModuleColumn("Available modules", myAvailableTree,
        "<html><b>Double-click</b> or select + <b>&gt;</b> to move a module to the right.</html>",
        220));
    outerSplitter.setSecondComponent(rightRegion);

    JPanel wrapper = new JPanel(new BorderLayout());
    wrapper.add(outerSplitter, BorderLayout.CENTER);
    return wrapper;
  }

  private JPanel createModuleColumn(String title, Tree tree, String hint, int width) {
    JPanel panel = new JPanel(new BorderLayout(0, 4));
    JPanel north = new JPanel(new BorderLayout(0, 2));
    JLabel t = new JLabel(title);
    north.add(t, BorderLayout.NORTH);
    JLabel h = new JLabel(hint);
    h.setFont(h.getFont().deriveFont(Font.PLAIN, 10f));
    h.setForeground(UIManager.getColor("Label.disabledForeground"));
    north.add(h, BorderLayout.CENTER);
    panel.add(north, BorderLayout.NORTH);
    JBScrollPane scroll = new JBScrollPane(tree);
    scroll.setPreferredSize(JBUI.size(width, 240));
    panel.add(scroll, BorderLayout.CENTER);
    return panel;
  }

  private JPanel createRail() {
    JPanel rail = new JPanel();
    rail.setLayout(new BoxLayout(rail, BoxLayout.Y_AXIS));
    rail.setBorder(JBUI.Borders.empty(16, 2));

    JButton add = new JButton(">");
    add.setToolTipText("Add selected module (left) to the selected list");
    add.setMargin(JBUI.emptyInsets());
    add.addActionListener(e -> moveToSelected(ModuleTreeBuilder.selectedModules(myAvailableTree)));

    JButton remove = new JButton("\u2715");
    remove.setToolTipText("Remove selected module (right)");
    remove.setMargin(JBUI.emptyInsets());
    remove.addActionListener(e -> {
      List<String> names = new ArrayList<>();
      for (Module m : ModuleTreeBuilder.selectedModules(mySelectedTree)) {
        names.add(m.getName());
      }
      removeFromSelected(names);
    });

    JButton removeAll = new JButton("\u2715 all");
    removeAll.setToolTipText("Remove all modules");
    removeAll.setMargin(JBUI.emptyInsets());
    removeAll.addActionListener(e -> {
      myController.removeAllSelected();
      refreshTrees();
      refreshSkipArea();
    });

    rail.add(Box.createVerticalGlue());
    rail.add(add);
    rail.add(Box.createVerticalStrut(6));
    rail.add(remove);
    rail.add(Box.createVerticalStrut(6));
    rail.add(removeAll);
    rail.add(Box.createVerticalGlue());
    return rail;
  }

  private JPanel createSkipPanel() {
    myTldAllCheck.addActionListener(e -> {
      Module m = myController.getFocusModule();
      if (m != null) {
        myController.setAllJarsFlag(m, SkipTokens.FLAG_TLD, myTldAllCheck.isSelected());
        refreshSkipArea();
      }
    });
    myPluggableAllCheck.addActionListener(e -> {
      Module m = myController.getFocusModule();
      if (m != null) {
        myController.setAllJarsFlag(m, SkipTokens.FLAG_PLUGGABLE, myPluggableAllCheck.isSelected());
        refreshSkipArea();
      }
    });

    JPanel header = new JPanel(new BorderLayout(0, 2));
    header.add(mySkipModuleLabel, BorderLayout.NORTH);
    JLabel h = new JLabel(
        "<html>Per-jar startup scan skips (TLD/taglib and pluggability/SCI) for the module "
            + "selected in the middle list.</html>");
    h.setFont(h.getFont().deriveFont(Font.PLAIN, 10f));
    h.setForeground(UIManager.getColor("Label.disabledForeground"));
    header.add(h, BorderLayout.CENTER);

    JPanel body = new JPanel(new BorderLayout(0, 4));

    JPanel top = new JPanel();
    top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));

    JPanel searchRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
    mySearchField.getTextEditor().setColumns(18);
    mySearchField.getTextEditor().setToolTipText("Filter the jars of the selected module (Ctrl+F)");
    mySearchField.getTextEditor().getDocument().addDocumentListener(new DocumentListener() {
      @Override
      public void insertUpdate(DocumentEvent e) {
        repopulateSkipRows();
      }

      @Override
      public void removeUpdate(DocumentEvent e) {
        repopulateSkipRows();
      }

      @Override
      public void changedUpdate(DocumentEvent e) {
        repopulateSkipRows();
      }
    });
    searchRow.add(mySearchField);
    top.add(searchRow);

    JPanel selectAll = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
    selectAll.add(myTldAllCheck);
    selectAll.add(myPluggableAllCheck);
    top.add(selectAll);
    body.add(top, BorderLayout.NORTH);

    JBScrollPane scroll = new JBScrollPane(mySkipTable);
    scroll.setPreferredSize(JBUI.size(360, 185));
    body.add(scroll, BorderLayout.CENTER);

    myNoModuleLabel.setFont(myNoModuleLabel.getFont().deriveFont(Font.PLAIN, 10f));
    myNoModuleLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
    JPanel footer = new JPanel(new BorderLayout());
    footer.add(myNoModuleLabel, BorderLayout.WEST);
    body.add(footer, BorderLayout.SOUTH);

    JPanel panel = new JPanel(new BorderLayout(0, 6));
    panel.add(header, BorderLayout.NORTH);
    panel.add(body, BorderLayout.CENTER);
    return panel;
  }

  private void registerSearchShortcut() {
    myPanel.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
        .put(KeyStroke.getKeyStroke(KeyEvent.VK_F, InputEvent.CTRL_DOWN_MASK), SEARCH_ACTION);
    myPanel.getActionMap().put(SEARCH_ACTION, new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        mySearchField.getTextEditor().requestFocusInWindow();
        mySearchField.getTextEditor().selectAll();
      }
    });
  }

  // ---- table ----

  private void configureSkipTable() {
    mySkipTable.setRowSelectionAllowed(false);
    mySkipTable.getTableHeader().setReorderingAllowed(false);
    mySkipTable.setDefaultRenderer(Boolean.class, new SkipBooleanRenderer());
    mySkipTable.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        if (e.getClickCount() != 1) {
          return;
        }
        int col = mySkipTable.columnAtPoint(e.getPoint());
        if (col != COL_TLD && col != COL_PLUGGABLE) {
          return;
        }
        int row = mySkipTable.rowAtPoint(e.getPoint());
        if (row < 0 || row >= mySkipModel.getRowCount()) {
          return;
        }
        Module m = myController.getFocusModule();
        if (m == null) {
          return;
        }
        String jar = (String) mySkipModel.getValueAt(row, COL_ENTRY);
        boolean next = !Boolean.TRUE.equals(mySkipModel.getValueAt(row, col));
        mySkipModel.setValueAt(next, row, col);
        if (col == COL_TLD) {
          myController.setTldSkipped(m, jar, next);
        } else {
          myController.setPluggableSkipped(m, jar, next);
        }
      }
    });
  }

  private void configureAvailableTree() {
    myAvailableTree.setDragEnabled(true);
    myAvailableTree.setDropMode(javax.swing.DropMode.ON);
    myAvailableTree.setTransferHandler(new TreeTransferHandler());
    myAvailableTree.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        if (e.getClickCount() == 2) {
          Module module = moduleAt(myAvailableTree, e);
          if (module != null) {
            moveToSelected(List.of(module));
          }
        }
      }
    });
  }

  private void configureSelectedTree() {
    mySelectedTree.setDragEnabled(true);
    mySelectedTree.setDropMode(javax.swing.DropMode.ON);
    mySelectedTree.setTransferHandler(new TreeTransferHandler());
    mySelectedTree.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        if (e.getClickCount() == 2) {
          Module module = moduleAt(mySelectedTree, e);
          if (module != null) {
            removeFromSelected(List.of(module.getName()));
          }
        }
      }
    });
    mySelectedTree.addTreeSelectionListener(e -> {
      List<Module> selected = ModuleTreeBuilder.selectedModules(mySelectedTree);
      if (selected.size() == 1) {
        myController.setFocusModuleName(selected.get(0).getName());
      }
      refreshSkipArea();
    });
  }

  private static Module moduleAt(Tree tree, MouseEvent e) {
    TreePath path = tree.getPathForLocation(e.getX(), e.getY());
    if (path != null && path.getLastPathComponent() instanceof DefaultMutableTreeNode node
        && node.getUserObject() instanceof Module module) {
      return module;
    }
    return null;
  }

  // ---- state actions ----

  private void moveToSelected(List<Module> modules) {
    myController.addToSelected(modules);
    refreshTrees();
    refreshSkipArea();
  }

  private void removeFromSelected(List<String> moduleNames) {
    myController.removeFromSelected(moduleNames);
    refreshTrees();
    refreshSkipArea();
  }

  // ---- refresh ----

  private void refreshTrees() {
    ModuleTreeBuilder.rebuild(myAvailableTree, myController.availableModules());
    List<Module> selected = myController.getSelectedModules();
    ModuleTreeBuilder.rebuild(mySelectedTree, selected);
    if (selected.size() == 1) {
      myController.setFocusModuleName(selected.get(0).getName());
    }
    Module focus = myController.getFocusModule();
    if (focus != null) {
      selectModule(mySelectedTree, focus.getName());
    }
  }

  private static void selectModule(Tree tree, String moduleName) {
    Object root = tree.getModel().getRoot();
    if (root instanceof DefaultMutableTreeNode node) {
      DefaultMutableTreeNode found = findModuleNode(node, moduleName);
      if (found != null) {
        tree.setSelectionPath(new TreePath(found.getPath()));
      }
    }
  }

  private static DefaultMutableTreeNode findModuleNode(DefaultMutableTreeNode parent,
                                                       String moduleName) {
    for (int i = 0; i < parent.getChildCount(); i++) {
      DefaultMutableTreeNode child = (DefaultMutableTreeNode) parent.getChildAt(i);
      if (child.getUserObject() instanceof Module module) {
        if (module.getName().equals(moduleName)) {
          return child;
        }
      } else {
        DefaultMutableTreeNode found = findModuleNode(child, moduleName);
        if (found != null) {
          return found;
        }
      }
    }
    return null;
  }

  private void refreshSkipArea() {
    Module focus = myController.getFocusModule();
    myCurrentJarNames = new ArrayList<>();
    myTldAllCheck.setEnabled(false);
    myPluggableAllCheck.setEnabled(false);
    myTldAllCheck.setSelected(false);
    myPluggableAllCheck.setSelected(false);
    myNoModuleLabel.setText("Select a module in the middle list.");

    if (focus == null) {
      mySkipModuleLabel.setText("No module selected");
      repopulateSkipRows();
      return;
    }
    mySkipModuleLabel.setText("Module: " + focus.getName());
    ModuleDependencies deps = ModuleDependencies.forModule(focus);
    myCurrentJarNames = new ArrayList<>(deps.jarNames());
    if (myCurrentJarNames.isEmpty()) {
      myNoModuleLabel.setText("No dependency jars for this module.");
    } else {
      myNoModuleLabel.setText("Dependency jars and per-jar scan skips:");
    }
    myTldAllCheck.setEnabled(!myCurrentJarNames.isEmpty());
    myPluggableAllCheck.setEnabled(!myCurrentJarNames.isEmpty());
    repopulateSkipRows();
  }

  private void repopulateSkipRows() {
    Module focus = myController.getFocusModule();
    String query = mySearchField.getText().trim().toLowerCase(java.util.Locale.ROOT);
    mySkipModel.setRowCount(0);
    for (String jar : myCurrentJarNames) {
      if (!query.isEmpty() && !jar.toLowerCase(java.util.Locale.ROOT).contains(query)) {
        continue;
      }
      boolean tld = focus != null && myController.isTldSkipped(focus, jar);
      boolean pluggable = focus != null && myController.isPluggableSkipped(focus, jar);
      mySkipModel.addRow(new Object[]{jar, tld, pluggable});
    }
  }

  // ---- drag & drop ----

  private final class TreeTransferHandler extends TransferHandler {

    @Override
    public int getSourceActions(JComponent c) {
      return ModuleTreeBuilder.selectedModules((Tree) c).isEmpty() ? NONE : MOVE;
    }

    @Override
    protected Transferable createTransferable(JComponent c) {
      List<String> names = new ArrayList<>();
      for (Module m : ModuleTreeBuilder.selectedModules((Tree) c)) {
        names.add(m.getName());
      }
      if (names.isEmpty()) {
        return null;
      }
      return new StringSelection(String.join("\n", names));
    }

    @Override
    public boolean canImport(TransferSupport support) {
      if (!support.isDataFlavorSupported(DataFlavor.stringFlavor)
          || !(support.getComponent() instanceof Tree)) {
        return false;
      }
      List<String> names = parseNames(support);
      if (names.isEmpty()) {
        return false;
      }
      boolean targetSelected = support.getComponent() == mySelectedTree;
      // The target only accepts modules that actually belong on that side.
      return targetSelected
          ? names.stream().noneMatch(myController::containsModule)
          : names.stream().anyMatch(myController::containsModule);
    }

    @Override
    public boolean importData(TransferSupport support) {
      if (!canImport(support)) {
        return false;
      }
      List<String> names = parseNames(support);
      boolean targetSelected = support.getComponent() == mySelectedTree;
      if (targetSelected) {
        List<Module> modules = new ArrayList<>();
        for (String name : names) {
          modules.add(myController.resolveModule(name));
        }
        moveToSelected(modules);
      } else {
        removeFromSelected(names);
      }
      return true;
    }

    private List<String> parseNames(TransferSupport support) {
      try {
        String data = (String) support.getTransferable()
            .getTransferData(DataFlavor.stringFlavor);
        return List.of(data.split("\n"));
      } catch (Exception e) {
        return List.of();
      }
    }
  }

  private static final class SkipBooleanRenderer implements TableCellRenderer {
    private final JCheckBox myCheckBox = new JCheckBox();

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                   boolean hasFocus, int row, int column) {
      myCheckBox.setHorizontalAlignment(JCheckBox.CENTER);
      myCheckBox.setSelected(Boolean.TRUE.equals(value));
      myCheckBox.setOpaque(false);
      return myCheckBox;
    }
  }
}
