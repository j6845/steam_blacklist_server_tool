package blacklist;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.swing.table.AbstractTableModel;

/** Blacklist entries keyed by IP: one entry per IP, first one wins. */
final class BlacklistTableModel extends AbstractTableModel {

    private static final String[] COLS = {"Name / Grund (Doppelklick zum Ändern)", "Adresse", "Datum"};
    private final Map<String, BlacklistEntry> byIp = new LinkedHashMap<>();
    private final List<BlacklistEntry> rows = new ArrayList<>();
    private final SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm");

    boolean add(BlacklistEntry e) {
        if (byIp.containsKey(e.ip())) {
            return false;
        }
        byIp.put(e.ip(), e);
        rows.add(e);
        fireTableRowsInserted(rows.size() - 1, rows.size() - 1);
        return true;
    }

    void removeRows(int[] modelRows) {
        int[] sorted = modelRows.clone();
        Arrays.sort(sorted);
        for (int i = sorted.length - 1; i >= 0; i--) {
            byIp.remove(rows.remove(sorted[i]).ip());
        }
        fireTableDataChanged();
    }

    void removeIps(Set<String> ips) {
        rows.removeIf(e -> ips.contains(e.ip()));
        byIp.keySet().removeAll(ips);
        fireTableDataChanged();
    }

    void clear() {
        byIp.clear();
        rows.clear();
        fireTableDataChanged();
    }

    int size() {
        return rows.size();
    }

    List<BlacklistEntry> entries() {
        return List.copyOf(rows);
    }

    @Override
    public int getRowCount() {
        return rows.size();
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
    public Object getValueAt(int row, int col) {
        BlacklistEntry e = rows.get(row);
        return switch (col) {
            case 0 -> e.name();
            case 1 -> e.addr();
            default -> fmt.format(new Date(e.date() * 1000));
        };
    }

    @Override
    public boolean isCellEditable(int row, int col) {
        return col == 0;
    }

    @Override
    public void setValueAt(Object value, int row, int col) {
        BlacklistEntry old = rows.get(row);
        BlacklistEntry updated = new BlacklistEntry(String.valueOf(value), old.date(), old.ip());
        rows.set(row, updated);
        byIp.put(updated.ip(), updated);
        fireTableCellUpdated(row, col);
    }
}
