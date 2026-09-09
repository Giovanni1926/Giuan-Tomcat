package org.giuantomcat.runConfiguration.ui;

import com.intellij.ui.HideableTitledPanel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.JBUI;
import org.giuantomcat.runConfiguration.settings.ConnectorProperties;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Editor for the additional {@code <Connector>} attributes written into the generated
 * {@code server.xml}. The whole section is a collapsible accordion; inside, a dropdown lists the
 * fixed {@link ConnectorProperties#CATALOG}, a text field holds the value and the configured
 * attributes are shown in a table (one row per attribute).
 */
public final class ConnectorPropertiesPanel {

  private static final int COL_NAME = 0;
  private static final int COL_VALUE = 1;

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

  private final HideableTitledPanel myPanel;

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

    JPanel body = new JPanel(new BorderLayout(0, 6));
    body.setBorder(JBUI.Borders.empty());
    body.add(controls, BorderLayout.NORTH);
    body.add(scroll, BorderLayout.CENTER);

    myPanel = new HideableTitledPanel("Connector attributes", body, true);
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

    private static final String TABLE_HEADER_FONT_KEY = "TableHeader.font";

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
