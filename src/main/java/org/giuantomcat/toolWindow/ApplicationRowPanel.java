package org.giuantomcat.toolWindow;

import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.JBUI;
import org.giuantomcat.toolWindow.model.TomcatApplication;
import org.giuantomcat.toolWindow.model.TomcatInstance;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;

/**
 * One row of the application list of an instance: status dot, context path, session count and the
 * lifecycle actions (real icon buttons, so hover tooltips work natively).
 */
final class ApplicationRowPanel extends JPanel {

  private static final Color RUNNING_COLOR = new Color(0x2e9e44);
  private static final Color STOPPED_COLOR = new Color(0xd04040);

  ApplicationRowPanel(TomcatInstance instance,
                      TomcatApplication application,
                      ApplicationActions actions) {
    super(new BorderLayout(6, 0));
    setBorder(BorderFactory.createCompoundBorder(
        BorderFactory.createMatteBorder(0, 0, 1, 0,
            com.intellij.ui.JBColor.namedColor("Separator.separatorColor", new Color(0xCDCDCD))),
        JBUI.Borders.empty(4, 8, 4, 2)));
    setOpaque(true);

    JLabel statusDot = new JLabel("\u25CF");
    statusDot.setFont(statusDot.getFont().deriveFont(Font.BOLD, 14f));
    statusDot.setForeground(application.isRunning() ? RUNNING_COLOR : STOPPED_COLOR);

    JBLabel context = new JBLabel(application.getContextPath());
    context.setFont(context.getFont().deriveFont(Font.BOLD));

    JBLabel sessions = new JBLabel("Sessions: " + application.getSessions());
    sessions.setFont(sessions.getFont().deriveFont(Font.PLAIN, 11f));
    sessions.setForeground(com.intellij.ui.JBColor.GRAY);

    JPanel info = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
    info.setOpaque(false);
    info.add(statusDot);
    info.add(context);
    info.add(sessions);

    JPanel actionsPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 0));
    actionsPanel.setOpaque(false);
    if (application.isRunning()) {
      actionsPanel.add(actionButton(TomcatToolWindowIcons.APP_STOP, "Stop application",
          () -> actions.stop(instance, application)));
      actionsPanel.add(actionButton(TomcatToolWindowIcons.APP_RELOAD, "Reload application",
          () -> actions.reload(instance, application)));
    } else {
      actionsPanel.add(actionButton(TomcatToolWindowIcons.APP_START, "Start application",
          () -> actions.start(instance, application)));
    }
    actionsPanel.add(actionButton(TomcatToolWindowIcons.APP_UNDEPLOY, "Undeploy application",
        () -> actions.undeploy(instance, application)));

    add(info, BorderLayout.WEST);
    add(actionsPanel, BorderLayout.EAST);
  }

  private static JButton actionButton(javax.swing.Icon icon, String tooltip, Runnable action) {
    JButton button = new JButton(icon);
    button.setToolTipText(tooltip);
    button.setBorder(JBUI.Borders.empty(2));
    button.setContentAreaFilled(false);
    button.setFocusPainted(false);
    button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    button.setPreferredSize(new Dimension(icon.getIconWidth() + 8, icon.getIconHeight() + 8));
    button.addActionListener(e -> action.run());
    return button;
  }
}
