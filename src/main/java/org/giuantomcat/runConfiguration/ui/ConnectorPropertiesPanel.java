package org.giuantomcat.runConfiguration.ui;

import com.intellij.icons.AllIcons;
import com.intellij.ui.TitledSeparator;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.giuantomcat.runConfiguration.settings.ConnectorProperties;

import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Window;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Editor for the additional {@code <Connector>} attributes written into the generated
 * {@code server.xml}. The section is a collapsible titled panel replicating the
 * {@code HideableDecorator} behaviour of the "Before launch" run-configuration section (a
 * clickable {@link TitledSeparator} with an arrow that hides/shows the content): inside, a
 * dropdown lists the fixed {@link ConnectorProperties#CATALOG}, a text field holds the value and
 * the configured attributes are shown in a striped table (one row per attribute).
 */
public final class ConnectorPropertiesPanel {

  private static final int COL_NAME = 0;
  private static final int COL_VALUE = 1;

  private static final String SECTION_TITLE = "Connector attributes";
  private static final int ICON_TEXT_GAP = 5;
  private static final String TABLE_HEADER_FONT_KEY = "TableHeader.font";

  private final Set<String> myTokens = new LinkedHashSet<>();

  private final JComboBox<String> myAttributeCombo =
      new JComboBox<>(ConnectorProperties.names().toArray(new String[0]));
  private final JTextField myValueField = new JTextField();
  private final JButton myAddButton = new JButton("Add / update");
  private final JButton myRemoveButton = new JButton("Remove");

  private final DefaultTableModel myTableModel = new DefaultTableModel(
      new Object[]{"Attribute", "Value"}, 0) {
    @Override
    public boolean isCellEditable(int row, int column) {
      return false;
    }
  };
  private final JBTable myTable = new JBTable(myTableModel);

  private final TitledSeparator mySeparator = new TitledSeparator(SECTION_TITLE);
  private final JPanel myBody = new JPanel(new BorderLayout(0, 6));
  private final JPanel myPanel = new JPanel(new BorderLayout());
  private boolean myExpanded = true;
  private Dimension myPreviousBodySize;

  public ConnectorPropertiesPanel() {
    prefillFromCombo();
    myAttributeCombo.addActionListener(e -> prefillFromCombo());
    myAddButton.addActionListener(e -> addOrUpdate());
    myRemoveButton.addActionListener(e -> removeSelected());

    myTable.setStriped(true);
    myTable.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
    myTable.setRowHeight(JBUI.scale(22));
    myTable.getTableHeader().setReorderingAllowed(false);
    myTable.getTableHeader().setDefaultRenderer(new HeaderRenderer());
    myTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    myTable.getColumnModel().getColumn(COL_NAME).setPreferredWidth(220);
    myTable.getColumnModel().getColumn(COL_VALUE).setPreferredWidth(260);

    JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
    myAttributeCombo.setPreferredSize(JBUI.size(200, myAttributeCombo.getPreferredSize().height));
    myValueField.setPreferredSize(JBUI.size(180, myValueField.getPreferredSize().height));
    controls.add(myAttributeCombo);
    controls.add(myValueField);
    controls.add(myAddButton);
    controls.add(myRemoveButton);

    JBScrollPane scroll = new JBScrollPane(myTable);
    scroll.setPreferredSize(JBUI.size(560, 140));
    scroll.setBorder(JBUI.Borders.empty());

    myBody.setBorder(JBUI.Borders.empty());
    myBody.add(controls, BorderLayout.NORTH);
    myBody.add(scroll, BorderLayout.CENTER);

    JPanel header = new JPanel(new BorderLayout());
    header.add(mySeparator, BorderLayout.CENTER);
    mySeparator.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    mySeparator.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseReleased(MouseEvent e) {
        setExpanded(!myExpanded);
      }
    });

    myPanel.add(header, BorderLayout.NORTH);
    myPanel.add(myBody, BorderLayout.CENTER);
    updateExpandedIcon();
    refreshRows();
  }

  public JComponent getComponent() {
    return myPanel;
  }

  /** Restores the editor from the stored tokens (one value per catalogue attribute). */
  public void setTokens(Set<String> tokens) {
    myTokens.clear();
    if (tokens != null) {
      for (String token : tokens) {
        ConnectorProperties.Parsed parsed = ConnectorProperties.Parsed.of(token);
        if (parsed != null) {
          myTokens.add(ConnectorProperties.token(parsed.name(), parsed.value()));
        }
      }
    }
    refreshRows();
  }

  public Set<String> getTokens() {
    return new LinkedHashSet<>(myTokens);
  }

  private void setExpanded(boolean expanded) {
    myExpanded = expanded;
    updateExpandedIcon();
    if (expanded) {
      myBody.setVisible(true);
    } else {
      myPreviousBodySize = myBody.getSize();
      myBody.setVisible(false);
    }
    adjustWindow();
    myPanel.invalidate();
    myPanel.repaint();
  }

  /** Resizes the enclosing window by the collapsed/expanded body height (like {@code HideableDecorator}). */
  private void adjustWindow() {
    Window window = SwingUtilities.getWindowAncestor(myPanel);
    if (window == null) {
      return;
    }
    Dimension bodySize = myPreviousBodySize;
    if (bodySize == null || bodySize.width <= 0 || bodySize.height <= 0) {
      bodySize = myBody.getPreferredSize();
    }
    Dimension windowSize = window.getSize();
    Dimension newSize = myExpanded
        ? new Dimension(Math.max(windowSize.width, myBody.getSize().width),
            windowSize.height + bodySize.height)
        : new Dimension(windowSize.width, windowSize.height - bodySize.height);
    if (!newSize.equals(windowSize)) {
      UIUtil.invokeLaterIfNeeded(() -> window.setSize(newSize));
    }
  }

  private void updateExpandedIcon() {
    Icon icon = myExpanded ? AllIcons.General.ArrowDown : AllIcons.General.ArrowRight;
    mySeparator.getLabel().setIcon(icon);
    mySeparator.getLabel().setIconTextGap(ICON_TEXT_GAP);
  }

  private void prefillFromCombo() {
    ConnectorProperties.Entry entry = selectedEntry();
    if (entry != null) {
      myValueField.setText(entry.suggestedValue());
    }
  }

  private ConnectorProperties.Entry selectedEntry() {
    Object item = myAttributeCombo.getSelectedItem();
    return item == null ? null : ConnectorProperties.byName(item.toString());
  }

  private void addOrUpdate() {
    ConnectorProperties.Entry entry = selectedEntry();
    if (entry == null) {
      return;
    }
    String value = myValueField.getText();
    if (value == null || value.isEmpty()) {
      return;
    }
    myTokens.removeIf(token -> {
      ConnectorProperties.Parsed parsed = ConnectorProperties.Parsed.of(token);
      return parsed != null && parsed.name().equals(entry.name());
    });
    myTokens.add(ConnectorProperties.token(entry.name(), value));
    refreshRows();
  }

  private void removeSelected() {
    int row = myTable.getSelectedRow();
    if (row < 0 || row >= myTableModel.getRowCount()) {
      return;
    }
    String name = (String) myTableModel.getValueAt(row, COL_NAME);
    myTokens.removeIf(token -> {
      ConnectorProperties.Parsed parsed = ConnectorProperties.Parsed.of(token);
      return parsed != null && parsed.name().equals(name);
    });
    refreshRows();
  }

  private void refreshRows() {
    myTableModel.setRowCount(0);
    List<String> orderedNames = ConnectorProperties.names();
    for (String name : orderedNames) {
      for (String token : myTokens) {
        ConnectorProperties.Parsed parsed = ConnectorProperties.Parsed.of(token);
        if (parsed != null && parsed.name().equals(name)) {
          myTableModel.addRow(new Object[]{name, parsed.value()});
          break;
        }
      }
    }
  }

  /** Left-aligned, bold header titles with padding, aligned with the cell content below. */
  private static final class HeaderRenderer extends DefaultTableCellRenderer {

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                   boolean hasFocus, int row, int column) {
      super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
      setHorizontalAlignment(SwingConstants.LEFT);
      setOpaque(true);
      setBackground(table.getTableHeader().getBackground());
      setForeground(table.getTableHeader().getForeground());
      Font headerFont = UIManager.getFont(TABLE_HEADER_FONT_KEY);
      setFont((headerFont == null ? getFont() : headerFont).deriveFont(Font.BOLD));
      setBorder(JBUI.Borders.empty(4, 8, 4, 0));
      return this;
    }
  }
}
