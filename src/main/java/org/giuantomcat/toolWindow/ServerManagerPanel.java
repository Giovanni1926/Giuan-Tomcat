package org.giuantomcat.toolWindow;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.Separator;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.util.ui.JBUI;
import org.giuantomcat.toolWindow.action.AddInstanceAction;
import org.giuantomcat.toolWindow.action.CollapseAllAction;
import org.giuantomcat.toolWindow.action.DeployAction;
import org.giuantomcat.toolWindow.action.EditInstanceAction;
import org.giuantomcat.toolWindow.action.ExpandAllAction;
import org.giuantomcat.toolWindow.action.LogsAction;
import org.giuantomcat.toolWindow.action.RefreshAction;
import org.giuantomcat.toolWindow.action.RemoveInstanceAction;
import org.giuantomcat.toolWindow.api.SshLogClient;
import org.giuantomcat.toolWindow.api.TomcatManagerClient;
import org.giuantomcat.toolWindow.dialog.DeployApplicationDialog;
import org.giuantomcat.toolWindow.dialog.InstanceDialog;
import org.giuantomcat.toolWindow.log.LogViewPanel;
import org.giuantomcat.toolWindow.model.TomcatApplication;
import org.giuantomcat.toolWindow.model.TomcatGroup;
import org.giuantomcat.toolWindow.model.TomcatInstance;
import org.giuantomcat.toolWindow.settings.TomcatCredentialsStore;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Font;
import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

/**
 * Main view of the server-manager tool window: a Maven-like header with an {@link ActionToolbar}
 * and a scrollable list of sections. Each section is a collapsible {@link GroupPanel} (a named
 * group of instances, or the "Ungrouped" pseudo-section) containing its {@link InstancePanel}s
 * (all configured instances, expandable). Network calls always run in background through
 * {@link ProgressManager}.
 */
public final class ServerManagerPanel extends JPanel implements ApplicationActions {

  private static final String TOOLBAR_PLACE = "giuan-tomcat-server-manager";

  static final String UNGROUPED_SECTION_TITLE = "Ungrouped";

  private final Project myProject;
  private final ToolWindow myToolWindow;
  private final ServerManagerController myController;

  private final JPanel myListContainer = new JPanel();
  private final JBLabel myEmptyLabel = new JBLabel("No Tomcat instance configured. Click + to add one.");
  private final List<InstancePanel> myInstancePanels = new ArrayList<>();
  private final List<GroupPanel> myGroupPanels = new ArrayList<>();
  private final ActionToolbar myToolbar;
  private final SshLogClient mySshLogClient = new SshLogClient();

  private String mySelectedInstanceId = null;
  private String mySelectedGroupId = null;
  private boolean mySelectedUngrouped = false;
  private final Set<String> myCollapsedInstanceIds = new LinkedHashSet<>();
  private final Set<String> myCollapsedGroupIds = new LinkedHashSet<>();
  private boolean myUngroupedCollapsed = false;

  public ServerManagerPanel(Project project, ToolWindow toolWindow) {
    super(new BorderLayout());
    myProject = project;
    myToolWindow = toolWindow;
    myController = new ServerManagerController();

    myListContainer.setLayout(new BoxLayout(myListContainer, BoxLayout.Y_AXIS));
    myListContainer.setOpaque(false);
    myListContainer.setBorder(JBUI.Borders.empty(2));

    myEmptyLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
    myEmptyLabel.setAlignmentX(0.5f);
    myEmptyLabel.setBorder(JBUI.Borders.empty(20));

    myToolbar = createToolbar();

    JPanel center = new JPanel(new BorderLayout());
    center.add(myListContainer, BorderLayout.NORTH);
    JBScrollPane scroll = new JBScrollPane(center);
    scroll.setBorder(JBUI.Borders.empty());
    scroll.getVerticalScrollBar().setUnitIncrement(16);

    add(createHeader(), BorderLayout.NORTH);
    add(scroll, BorderLayout.CENTER);

    rebuild();
    if (!myController.isEmpty()) {
      refreshAll();
    }
  }

  // ---- exposed to toolbar actions ----

  public boolean hasInstances() {
    return !myController.isEmpty();
  }

  public boolean hasSelection() {
    return selectedInstance() != null;
  }

  public boolean hasGroupSelection() {
    return selectedDeployableGroupId() != null;
  }

