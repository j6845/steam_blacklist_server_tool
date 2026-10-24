package blacklist;

import java.awt.BorderLayout;
import java.awt.Desktop;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.File;
import java.net.URI;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.prefs.Preferences;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.RowFilter;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingWorker;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableRowSorter;

final class MainWindow extends JFrame {

    private static final String API_KEY_URL = "https://steamcommunity.com/dev/apikey";
    private static final int A2S_MAX_PER_IP = 5;

    private final Preferences prefs = Preferences.userNodeForPackage(MainWindow.class);

    // Blacklist tab
    private final BlacklistModel blacklist = new BlacklistModel();
    private final JTable blacklistTable = new JTable(blacklist);
    private final JLabel blacklistStatus = new JLabel();

    // Search tab
    private final JPasswordField apiKeyField = new JPasswordField(prefs.get("apiKey", ""), 32);
    private final JSpinner appIdSpinner = new JSpinner(new SpinnerNumberModel(prefs.getInt("appId", 240), 1, Integer.MAX_VALUE, 1));
    private final JTextField countriesField = new JTextField(prefs.get("countries", "RU"), 20);
    private final JCheckBox cyrillicBox =
            new JCheckBox("+ kyrillische Schrift in Server-/Spielname", prefs.getBoolean("cyrillic", true));
    private final JSpinner minServersSpinner = new JSpinner(new SpinnerNumberModel(prefs.getInt("minServers", 30), 0, 100000, 1));
    private final JSpinner maxPlayersSpinner = new JSpinner(new SpinnerNumberModel(prefs.getInt("maxPlayers", 66), 0, 1000, 1));
    private final JTextField keywordsField = new JTextField(prefs.get("keywords", ""), 20);
    private final JCheckBox onlyFlagged = new JCheckBox("Nur markierte anzeigen", true);
    private final ScanModel scan = new ScanModel();
    private final JTable scanTable = new JTable(scan);
    private final TableRowSorter<ScanModel> scanSorter = new TableRowSorter<>(scan);
    private final JLabel scanStatus = new JLabel("Noch keine Server geladen.");
    private final JProgressBar progress = new JProgressBar();
    private final JButton loadButton = new JButton("Server laden");
    private final JButton reevaluateButton = new JButton("Neu bewerten");

    private CountryDb countryDb;
    private List<SteamServerQuery.ServerInfo> lastServers = List.of();
    private Map<String, String> lastGames = Map.of();
    private int lastTruncated;

    MainWindow() {
        super(title());
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Blacklist", buildBlacklistTab());
        tabs.addTab("Server suchen", buildSearchTab());
        setContentPane(tabs);
        setSize(1100, 700);
        setLocationRelativeTo(null);
        updateBlacklistStatus();
    }

    private static String title() {
        String version = MainWindow.class.getPackage().getImplementationVersion();
        return "Steam Server Blacklist Tool" + (version != null ? " " + version : "");
    }

    // ---------------------------------------------------------------- Blacklist tab

    private JComponent buildBlacklistTab() {
        blacklistTable.setAutoCreateRowSorter(true);
        blacklistTable.getColumnModel().getColumn(0).setPreferredWidth(500);
        blacklistTable.getColumnModel().getColumn(1).setPreferredWidth(140);
        blacklistTable.getColumnModel().getColumn(2).setPreferredWidth(160);

        JButton add = new JButton("Eintrag hinzufügen...");
        add.addActionListener(e -> addSingleEntry());
        JButton paste = new JButton("IPs einfügen...");
        paste.addActionListener(e -> pasteIps());
        JButton importFile = new JButton("Datei importieren...");
        importFile.addActionListener(e -> importFile());
        JButton remove = new JButton("Entfernen");
        remove.addActionListener(e -> removeSelected());
        JButton clear = new JButton("Alle löschen");
        clear.addActionListener(e -> {
            if (blacklist.size() > 0 && confirm("Alle " + blacklist.size() + " Einträge löschen?")) {
                blacklist.clear();
                updateBlacklistStatus();
            }
        });
        JButton save = new JButton("Speichern als...");
        save.addActionListener(e -> saveFile());

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        for (JButton b : new JButton[] {add, paste, importFile, remove, clear}) {
            buttons.add(b);
        }
        JPanel bottom = new JPanel(new BorderLayout());
        bottom.add(blacklistStatus, BorderLayout.WEST);
        bottom.add(save, BorderLayout.EAST);
        bottom.setBorder(BorderFactory.createEmptyBorder(4, 8, 8, 8));

        JPanel panel = new JPanel(new BorderLayout());
        panel.add(buttons, BorderLayout.NORTH);
        panel.add(new JScrollPane(blacklistTable), BorderLayout.CENTER);
        panel.add(bottom, BorderLayout.SOUTH);
        return panel;
    }

