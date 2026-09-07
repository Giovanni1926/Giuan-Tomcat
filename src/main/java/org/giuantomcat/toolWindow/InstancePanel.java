package org.giuantomcat.toolWindow;

import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.JBUI;
import org.giuantomcat.toolWindow.model.TomcatApplication;
import org.giuantomcat.toolWindow.model.TomcatInstance;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.Font;
import java.util.List;

/**
 * One collapsible entry of the server-manager list: a Maven-like instance header (chevron, icon,
 * name, endpoint) followed by the application rows of that instance. Expansion is toggled by
 * clicking the header.
 */
final class InstancePanel extends JPanel {

  private static final String EXPANDED_GLYPH = "\u25BE";
  private static final String COLLAPSED_GLYPH = "\u25B8";

  private final TomcatInstance myInstance;
  private final JBLabel myHeader;
  private final JPanel myAppsContainer = new JPanel();
  private final String myError;
  private boolean myExpanded = true;

  InstancePanel(TomcatInstance instance,
                List<TomcatApplication> applications,
                String error,
                ApplicationActions actions) {
    super(new BorderLayout());
    setOpaque(false);

    myInstance = instance;
    myError = error;
    myHeader = new JBLabel();
    myHeader.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    myHeader.setIcon(TomcatToolWindowIcons.INSTANCE);
    myHeader.setBorder(BorderFactory.createEmptyBorder(3, 6, 3, 6));

    myAppsContainer.setLayout(new BoxLayout(myAppsContainer, BoxLayout.Y_AXIS));
    myAppsContainer.setOpaque(false);
    myAppsContainer.setBorder(BorderFactory.createEmptyBorder(0, 20, 0, 0));

    if (!applications.isEmpty()) {
      for (TomcatApplication application : applications) {
        myAppsContainer.add(new ApplicationRowPanel(instance, application, actions));
      }
    } else if (error != null) {
      myAppsContainer.add(instanceMessage(error));
    } else {
      myAppsContainer.add(instanceMessage("No applications"));
    }

    add(myHeader, BorderLayout.NORTH);
    add(myAppsContainer, BorderLayout.CENTER);

    refreshHeader();
    setExpanded(true);
  }

  TomcatInstance getInstance() {
    return myInstance;
  }

  JBLabel getHeader() {
    return myHeader;
  }

  boolean isExpanded() {
    return myExpanded;
  }

  void setExpanded(boolean expanded) {
    myExpanded = expanded;
    myAppsContainer.setVisible(expanded);
    refreshHeader();
  }

  void setSelected(boolean selected) {
    if (selected) {
      myHeader.setOpaque(true);
      myHeader.setBackground(com.intellij.ui.JBColor.namedColor(
          "List.selectionBackground", com.intellij.util.ui.UIUtil.getListBackground()));
      myHeader.setForeground(com.intellij.ui.JBColor.namedColor(
          "List.selectionForeground", com.intellij.util.ui.UIUtil.getListForeground()));
    } else {
      myHeader.setOpaque(false);
      myHeader.setForeground(null);
    }
  }

  private void refreshHeader() {
    String chevron = myExpanded ? EXPANDED_GLYPH : COLLAPSED_GLYPH;
    String name = myInstance.displayName();
    myHeader.setText(chevron + "  " + name + "    " + myInstance.endpointLabel());
    myHeader.setFont(myHeader.getFont().deriveFont(Font.BOLD));
    if (myError != null) {
      myHeader.setForeground(com.intellij.ui.JBColor.namedColor("List.errorForeground",
          new java.awt.Color(0xd04040)));
    }
    myHeader.setToolTipText(myError != null
        ? myError
        : myInstance.hasSsh()
            ? myInstance.endpointLabel() + "\n" + myInstance.sshLabel()
            : myInstance.endpointLabel());
  }

  private static JBLabel instanceMessage(String text) {
    JBLabel label = new JBLabel(text);
    label.setFont(label.getFont().deriveFont(Font.PLAIN, 11f));
    label.setForeground(com.intellij.ui.JBColor.GRAY);
    label.setBorder(JBUI.Borders.empty(4, 8));
    return label;
  }
}