  public boolean hasDeployTarget() {
    if (selectedInstance() != null) {
      return true;
    }
    String groupId = selectedDeployableGroupId();
    return groupId != null && myController.hasMembers(groupId);
  }

  public String deployActionText() {
    return hasGroupSelection() ? "Deploy to Group" : "Deploy";
  }

  public boolean hasLogsAvailable() {
    TomcatInstance selected = selectedInstance();
    return selected != null && selected.hasSsh();
  }

  public void refreshAll() {
    List<TomcatInstance> instances = myController.getInstances();
    if (instances.isEmpty()) {
      return;
    }
    runInBackground("Refreshing Tomcat instances", () -> {
      for (TomcatInstance instance : instances) {
        refreshInstanceSnapshot(instance);
      }
      return null;
    }, ignored -> rebuild());
  }

  public void expandAll() {
    myCollapsedInstanceIds.clear();
    myCollapsedGroupIds.clear();
    myUngroupedCollapsed = false;
    for (GroupPanel group : myGroupPanels) {
      group.setExpanded(true);
    }
    for (InstancePanel panel : myInstancePanels) {
      panel.setExpanded(true);
    }
  }

  public void collapseAll() {
    for (GroupPanel group : myGroupPanels) {
      group.setExpanded(false);
      TomcatGroup g = group.getGroup();
      if (g == null) {
        myUngroupedCollapsed = true;
      } else {
        myCollapsedGroupIds.add(g.id);
      }
    }
    for (InstancePanel panel : myInstancePanels) {
      panel.setExpanded(false);
      myCollapsedInstanceIds.add(panel.getInstance().id);
    }
  }

  public void addInstance() {
    InstanceDialog dialog = new InstanceDialog(null, myController.getGroups(), null, null, null);
    if (!dialog.showAndGet()) {
      return;
    }
    persistNewGroups(dialog);
    TomcatInstance instance = dialog.getInstance();
    if (instance == null) {
      return;
    }
    myController.addInstance(instance);
    persistCredentials(instance, dialog);
    select(instance.id);
    rebuild();
    refreshInstance(instance);
  }

  public void editInstance() {
    TomcatInstance selected = selectedInstance();
    if (selected == null) {
      return;
    }
    TomcatCredentialsStore store = myController.credentials(selected);
    InstanceDialog dialog = new InstanceDialog(selected, myController.getGroups(),
        store.getManagerPassword(), store.getSshPassword(), store.getSshPassphrase());
    if (!dialog.showAndGet()) {
      return;
    }
    persistNewGroups(dialog);
    TomcatInstance instance = dialog.getInstance();
    if (instance == null) {
      return;
    }
    myController.updateInstance(instance);
    persistCredentials(instance, dialog);
    select(instance.id);
    rebuild();
    refreshInstance(instance);
  }

  public void removeInstance() {
    TomcatInstance selected = selectedInstance();
    if (selected == null) {
      return;
    }
    int answer = Messages.showYesNoDialog(myProject,
        "Remove instance \"" + selected.displayName() + "\"?", "Giuan Tomcat",
        Messages.getQuestionIcon());
    if (answer != Messages.YES) {
      return;
    }
    myController.removeInstance(selected.id);
    myCollapsedInstanceIds.remove(selected.id);
    if (selected.id.equals(mySelectedInstanceId)) {
      mySelectedInstanceId = null;
    }
    rebuild();
  }

  public void deploy() {
    List<TomcatInstance> targets = deployTargets();
    if (targets.isEmpty()) {
      Messages.showInfoMessage(myProject,
          "Select an instance or a group containing instances to deploy.", "Giuan Tomcat");
      return;
    }
    DeployApplicationDialog dialog = new DeployApplicationDialog();
    if (!dialog.showAndGet()) {
      return;
    }
    String contextPath = dialog.getContextPath();
    File war = dialog.getWarFile();
    if (war == null) {
      return;
    }
    deployToInstances(contextPath, war, targets);
  }