    private void addSingleEntry() {
        JTextField addr = new JTextField(20);
        JTextField reason = new JTextField("Manuell", 30);
        JPanel p = form(new String[] {"IP (Port wird immer 0)", "Grund / Name"}, new JComponent[] {addr, reason});
        if (JOptionPane.showConfirmDialog(this, p, "Eintrag hinzufügen", JOptionPane.OK_CANCEL_OPTION)
                != JOptionPane.OK_OPTION) {
            return;
        }
        String ip = Ip.hostOf(addr.getText());
        if (ip == null) {
            error("Ungültige IP: " + addr.getText());
            return;
        }
        String name = reason.getText().isBlank() ? ip : reason.getText() + " " + ip;
        if (!blacklist.add(BlacklistEntry.now(name, ip))) {
            info(ip + " ist bereits in der Blacklist.");
        }
        updateBlacklistStatus();
    }

    private void pasteIps() {
        JTextArea area = new JTextArea(15, 40);
        JTextField reason = new JTextField("Manuell", 30);
        JPanel p = new JPanel(new BorderLayout(4, 4));
        p.add(new JLabel("Eine IP pro Zeile (IP oder IP:Port, beliebiger Text wird ignoriert):"), BorderLayout.NORTH);
        p.add(new JScrollPane(area), BorderLayout.CENTER);
        p.add(form(new String[] {"Grund / Name"}, new JComponent[] {reason}), BorderLayout.SOUTH);
        if (JOptionPane.showConfirmDialog(this, p, "IPs einfügen", JOptionPane.OK_CANCEL_OPTION)
                != JOptionPane.OK_OPTION) {
            return;
        }
        int added = 0;
        int dupes = 0;
        int invalid = 0;
        for (String token : area.getText().split("[\\s,;]+")) {
            if (token.isBlank()) {
                continue;
            }
            String ip = Ip.hostOf(token);
            if (ip == null) {
                invalid++;
                continue;
            }
            if (blacklist.add(BlacklistEntry.now(reason.getText() + " " + ip, ip))) {
                added++;
            } else {
                dupes++;
            }
        }
        updateBlacklistStatus();
        info(added + " hinzugefügt, " + dupes + " Duplikate, " + invalid + " ungültig.");
    }

    private void importFile() {
        JFileChooser fc = chooser();
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        rememberDir(fc.getSelectedFile());
        try {
            List<BlacklistEntry> entries = BlacklistFile.read(fc.getSelectedFile().toPath());
            int added = 0;
            for (BlacklistEntry e : entries) {
                if (blacklist.add(e)) {
                    added++;
                }
            }
            updateBlacklistStatus();
            info(entries.size() + " Einträge gelesen, " + added + " neu hinzugefügt.");
        } catch (Exception ex) {
            error("Import fehlgeschlagen: " + ex.getMessage());
        }
    }

    private void removeSelected() {
        int[] rows = blacklistTable.getSelectedRows();
        int[] modelRows = Arrays.stream(rows).map(blacklistTable::convertRowIndexToModel).toArray();
        blacklist.removeRows(modelRows);
        updateBlacklistStatus();
    }

