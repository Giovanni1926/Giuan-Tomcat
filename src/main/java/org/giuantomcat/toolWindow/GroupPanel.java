package org.giuantomcat.toolWindow;

import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.JBUI;
import org.giuantomcat.toolWindow.model.TomcatGroup;
import org.jetbrains.annotations.Nullable;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.Font;

/**
 * One collapsible section of the server-manager list grouping several {@link InstancePanel}s: a
 * Maven-like header (chevron, folder icon, group name, member count) followed by the instance
 * entries of that group. The {@code Ungrouped} pseudo-section is represented with a {@code null}
 * group (no context actions available). Expansion is toggled by clicking the header.
 */
final class GroupPanel extends JPanel {

  private static final String EXPANDED_GLYPH = "\u25BE";
  private static final String COLLAPSED_GLYPH = "\u25B8";

  private final TomcatGroup myGroup;
  private final JBLabel myHeader;
  private final JPanel myMembersContainer = new JPanel();
  private final int myMemberCount;
  private boolean myExpanded = true;

  GroupPanel(@Nullable TomcatGroup group, int memberCount) {
    super(new BorderLayout());
    setOpaque(false);

    myGroup = group;
    myMemberCount = memberCount;

    myHeader = new JBLabel();
    myHeader.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    myHeader.setIcon(TomcatToolWindowIcons.GROUP);
    myHeader.setBorder(BorderFactory.createEmptyBorder(3, 6, 3, 6));

    myMembersContainer.setLayout(new BoxLayout(myMembersContainer, BoxLayout.Y_AXIS));
    myMembersContainer.setOpaque(false);
    myMembersContainer.setBorder(BorderFactory.createEmptyBorder(0, 12, 0, 0));

    add(myHeader, BorderLayout.NORTH);
    add(myMembersContainer, BorderLayout.CENTER);

    refreshHeader();
  }

  @Nullable
  TomcatGroup getGroup() {
    return myGroup;
  }

  JBLabel getHeader() {
    return myHeader;
  }

  boolean isExpanded() {
    return myExpanded;
  }

  void setExpanded(boolean expanded) {
    myExpanded = expanded;
    myMembersContainer.setVisible(expanded);
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

  void addMember(InstancePanel panel) {
    myMembersContainer.add(panel);
  }

  private void refreshHeader() {
    String chevron = myExpanded ? EXPANDED_GLYPH : COLLAPSED_GLYPH;
    String title = myGroup == null ? ServerManagerPanel.UNGROUPED_SECTION_TITLE : myGroup.displayName();
    myHeader.setText(chevron + "  " + title + "    (" + myMemberCount + ")");
    myHeader.setFont(myHeader.getFont().deriveFont(Font.BOLD));
    myHeader.setToolTipText(null);
  }
}