  public void openLogs() {
    TomcatInstance selected = selectedInstance();
    if (selected == null) {
      return;
    }
    if (!selected.hasSsh()) {
      Messages.showInfoMessage(myProject,
          "SSH is not configured for \"" + selected.displayName()
              + "\". Edit the instance and enable SSH to consult the logs.",
          "Giuan Tomcat");
      return;
    }
    TomcatCredentialsStore store = myController.credentials(selected);
    LogViewPanel logView = new LogViewPanel(myProject);
    Content content = ContentFactory.getInstance().createContent(
        logView.getComponent(), "Logs - " + selected.displayName(), false);
    content.setCloseable(true);
    content.setDisposer(logView);
    myToolWindow.getContentManager().addContent(content);
    myToolWindow.getContentManager().setSelectedContent(content);

    SshLogClient.TailHandle tail = mySshLogClient.tail(selected,
        store.getSshPassword(), store.getSshPassphrase(),
        logView::appendLine,
        error -> ApplicationManager.getApplication().invokeLater(
            () -> logView.appendError("SSH error: " + errorMessage(error))));
    logView.setTail(tail);
  }

  // ---- group management ----

  public void renameSelectedGroup() {
    GroupPanel selected = selectedGroupPanel();
    if (selected == null || selected.getGroup() == null) {
      return;
    }
    TomcatGroup group = selected.getGroup();
    String name = Messages.showInputDialog(myProject,
        "Enter a new name for the group:", "Rename Group",
        Messages.getQuestionIcon(), group.name, null);
    if (name == null || name.trim().isEmpty()) {
      return;
    }
    name = name.trim();
    if (!name.equals(group.name) && myController.findGroupByName(name) != null) {
      Messages.showErrorDialog(myProject, "A group with this name already exists.", "Rename Group");
      return;
    }
    TomcatGroup updated = new TomcatGroup();
    updated.id = group.id;
    updated.name = name;
    myController.updateGroup(updated);
    selectGroup(group.id);
    rebuild();
  }

  public void removeSelectedGroup() {
    GroupPanel selected = selectedGroupPanel();
    if (selected == null || selected.getGroup() == null) {
      return;
    }
    TomcatGroup group = selected.getGroup();
    int members = myController.getMembers(group.id).size();
    String detail = members > 0
        ? " The " + members + " instance(s) will be moved to \"" + UNGROUPED_SECTION_TITLE + "\"."
        : "";
    int answer = Messages.showYesNoDialog(myProject,
        "Remove group \"" + group.name + "\"?" + detail, "Giuan Tomcat",
        Messages.getQuestionIcon());
    if (answer != Messages.YES) {
      return;
    }
    myController.removeGroup(group.id);
    myCollapsedGroupIds.remove(group.id);
    if (group.id.equals(mySelectedGroupId)) {
      mySelectedGroupId = null;
    }
    rebuild();
  }

  public void deploySelectedGroup() {
    if (!hasGroupSelection()) {
      return;
    }
    deploy();
  }

  // ---- ApplicationActions ----

  @Override
  public void start(TomcatInstance instance, TomcatApplication application) {
    lifecycle(instance, application, "Start", client -> client.start(application.getContextPath()));
  }

  @Override
  public void stop(TomcatInstance instance, TomcatApplication application) {
    lifecycle(instance, application, "Stop", client -> client.stop(application.getContextPath()));
  }

  @Override
  public void reload(TomcatInstance instance, TomcatApplication application) {
    lifecycle(instance, application, "Reload", client -> client.reload(application.getContextPath()));
  }

  @Override
  public void undeploy(TomcatInstance instance, TomcatApplication application) {
    int answer = Messages.showYesNoDialog(myProject,
        "Undeploy \"" + application.getContextPath() + "\" from "
            + instance.displayName() + "?", "Giuan Tomcat", Messages.getQuestionIcon());
    if (answer != Messages.YES) {
      return;
    }
    lifecycle(instance, application, "Undeploy",
        client -> client.undeploy(application.getContextPath()));
  }

  private void lifecycle(TomcatInstance instance,
                         TomcatApplication application,
                         String action,
                         ClientCommand command) {
    runInBackground(action + " " + application.getContextPath(), () -> {
      command.run(myController.managerClient(instance));
      return null;
    }, ignored -> refreshInstance(instance));
  }

  // ---- view construction ----

  private JComponent createHeader() {
    JPanel header = new JPanel(new BorderLayout());
    header.add(myToolbar.getComponent(), BorderLayout.SOUTH);
    header.setBorder(JBUI.Borders.customLine(
        com.intellij.ui.JBColor.namedColor("Separator.separatorColor",
            com.intellij.ui.JBColor.LIGHT_GRAY), 0, 0, 1, 0));
    return header;
  }

