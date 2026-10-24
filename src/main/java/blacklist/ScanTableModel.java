package blacklist;

import java.util.ArrayList;
import java.util.List;
import javax.swing.table.AbstractTableModel;

/** Scan result per IP with a checkbox column; flagged IPs start checked. */
final class ScanTableModel extends AbstractTableModel {

    private static final String[] COLS =
            {"Sperren", "IP", "Land", "Server", "Spieler", "Grund", "Servername", "Spiel"};
    private List<ServerScan.IpGroup> groups = List.of();
    private boolean[] selected = new boolean[0];

    void setGroups(List<ServerScan.IpGroup> groups) {
        this.groups = groups;
        this.selected = new boolean[groups.size()];
        for (int i = 0; i < groups.size(); i++) {
            selected[i] = groups.get(i).flagged();
        }
        fireTableDataChanged();
    }

    ServerScan.IpGroup group(int row) {
        return groups.get(row);
    }

    void setSelected(int row, boolean value) {
        selected[row] = value;
        fireTableCellUpdated(row, 0);
    }

    List<ServerScan.IpGroup> selected() {
        List<ServerScan.IpGroup> result = new ArrayList<>();
        for (int i = 0; i < groups.size(); i++) {
            if (selected[i]) {
                result.add(groups.get(i));
            }
        }
        return result;
    }

    @Override
    public int getRowCount() {
        return groups.size();
    }

    @Override
    public int getColumnCount() {
        return COLS.length;
    }

    @Override
    public String getColumnName(int col) {
        return COLS[col];
    }

    @Override
    public Class<?> getColumnClass(int col) {
        return switch (col) {
            case 0 -> Boolean.class;
            case 3, 4 -> Integer.class;
            default -> String.class;
        };
    }

    @Override
    public Object getValueAt(int row, int col) {
        ServerScan.IpGroup g = groups.get(row);
        return switch (col) {
            case 0 -> selected[row];
            case 1 -> g.ip();
            case 2 -> g.country();
            case 3 -> g.serverCount();
            case 4 -> g.players();
            case 5 -> g.reason();
            case 6 -> g.sampleName();
            default -> g.sampleGame();
        };
    }

    @Override
    public boolean isCellEditable(int row, int col) {
        return col == 0;
    }

    @Override
    public void setValueAt(Object value, int row, int col) {
        setSelected(row, Boolean.TRUE.equals(value));
    }
}