    private void saveFile() {
        if (blacklist.size() == 0) {
            info("Blacklist ist leer.");
            return;
        }
        JFileChooser fc = chooser();
        fc.setSelectedFile(new File(fc.getCurrentDirectory(), "server_blacklist.txt"));
        if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File f = fc.getSelectedFile();
        if (!f.getName().toLowerCase(Locale.ROOT).endsWith(".txt")) {
            f = new File(f.getParentFile(), f.getName() + ".txt");
        }
        if (f.exists() && !confirm(f.getName() + " überschreiben?")) {
            return;
        }
        rememberDir(f);
        try {
            BlacklistFile.write(f.toPath(), blacklist.entries());
            info(blacklist.size() + " Einträge gespeichert:\n" + f
                    + "\n\nIm Spiel: Serverbrowser > Blacklisted Servers > Import servers from file");
        } catch (Exception ex) {
            error("Speichern fehlgeschlagen: " + ex.getMessage());
        }
    }

    private void updateBlacklistStatus() {
        blacklistStatus.setText(blacklist.size() + " Einträge");
    }

    // ---------------------------------------------------------------- Search tab

    private JComponent buildSearchTab() {
        JLabel keyLink = new JLabel("<html><a href=''>Key holen</a></html>");
        keyLink.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        keyLink.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                try {
                    Desktop.getDesktop().browse(URI.create(API_KEY_URL));
                } catch (Exception ex) {
                    info(API_KEY_URL);
                }
            }
        });
        JPanel keyRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        keyRow.add(apiKeyField);
        keyRow.add(keyLink);

        JPanel countryRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        countryRow.add(countriesField);
        countryRow.add(cyrillicBox);

        JPanel criteria = form(
                new String[] {"Steam Web API Key", "AppID (240 = CS:S)", "Länder (ISO, kommagetrennt)",
                    "Ab N Servern pro IP (0 = aus)", "max_players über (0 = aus)", "Name enthält (kommagetrennt)"},
                new JComponent[] {keyRow, appIdSpinner, countryRow, minServersSpinner, maxPlayersSpinner, keywordsField});
        criteria.setBorder(BorderFactory.createTitledBorder("Kriterien (eines reicht zum Markieren)"));

        loadButton.addActionListener(e -> loadServers());
        reevaluateButton.addActionListener(e -> reevaluate());
        reevaluateButton.setEnabled(false);
        JButton addSelected = new JButton("Ausgewählte zur Blacklist");
        addSelected.addActionListener(e -> addScanSelection());
        JButton selectAll = new JButton("Alle sichtbaren an");
        selectAll.addActionListener(e -> setVisibleSelected(true));
        JButton selectNone = new JButton("Alle sichtbaren aus");
        selectNone.addActionListener(e -> setVisibleSelected(false));
        onlyFlagged.addActionListener(e -> applyScanFilter());

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT));
        actions.add(loadButton);
        actions.add(reevaluateButton);
        actions.add(onlyFlagged);
        actions.add(selectAll);
        actions.add(selectNone);
        actions.add(addSelected);

        JPanel top = new JPanel(new BorderLayout());
        top.add(criteria, BorderLayout.CENTER);
        top.add(actions, BorderLayout.SOUTH);

        scanTable.setRowSorter(scanSorter);
        int[] widths = {50, 120, 50, 60, 80, 60, 300, 150, 220};
        for (int i = 0; i < widths.length; i++) {
            scanTable.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }

        JPanel bottom = new JPanel(new BorderLayout(8, 0));
        bottom.add(scanStatus, BorderLayout.CENTER);
        progress.setVisible(false);
        bottom.add(progress, BorderLayout.EAST);
        bottom.setBorder(BorderFactory.createEmptyBorder(4, 8, 8, 8));

        JPanel panel = new JPanel(new BorderLayout());
        panel.add(top, BorderLayout.NORTH);
        panel.add(new JScrollPane(scanTable), BorderLayout.CENTER);
        panel.add(bottom, BorderLayout.SOUTH);
        return panel;
    }

    private void loadServers() {
        String key = new String(apiKeyField.getPassword()).trim();
        if (key.isEmpty()) {
            error("Steam Web API Key fehlt. Kostenlos erhältlich unter:\n" + API_KEY_URL);
            return;
        }
        int appId = (Integer) appIdSpinner.getValue();
        ServerScan.Criteria criteria = criteria();
        savePrefs(key);
        setBusy(true);

        new SwingWorker<List<ServerScan.IpGroup>, String>() {
            private SteamServerQuery.Result result;
            private Map<String, String> games = Map.of();

            @Override
            protected List<ServerScan.IpGroup> doInBackground() throws Exception {
                if (countryDb == null) {
                    countryDb = CountryDb.load(this::publish);
                }
                result = SteamServerQuery.fetch(key, appId, this::publish);
                if (criteria.cyrillic()) {
                    // The game description is only needed where the server name isn't Cyrillic already.
                    games = A2sQuery.queryGames(ServerScan.withoutCyrillicName(result.servers()),
                            A2S_MAX_PER_IP, this::publish);
                }
                return ServerScan.analyze(result.servers(), countryDb, games, criteria);
            }

            @Override
            protected void process(List<String> chunks) {
                scanStatus.setText(chunks.get(chunks.size() - 1));
            }

            @Override
            protected void done() {
                setBusy(false);
                try {
                    List<ServerScan.IpGroup> groups = get();
                    lastServers = result.servers();
                    lastGames = games;
                    lastTruncated = result.truncated();
                    showScan(groups);
                } catch (ExecutionException ex) {
                    scanStatus.setText("Fehler.");
                    error(ex.getCause().getMessage());
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }
        }.execute();
    }

    private void reevaluate() {
        savePrefs(new String(apiKeyField.getPassword()).trim());
        showScan(ServerScan.analyze(lastServers, countryDb, lastGames, criteria()));
    }

    private void showScan(List<ServerScan.IpGroup> groups) {
        scan.setGroups(groups);
        applyScanFilter();
        reevaluateButton.setEnabled(!lastServers.isEmpty());
        long flagged = groups.stream().filter(ServerScan.IpGroup::flagged).count();
        String status = lastServers.size() + " Server auf " + groups.size() + " IPs, " + flagged + " IPs markiert.";
        if (cyrillicBox.isSelected() && lastGames.isEmpty()) {
            status += " Spielnamen nicht geladen: für Kyrillisch im Spielnamen 'Server laden'.";
        }
        if (lastTruncated > 0) {
            status += " Achtung: " + lastTruncated + " Teillisten unvollständig (Steam-Limit).";
        }
        scanStatus.setText(status);
    }

    private void addScanSelection() {
        int added = 0;
        int dupes = 0;
        for (ServerScan.IpGroup g : scan.selected()) {
            String name = (g.flagged() ? g.reason() : "Manuell") + " | " + g.ip() + " | " + g.sampleName();
            if (name.length() > 120) {
                name = name.substring(0, 120);
            }
            if (blacklist.add(BlacklistEntry.now(name, g.ip()))) {
                added++;
            } else {
                dupes++;
            }
        }
        updateBlacklistStatus();
        info(added + " IPs zur Blacklist hinzugefügt, " + dupes + " waren schon drin.\n"
                + "Speichern im Tab 'Blacklist'.");
    }

    private void setVisibleSelected(boolean value) {
        for (int i = 0; i < scanTable.getRowCount(); i++) {
            scan.setSelected(scanTable.convertRowIndexToModel(i), value);
        }
    }

    private void applyScanFilter() {
        if (onlyFlagged.isSelected()) {
            scanSorter.setRowFilter(new RowFilter<ScanModel, Integer>() {
                @Override
                public boolean include(Entry<? extends ScanModel, ? extends Integer> entry) {
                    return scan.group(entry.getIdentifier()).flagged();
                }
            });
        } else {
            scanSorter.setRowFilter(null);
        }
    }

    private ServerScan.Criteria criteria() {
        Set<String> countries = new LinkedHashSet<>();
        for (String c : countriesField.getText().split("[\\s,;]+")) {
            if (c.length() == 2) {
                countries.add(c.toUpperCase(Locale.ROOT));
            }
        }
        List<String> keywords = new ArrayList<>();
        for (String k : keywordsField.getText().split(",")) {
            if (!k.isBlank()) {
                keywords.add(k.trim());
            }
        }
        return new ServerScan.Criteria(countries, cyrillicBox.isSelected(), (Integer) minServersSpinner.getValue(),
                (Integer) maxPlayersSpinner.getValue(), keywords);
    }

    private void savePrefs(String key) {
        prefs.put("apiKey", key);
        prefs.putInt("appId", (Integer) appIdSpinner.getValue());
        prefs.put("countries", countriesField.getText());
        prefs.putBoolean("cyrillic", cyrillicBox.isSelected());
        prefs.putInt("minServers", (Integer) minServersSpinner.getValue());
        prefs.putInt("maxPlayers", (Integer) maxPlayersSpinner.getValue());
        prefs.put("keywords", keywordsField.getText());
    }

    private void setBusy(boolean busy) {
        loadButton.setEnabled(!busy);
        reevaluateButton.setEnabled(!busy && !lastServers.isEmpty());
        progress.setIndeterminate(busy);
        progress.setVisible(busy);
    }

    // ---------------------------------------------------------------- helpers

    private JFileChooser chooser() {
        JFileChooser fc = new JFileChooser(prefs.get("lastDir", System.getProperty("user.home")));
        fc.setFileFilter(new FileNameExtensionFilter("Blacklist (*.txt)", "txt"));
        return fc;
    }

    private void rememberDir(File f) {
        if (f.getParentFile() != null) {
            prefs.put("lastDir", f.getParentFile().getAbsolutePath());
        }
    }

    private static JPanel form(String[] labels, JComponent[] fields) {
        JPanel p = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(3, 6, 3, 6);
        c.anchor = GridBagConstraints.WEST;
        for (int i = 0; i < labels.length; i++) {
            c.gridx = 0;
            c.gridy = i;
            c.weightx = 0;
            c.fill = GridBagConstraints.NONE;
            p.add(new JLabel(labels[i]), c);
            c.gridx = 1;
            c.weightx = 1;
            c.fill = GridBagConstraints.HORIZONTAL;
            p.add(fields[i], c);
        }
        return p;
    }

    private void info(String msg) {
        JOptionPane.showMessageDialog(this, msg, "Info", JOptionPane.INFORMATION_MESSAGE);
    }

    private void error(String msg) {
        JOptionPane.showMessageDialog(this, msg, "Fehler", JOptionPane.ERROR_MESSAGE);
    }

    private boolean confirm(String msg) {
        return JOptionPane.showConfirmDialog(this, msg, "Bestätigen", JOptionPane.YES_NO_OPTION)
                == JOptionPane.YES_OPTION;
    }

    // ---------------------------------------------------------------- table models

    /** Blacklist entries keyed by IP: one entry per IP, first one wins. */
    private static final class BlacklistModel extends AbstractTableModel {
        private static final String[] COLS = {"Name / Grund", "Datum", "Adresse"};
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
                case 1 -> fmt.format(new Date(e.date() * 1000));
                default -> e.addr();
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

    /** Scan result per IP with a checkbox column; flagged IPs start checked. */
    private static final class ScanModel extends AbstractTableModel {
        private static final String[] COLS =
                {"Auswahl", "IP", "Land", "Server", "max_players", "Spieler", "Beispielname", "Spiel", "Grund"};
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
                case 3, 4, 5 -> Integer.class;
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
                case 4 -> g.maxPlayers();
                case 5 -> g.players();
                case 6 -> g.sampleName();
                case 7 -> g.sampleGame();
                default -> g.reason();
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
}