  private ActionToolbar createToolbar() {
    DefaultActionGroup group = new DefaultActionGroup();
    group.add(new RefreshAction(this));
    group.add(new ExpandAllAction(this));
    group.add(new CollapseAllAction(this));
    group.add(new Separator());
    group.add(new AddInstanceAction(this));
    group.add(new EditInstanceAction(this));
    group.add(new RemoveInstanceAction(this));
    group.add(new Separator());
    group.add(new DeployAction(this));
    group.add(new LogsAction(this));

    ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar(TOOLBAR_PLACE, group, true);
    toolbar.setTargetComponent(this);
    return toolbar;
  }

  private void rebuild() {
    for (GroupPanel existing : myGroupPanels) {
      if (!existing.isExpanded()) {
        TomcatGroup g = existing.getGroup();
        if (g == null) {
          myUngroupedCollapsed = true;
        } else {
          myCollapsedGroupIds.add(g.id);
        }
      }
    }
    for (InstancePanel existing : myInstancePanels) {
      if (!existing.isExpanded()) {
        myCollapsedInstanceIds.add(existing.getInstance().id);
      }
    }

    myListContainer.removeAll();
    myInstancePanels.clear();
    myGroupPanels.clear();

    List<TomcatInstance> allInstances = myController.getInstances();
    if (allInstances.isEmpty() && !myController.hasGroups()) {
      myListContainer.add(myEmptyLabel);
    } else {
      myListContainer.add(Box.createVerticalStrut(2));
      for (TomcatGroup group : myController.getGroups()) {
        List<TomcatInstance> members = myController.getMembers(group.id);
        GroupPanel groupPanel = new GroupPanel(group, members.size());
        groupPanel.setExpanded(!myCollapsedGroupIds.contains(group.id));
        wireGroupHeader(groupPanel);
        addMembers(groupPanel, members);
        myGroupPanels.add(groupPanel);
        myListContainer.add(groupPanel);
        myListContainer.add(Box.createVerticalStrut(4));
      }
      List<TomcatInstance> ungrouped = myController.getUngroupedInstances();
      if (!ungrouped.isEmpty()) {
        GroupPanel ungroupedPanel = new GroupPanel(null, ungrouped.size());
        ungroupedPanel.setExpanded(!myUngroupedCollapsed);
        wireGroupHeader(ungroupedPanel);
        addMembers(ungroupedPanel, ungrouped);
        myGroupPanels.add(ungroupedPanel);
        myListContainer.add(ungroupedPanel);
        myListContainer.add(Box.createVerticalStrut(4));
      }
    }
    refreshSelectionColors();
    refreshToolbarActions();
    revalidate();
    repaint();
  }

  private void addMembers(GroupPanel groupPanel, List<TomcatInstance> instances) {
    for (TomcatInstance instance : instances) {
      List<TomcatApplication> applications = myController.getApplications(instance.id);
      String error = myController.getError(instance.id);
      InstancePanel panel = new InstancePanel(instance, applications, error, this);
      panel.setExpanded(!myCollapsedInstanceIds.contains(instance.id));
      wireHeader(panel);
      myInstancePanels.add(panel);
      groupPanel.addMember(panel);
    }
  }

  private void refreshToolbarActions() {
    if (myToolbar.getComponent().isDisplayable()) {
      myToolbar.updateActionsAsync();
    }
  }

  private void wireGroupHeader(GroupPanel groupPanel) {
    JBLabel header = groupPanel.getHeader();
    header.addMouseListener(new java.awt.event.MouseAdapter() {
      @Override
      public void mouseClicked(java.awt.event.MouseEvent e) {
        if (SwingUtilities.isLeftMouseButton(e) && e.getClickCount() == 1) {
          groupPanel.setExpanded(!groupPanel.isExpanded());
          selectGroupPanel(groupPanel);
          refreshSelectionColors();
          refreshToolbarActions();
        }
      }

      @Override
      public void mousePressed(java.awt.event.MouseEvent e) {
        showGroupPopup(groupPanel, e);
      }

      @Override
      public void mouseReleased(java.awt.event.MouseEvent e) {
        showGroupPopup(groupPanel, e);
      }
    });
  }

