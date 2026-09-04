package org.giuantomcat.runConfiguration.ui;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.treeStructure.Tree;
import org.jetbrains.annotations.NotNull;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Groups modules by folder into a reusable tree (Available and Selected). */
public final class ModuleTreeBuilder {

  private ModuleTreeBuilder() {
  }

  /** Creates a pre-configured module tree (folder grouping, single selection). */
  public static Tree createTree() {
    DefaultTreeModel model = new DefaultTreeModel(new DefaultMutableTreeNode());
    Tree tree = new Tree(model);
    tree.setRootVisible(false);
    tree.setShowsRootHandles(true);
    tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
    tree.setCellRenderer(new FolderModuleCellRenderer());
    return tree;
  }

  public static DefaultMutableTreeNode buildRoot(List<Module> modules) {
    DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode();
    if (modules == null || modules.isEmpty()) {
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

  public static void rebuild(Tree tree, List<Module> modules) {
    DefaultTreeModel model = (DefaultTreeModel) tree.getModel();
    model.setRoot(buildRoot(modules));
    expandAll(tree);
  }

  public static void expandAll(Tree tree) {
    Object root = tree.getModel().getRoot();
    if (root instanceof DefaultMutableTreeNode node) {
      expandNode(tree, node);
    }
  }

  private static void expandNode(Tree tree, DefaultMutableTreeNode node) {
    tree.expandPath(new TreePath(node.getPath()));
    Enumeration<TreeNode> children = node.children();
    while (children.hasMoreElements()) {
      DefaultMutableTreeNode child = (DefaultMutableTreeNode) children.nextElement();
      if (!child.isLeaf()) {
        expandNode(tree, child);
      }
    }
  }

  private static void fillFolder(DefaultMutableTreeNode parent, FolderNode folder) {
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

  private static List<String> absoluteSegments(Module module) {
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
    if (paths.isEmpty()) {
      return List.of();
    }
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
    private final Map<String, FolderNode> children = new LinkedHashMap<>();
    private Module module;

    private FolderNode(String name) {
      this.name = name;
    }
  }

  /** Renderer for module and folder nodes. */
  public static final class FolderModuleCellRenderer extends ColoredTreeCellRenderer {
    @Override
    public void customizeCellRenderer(@NotNull javax.swing.JTree tree, Object value,
                                      boolean selected, boolean expanded, boolean leaf, int row,
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

  /** Extracts the modules selected in the tree (leaf nodes with a Module userObject). */
  public static List<Module> selectedModules(Tree tree) {
    List<Module> result = new ArrayList<>();
    TreePath[] paths = tree.getSelectionPaths();
    if (paths == null) {
      return result;
    }
    for (TreePath path : paths) {
      if (path.getLastPathComponent() instanceof DefaultMutableTreeNode node
          && node.getUserObject() instanceof Module module) {
        result.add(module);
      }
    }
    return result;
  }
}