  private void showGroupPopup(GroupPanel groupPanel, java.awt.event.MouseEvent e) {
    if (!e.isPopupTrigger() || groupPanel.getGroup() == null) {
      return;
    }
    selectGroupPanel(groupPanel);
    refreshSelectionColors();
    refreshToolbarActions();
    javax.swing.JPopupMenu menu = new javax.swing.JPopupMenu();
    menu.add(item("Deploy to Group\u2026", this::deploySelectedGroup));
    menu.addSeparator();
    menu.add(item("Rename Group\u2026", this::renameSelectedGroup));
    menu.add(item("Remove Group\u2026", this::removeSelectedGroup));
    menu.show(e.getComponent(), e.getX(), e.getY());
  }

  private void wireHeader(InstancePanel panel) {
    JBLabel header = panel.getHeader();
    header.addMouseListener(new java.awt.event.MouseAdapter() {
      @Override
      public void mouseClicked(java.awt.event.MouseEvent e) {
        if (SwingUtilities.isLeftMouseButton(e) && e.getClickCount() == 1) {
          panel.setExpanded(!panel.isExpanded());
          select(panel.getInstance().id);
          refreshSelectionColors();
          refreshToolbarActions();
        }
      }

      @Override
      public void mousePressed(java.awt.event.MouseEvent e) {
        showInstancePopup(panel, e);
      }

      @Override
      public void mouseReleased(java.awt.event.MouseEvent e) {
        showInstancePopup(panel, e);
      }
    });
  }

  private void showInstancePopup(InstancePanel panel, java.awt.event.MouseEvent e) {
    if (!e.isPopupTrigger()) {
      return;
    }
    TomcatInstance instance = panel.getInstance();
    select(instance.id);
    refreshSelectionColors();
    refreshToolbarActions();
    javax.swing.JPopupMenu menu = new javax.swing.JPopupMenu();
    menu.add(item("Refresh", () -> refreshInstance(instance)));
    menu.addSeparator();
    menu.add(item("Edit\u2026", this::editInstance));
    menu.add(item("Remove\u2026", this::removeInstance));
    menu.addSeparator();
    menu.add(item("Deploy\u2026", this::deploy));
    if (instance.hasSsh()) {
      menu.add(item("Open Logs", this::openLogs));
    } else {
      menu.add(disabledItem("Open Logs (SSH not configured)"));
    }
    menu.show(e.getComponent(), e.getX(), e.getY());
  }

  private static javax.swing.JMenuItem disabledItem(String text) {
    javax.swing.JMenuItem menuItem = new javax.swing.JMenuItem(text);
    menuItem.setEnabled(false);
    return menuItem;
  }

  private static javax.swing.JMenuItem item(String text, Runnable action) {
    javax.swing.JMenuItem menuItem = new javax.swing.JMenuItem(text);
    menuItem.addActionListener(e -> action.run());
    return menuItem;
  }

  private void refreshSelectionColors() {
    for (InstancePanel panel : myInstancePanels) {
      panel.setSelected(panel.getInstance().id.equals(mySelectedInstanceId));
    }
    for (GroupPanel group : myGroupPanels) {
      TomcatGroup g = group.getGroup();
      boolean selected = g == null
          ? mySelectedUngrouped
          : g.id.equals(mySelectedGroupId);
      group.setSelected(selected);
    }
  }

  // ---- selection ----

  private void select(String instanceId) {
    mySelectedInstanceId = instanceId;
    mySelectedGroupId = null;
    mySelectedUngrouped = false;
  }

  private void selectGroupPanel(GroupPanel groupPanel) {
    TomcatGroup group = groupPanel.getGroup();
    if (group == null) {
      mySelectedUngrouped = true;
      mySelectedGroupId = null;
    } else {
      mySelectedGroupId = group.id;
      mySelectedUngrouped = false;
    }
    mySelectedInstanceId = null;
  }

  private void selectGroup(String groupId) {
    mySelectedGroupId = groupId;
    mySelectedInstanceId = null;
    mySelectedUngrouped = false;
  }

  @Nullable
  private TomcatInstance selectedInstance() {
    return myController.findById(mySelectedInstanceId == null ? "" : mySelectedInstanceId);
  }

  @Nullable
  private String selectedDeployableGroupId() {
    if (mySelectedGroupId == null) {
      return null;
    }
    return myController.findGroupById(mySelectedGroupId) != null ? mySelectedGroupId : null;
  }

  @Nullable
  private GroupPanel selectedGroupPanel() {
    for (GroupPanel group : myGroupPanels) {
      TomcatGroup g = group.getGroup();
      if (g != null && g.id.equals(mySelectedGroupId)) {
        return group;
      }
    }
    return null;
  }

  private List<TomcatInstance> deployTargets() {
    TomcatInstance instance = selectedInstance();
    if (instance != null) {
      return List.of(instance);
    }
    String groupId = selectedDeployableGroupId();
    if (groupId != null) {
      return myController.getMembers(groupId);
    }
    return List.of();
  }

  // ---- networking ----

  private void refreshInstance(TomcatInstance instance) {
    if (instance == null) {
      return;
    }
    runInBackground("Refreshing " + instance.displayName(), () -> {
      refreshInstanceSnapshot(instance);
      return null;
    }, ignored -> rebuild());
  }

  private void refreshInstanceSnapshot(TomcatInstance instance) {
    try {
      List<TomcatApplication> applications = myController.managerClient(instance).list();
      myController.setApplications(instance.id, applications);
      myController.clearError(instance.id);
    } catch (Exception e) {
      myController.setApplications(instance.id, List.of());
      myController.setError(instance.id, errorMessage(e));
    }
  }

  private void deployToInstances(String contextPath, File war, List<TomcatInstance> targets) {
    String title = targets.size() == 1
        ? "Deploying " + contextPath + " to " + targets.get(0).displayName()
        : "Deploying " + contextPath + " to " + targets.size() + " Tomcat instances";
    ProgressManager.getInstance().run(new Task.Backgroundable(myProject, title, false) {
      private final List<String> failures = new ArrayList<>();

      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        for (TomcatInstance target : targets) {
          indicator.setText("Deploying to " + target.displayName());
          try {
            myController.managerClient(target).deploy(contextPath, war);
          } catch (Throwable t) {
            failures.add(target.displayName() + ": " + errorMessage(t));
          }
        }
        for (TomcatInstance target : targets) {
          refreshInstanceSnapshot(target);
        }
      }

      @Override
      public void onSuccess() {
        if (!failures.isEmpty()) {
          Messages.showErrorDialog(myProject,
              "Deployment failed for:\n" + String.join("\n", failures), "Giuan Tomcat");
        } else if (targets.size() > 1) {
          Messages.showInfoMessage(myProject,
              "Deployment completed on " + targets.size() + " instances.", "Giuan Tomcat");
        }
        rebuild();
      }
    });
  }

  private void persistCredentials(TomcatInstance instance, InstanceDialog dialog) {
    TomcatCredentialsStore store = myController.credentials(instance);
    String managerPassword = dialog.getManagerPassword();
    if (managerPassword != null) {
      store.setManagerPassword(managerPassword);
    }
    if (!instance.hasSsh()) {
      return;
    }
    String sshPassword = dialog.getSshPassword();
    if (sshPassword != null) {
      store.setSshPassword(sshPassword);
    }
    String sshPassphrase = dialog.getSshPassphrase();
    if (sshPassphrase != null) {
      store.setSshPassphrase(sshPassphrase);
    }
  }

  private void persistNewGroups(InstanceDialog dialog) {
    for (TomcatGroup group : dialog.getNewGroups()) {
      if (myController.findGroupById(group.id) == null) {
        myController.addGroup(group);
      }
    }
  }

  private <T> void runInBackground(String title, Callable<T> work, Consumer<T> onSuccess) {
    ProgressManager.getInstance().run(new Task.Backgroundable(myProject, title, false) {
      private T result;
      private Throwable error;

      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        try {
          result = work.call();
        } catch (Throwable t) {
          error = t;
        }
      }

      @Override
      public void onSuccess() {
        if (error != null) {
          Messages.showErrorDialog(myProject, errorMessage(error), "Giuan Tomcat");
        } else {
          onSuccess.accept(result);
        }
      }
    });
  }

  private static String errorMessage(Throwable error) {
    return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
  }

  private interface ClientCommand {
    void run(TomcatManagerClient client) throws Exception;
  }
}
