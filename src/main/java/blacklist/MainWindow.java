package blacklist;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.prefs.Preferences;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.RowFilter;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.TableRowSorter;

/** Wizard: Start -> Steam key -> filters -> review -> save. */
final class MainWindow extends JFrame {

    private static final String API_KEY_URL = "https://steamcommunity.com/dev/apikey";
    private static final int A2S_MAX_PER_IP = 5;
    private static final long CACHE_MS = 10 * 60 * 1000;

    private static final String[] STEPS = {"Start", "Steam-Key", "Filter", "Prüfen", "Speichern"};
    private static final int START = 0;
    private static final int KEY = 1;
    private static final int FILTER = 2;
    private static final int REVIEW = 3;
    private static final int SAVE = 4;

    private final Preferences prefs = Preferences.userNodeForPackage(MainWindow.class);

    // Navigation
    private final CardLayout cards = new CardLayout();
    private final JPanel content = new JPanel(cards);
    private final JLabel[] stepLabels = new JLabel[STEPS.length];
    private final JButton backButton = new JButton("‹ Zurück");
    private final JButton nextButton = new JButton();
    private int step;
    private boolean editOnly;

    // Blacklist being built; scanIps = entries that came from the last review step
    private final BlacklistTableModel blacklist = new BlacklistTableModel();
    private final Set<String> scanIps = new HashSet<>();

    // Step: Steam key
    private final JPasswordField apiKeyField = new JPasswordField(prefs.get("apiKey", ""), 34);
    private final JSpinner appIdSpinner = new JSpinner(new SpinnerNumberModel(prefs.getInt("appId", 240), 1, Integer.MAX_VALUE, 1));
    private final JLabel keyHint = new JLabel(" ");

    // Step: filters
    private final JCheckBox russianBox = new JCheckBox("Russisch- und ukrainischsprachige Server", prefs.getBoolean("cyrillic", true));
    private final JCheckBox noGameBox = new JCheckBox("Server ohne Spielangabe", prefs.getBoolean("noGameOn", true));
    private final JCheckBox noSteamBox = new JCheckBox("NoSteam-Server", prefs.getBoolean("noSteamOn", true));
    private final JCheckBox countryBox = new JCheckBox("Server aus bestimmten Ländern", prefs.getBoolean("countryOn", true));
    private final JTextField countriesField = new JTextField(prefs.get("countries", "RU"), 12);
    private final JCheckBox keywordBox = new JCheckBox("Bestimmte Wörter", prefs.getBoolean("keywordOn", false));
    private final JTextField keywordsField = new JTextField(prefs.get("keywords", ""), 24);
    private final JProgressBar progress = new JProgressBar();
    private final JLabel searchStatus = new JLabel(" ");

    // Step: review
    private final ScanTableModel scan = new ScanTableModel();
    private final JTable scanTable = new JTable(scan);
    private final TableRowSorter<ScanTableModel> scanSorter = new TableRowSorter<>(scan);
    private final JCheckBox showAllBox = new JCheckBox("Auch nicht markierte IPs anzeigen");
    private final JTextField scanSearch = new JTextField(18);
    private final JLabel reviewSummary = new JLabel();

    // Step: save
    private final JTable blacklistTable = new JTable(blacklist);
    private final JLabel saveSummary = new JLabel();
    private final JPanel savedBox = new JPanel(new BorderLayout(12, 6));
    private final JLabel savedLabel = new JLabel();
    private File savedFile;

    // Downloaded data, reused for 10 minutes when only the filters change
    private CountryDb countryDb;
    private List<SteamServerQuery.ServerInfo> lastServers = List.of();
    private Map<String, String> lastGames = Map.of();
    private int lastAppId;
    private long lastLoadTime;
    private int lastTruncated;

    MainWindow() {
        super(title());
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        content.add(buildStartStep(), String.valueOf(START));
        content.add(buildKeyStep(), String.valueOf(KEY));
        content.add(buildFilterStep(), String.valueOf(FILTER));
        content.add(buildReviewStep(), String.valueOf(REVIEW));
        content.add(buildSaveStep(), String.valueOf(SAVE));
        content.setBorder(BorderFactory.createEmptyBorder(8, 28, 8, 28));

        JPanel root = new JPanel(new BorderLayout());
        root.add(buildHeader(), BorderLayout.NORTH);
        root.add(content, BorderLayout.CENTER);
        root.add(buildFooter(), BorderLayout.SOUTH);
        setContentPane(root);

        setSize(1080, 720);
        setMinimumSize(new Dimension(820, 600));
        setLocationRelativeTo(null);
        showStep(START);
    }

    private static String title() {
        String version = MainWindow.class.getPackage().getImplementationVersion();
        return "Steam Server Blacklist Tool" + (version != null ? " " + version : "");
    }

    // ---------------------------------------------------------------- frame parts

    private JComponent buildHeader() {
        JPanel steps = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        steps.setOpaque(false);
        for (int i = 0; i < STEPS.length; i++) {
            if (i > 0) {
                JLabel sep = new JLabel("   ›   ");
                sep.setForeground(Theme.MUTED);
                steps.add(sep);
            }
            stepLabels[i] = new JLabel((i + 1) + "  " + STEPS[i]);
            steps.add(stepLabels[i]);
        }
        JPanel header = new JPanel(new BorderLayout());
        header.add(steps, BorderLayout.WEST);
        header.setBorder(BorderFactory.createEmptyBorder(18, 28, 10, 28));
        return header;
    }

    private JComponent buildFooter() {
        backButton.addActionListener(e -> goBack());
        nextButton.addActionListener(e -> goNext());
        nextButton.putClientProperty("JButton.buttonType", "default");
        nextButton.setFont(nextButton.getFont().deriveFont(Font.BOLD));
        nextButton.setPreferredSize(new Dimension(220, 38));
        backButton.setPreferredSize(new Dimension(120, 38));

        JPanel footer = new JPanel(new BorderLayout());
        footer.add(backButton, BorderLayout.WEST);
        footer.add(nextButton, BorderLayout.EAST);
        footer.setBorder(BorderFactory.createEmptyBorder(10, 28, 18, 28));
        return footer;
    }

    private void showStep(int s) {
        step = s;
        cards.show(content, String.valueOf(s));
        for (int i = 0; i < STEPS.length; i++) {
            JLabel l = stepLabels[i];
            l.setFont(l.getFont().deriveFont(i == s ? Font.BOLD : Font.PLAIN));
            l.setForeground(i == s ? Theme.ACCENT : i < s ? null : Theme.MUTED);
        }
        backButton.setVisible(s != START);
        nextButton.setVisible(s != START);
        nextButton.setEnabled(true);
        backButton.setEnabled(true);
        switch (s) {
            case KEY -> nextButton.setText("Weiter ›");
            case FILTER -> nextButton.setText("Server suchen ›");
            case REVIEW -> nextButton.setText("Übernehmen und weiter ›");
            case SAVE -> {
                nextButton.setText("Datei speichern …");
                updateSaveSummary();
            }
            default -> { }
        }
        getRootPane().setDefaultButton(s == START ? null : nextButton);
    }

    private void goBack() {
        switch (step) {
            case SAVE -> showStep(editOnly ? START : REVIEW);
            case START -> { }
            default -> showStep(step - 1);
        }
    }

    private void goNext() {
        switch (step) {
            case KEY -> {
                if (validateKey()) {
                    savePrefs();
                    showStep(FILTER);
                }
            }
            case FILTER -> startSearch();
            case REVIEW -> {
                applyReview();
                showStep(SAVE);
            }
            case SAVE -> saveFile();
            default -> { }
        }
    }

    // ---------------------------------------------------------------- step 1: start

    private JComponent buildStartStep() {
        JPanel p = page("Willkommen",
                "Dieses Tool erstellt eine Blacklist für den Serverbrowser von Counter-Strike: Source. "
                        + "Damit verschwinden Fake-Server, Spam und russischsprachige Server aus deiner Serverliste.");
        p.add(Box.createVerticalStrut(24));
        p.add(choiceCard("Neue Blacklist erstellen",
                "Server suchen und eine neue server_blacklist.txt anlegen.", () -> {
                    blacklist.clear();
                    scanIps.clear();
                    editOnly = false;
                    showStep(KEY);
                }));
        p.add(Box.createVerticalStrut(12));
        p.add(choiceCard("Vorhandene Blacklist erweitern",
                "Deine bisherige Datei laden und neu gefundene Server dazunehmen.", () -> {
                    if (loadExisting()) {
                        editOnly = false;
                        showStep(KEY);
                    }
                }));
        p.add(Box.createVerticalStrut(12));
        p.add(choiceCard("Vorhandene Blacklist nur bearbeiten",
                "Datei laden, Einträge ansehen, hinzufügen oder löschen. Ohne Serversuche.", () -> {
                    if (loadExisting()) {
                        editOnly = true;
                        showStep(SAVE);
                    }
                }));
        p.add(Box.createVerticalGlue());
        return p;
    }

    private boolean loadExisting() {
        File f = FileDialogs.open(this, lastDir());
        if (f == null) {
            return false;
        }
        rememberDir(f);
        try {
            List<BlacklistEntry> entries = BlacklistFile.read(f.toPath());
            blacklist.clear();
            scanIps.clear();
            entries.forEach(blacklist::add);
            return true;
        } catch (Exception ex) {
            error("Die Datei konnte nicht gelesen werden:\n" + ex.getMessage());
            return false;
        }
    }

    // ---------------------------------------------------------------- step 2: steam key

    private JComponent buildKeyStep() {
        JPanel p = page("Steam Web API Key",
                "Das Tool lädt die aktuelle Serverliste direkt von Steam. Dafür brauchst du einen kostenlosen Schlüssel. "
                        + "Er wird nur auf deinem PC gespeichert.");
        p.add(Box.createVerticalStrut(20));
        p.add(numbered(1, "Key-Seite von Steam öffnen und mit deinem Steam-Konto anmelden."));
        JButton open = new JButton("Key-Seite öffnen");
        open.addActionListener(e -> browse(API_KEY_URL));
        p.add(indent(open));
        p.add(Box.createVerticalStrut(14));
        p.add(numbered(2, "Bei „Domain Name“ irgendetwas eintragen, z.B. localhost, und den Key anfordern."));
        p.add(Box.createVerticalStrut(14));
        p.add(numbered(3, "Den angezeigten Key (32 Zeichen) hier einfügen:"));
        apiKeyField.putClientProperty("JPasswordField.showRevealButton", true);
        apiKeyField.putClientProperty("JTextField.placeholderText", "z.B. 0123456789ABCDEF0123456789ABCDEF");
        apiKeyField.setMaximumSize(new Dimension(420, 34));
        p.add(indent(apiKeyField));
        keyHint.setForeground(Theme.ERROR);
        p.add(indent(keyHint));

        p.add(Box.createVerticalStrut(28));
        appIdSpinner.setEditor(new JSpinner.NumberEditor(appIdSpinner, "#"));
        appIdSpinner.setPreferredSize(new Dimension(100, appIdSpinner.getPreferredSize().height));
        p.add(row(muted("Spiel: Steam-AppID"), appIdSpinner,
                muted("(240 = Counter-Strike: Source, nur für andere Source-Spiele ändern)")));
        p.add(Box.createVerticalGlue());
        return p;
    }

    private boolean validateKey() {
        String key = apiKey();
        if (!key.matches("[0-9A-Fa-f]{32}")) {
            keyHint.setText(key.isEmpty()
                    ? "Bitte zuerst den Key einfügen."
                    : "Das sieht nicht nach einem Steam-Key aus: er besteht aus 32 Zeichen (0-9, A-F).");
            return false;
        }
        keyHint.setText(" ");
        return true;
    }

    private String apiKey() {
        return new String(apiKeyField.getPassword()).trim();
    }

    // ---------------------------------------------------------------- step 3: filters

    private JComponent buildFilterStep() {
        JPanel p = page("Was soll ausgeblendet werden?",
                "Eine Server-IP wird gesperrt, sobald einer der gewählten Punkte zutrifft. "
                        + "Im nächsten Schritt kannst du das Ergebnis noch prüfen.");
        p.add(Box.createVerticalStrut(18));
        p.add(filterRow(russianBox, null,
                "Kyrillische Schrift, eine .ru-Adresse oder der Tag „ru“ bei Servername, Spiel oder Tags."));
        p.add(filterRow(noGameBox, null,
                "In der Spalte „Spiel“ stehen nur Sonderzeichen statt eines Spielnamens, z.B. █ █ █ █."));
        p.add(filterRow(noSteamBox, null,
                "Server für Spieler ohne Steam: „NoSteam“, „no-steam“ oder „non-steam“ bei Servername, Spiel oder Tags."));
        p.add(filterRow(countryBox, row(muted("Ländercodes:"), countriesField, muted("z.B. RU oder RU, BY")),
                "Nach dem Land, in dem die IP-Adresse registriert ist."));
        p.add(filterRow(keywordBox, row(keywordsField, muted("mehrere mit Komma trennen")),
                "Eines dieser Wörter kommt im Servernamen, im Spiel oder in den Tags vor."));
        for (JCheckBox box : new JCheckBox[] {countryBox, keywordBox}) {
            box.addActionListener(e -> updateFilterFields());
        }
        updateFilterFields();

        p.add(Box.createVerticalStrut(18));
        progress.setVisible(false);
        progress.setMaximumSize(new Dimension(Integer.MAX_VALUE, 6));
        progress.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.add(progress);
        p.add(Box.createVerticalStrut(6));
        searchStatus.setForeground(Theme.MUTED);
        searchStatus.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.add(searchStatus);
        p.add(Box.createVerticalGlue());
        return p;
    }

    private void updateFilterFields() {
        countriesField.setEnabled(countryBox.isSelected());
        keywordsField.setEnabled(keywordBox.isSelected());
    }

    private ServerScan.Criteria criteria() {
        Set<String> countries = new LinkedHashSet<>();
        if (countryBox.isSelected()) {
            for (String c : countriesField.getText().split("[\\s,;]+")) {
                if (c.length() == 2) {
                    countries.add(c.toUpperCase(Locale.ROOT));
                }
            }
        }
        List<String> keywords = new ArrayList<>();
        if (keywordBox.isSelected()) {
            for (String k : keywordsField.getText().split(",")) {
                if (!k.isBlank()) {
                    keywords.add(k.trim());
                }
            }
        }
        return new ServerScan.Criteria(countries, russianBox.isSelected(), noGameBox.isSelected(),
                noSteamBox.isSelected(), keywords);
    }

    private void startSearch() {
        ServerScan.Criteria criteria = criteria();
        if (criteria.countries().isEmpty() && !criteria.russian() && !criteria.noGame() && !criteria.noSteam()
                && criteria.keywords().isEmpty()) {
            searchStatus.setForeground(Theme.ERROR);
            searchStatus.setText("Bitte mindestens einen Punkt auswählen.");
            return;
        }
        savePrefs();
        int appId = (Integer) appIdSpinner.getValue();
        boolean cacheValid = !lastServers.isEmpty() && lastAppId == appId
                && System.currentTimeMillis() - lastLoadTime < CACHE_MS;
        if (cacheValid) {
            showScan(ServerScan.analyze(lastServers, countryDb, lastGames, criteria));
            showStep(REVIEW);
            return;
        }

        String key = apiKey();
        setSearching(true);
        new SwingWorker<List<ServerScan.IpGroup>, String>() {
            private SteamServerQuery.Result result;
            private Map<String, String> games = Map.of();

            @Override
            protected List<ServerScan.IpGroup> doInBackground() throws Exception {
                if (countryDb == null) {
                    publish("Lade Länder-Datenbank (nur beim ersten Mal) ...");
                    countryDb = CountryDb.load(s -> { });
                }
                publish("Lade Serverliste von Steam ...");
                result = SteamServerQuery.fetch(key, appId, this::publish);
                // Always loaded, so changing the filters later doesn't need a new search.
                publish("Frage Spielnamen bei den Servern ab ...");
                games = A2sQuery.queryGames(result.servers(), A2S_MAX_PER_IP, this::publish);
                return ServerScan.analyze(result.servers(), countryDb, games, criteria);
            }

            @Override
            protected void process(List<String> chunks) {
                searchStatus.setText(chunks.get(chunks.size() - 1));
            }

            @Override
            protected void done() {
                setSearching(false);
                try {
                    List<ServerScan.IpGroup> groups = get();
                    lastServers = result.servers();
                    lastGames = games;
                    lastAppId = appId;
                    lastLoadTime = System.currentTimeMillis();
                    lastTruncated = result.truncated();
                    searchStatus.setText(" ");
                    showScan(groups);
                    showStep(REVIEW);
                } catch (ExecutionException ex) {
                    searchStatus.setForeground(Theme.ERROR);
                    searchStatus.setText("Suche fehlgeschlagen.");
                    error(ex.getCause().getMessage());
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }
        }.execute();
    }

    private void setSearching(boolean busy) {
        searchStatus.setForeground(Theme.MUTED);
        progress.setIndeterminate(busy);
        progress.setVisible(busy);
        nextButton.setEnabled(!busy);
        backButton.setEnabled(!busy);
        nextButton.setText(busy ? "Suche läuft …" : "Server suchen ›");
    }

    // ---------------------------------------------------------------- step 4: review

    private JComponent buildReviewStep() {
        JPanel p = new JPanel(new BorderLayout(0, 10));
        JPanel top = page("Ergebnis prüfen",
                "Markierte IPs werden gesperrt. Entferne den Haken bei IPs, die du behalten möchtest.");
        reviewSummary.putClientProperty("FlatLaf.styleClass", "h3");
        reviewSummary.setAlignmentX(Component.LEFT_ALIGNMENT);
        top.add(Box.createVerticalStrut(10));
        top.add(reviewSummary);
        top.add(Box.createVerticalStrut(8));

        scanSearch.putClientProperty("JTextField.placeholderText", "Suchen (IP, Name, Land …)");
        scanSearch.putClientProperty("JTextField.showClearButton", true);
        scanSearch.getDocument().addDocumentListener(onChange(this::applyScanFilter));
        showAllBox.addActionListener(e -> applyScanFilter());
        JButton all = new JButton("Alle sichtbaren sperren");
        all.addActionListener(e -> setVisibleSelected(true));
        JButton none = new JButton("Alle sichtbaren freigeben");
        none.addActionListener(e -> setVisibleSelected(false));
        JPanel tools = row(scanSearch, showAllBox, all, none);
        top.add(tools);

        scanTable.setRowSorter(scanSorter);
        scanTable.setFillsViewportHeight(true);
        int[] widths = {60, 115, 45, 55, 55, 170, 300, 150, 180};
        for (int i = 0; i < widths.length; i++) {
            scanTable.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }
        p.add(top, BorderLayout.NORTH);
        p.add(new JScrollPane(scanTable), BorderLayout.CENTER);
        return p;
    }

    private void showScan(List<ServerScan.IpGroup> groups) {
        scan.setGroups(groups);
        applyScanFilter();
        long flagged = groups.stream().filter(ServerScan.IpGroup::flagged).count();
        String text = flagged + " von " + groups.size() + " Server-IPs werden gesperrt ("
                + lastServers.size() + " Server insgesamt).";
        if (lastTruncated > 0) {
            text += "  Achtung: Steam hat nicht alle Server geliefert.";
        }
        reviewSummary.setText(text);
    }

    private void applyScanFilter() {
        boolean showAll = showAllBox.isSelected();
        String q = scanSearch.getText().trim().toLowerCase(Locale.ROOT);
        scanSorter.setRowFilter(new RowFilter<ScanTableModel, Integer>() {
            @Override
            public boolean include(Entry<? extends ScanTableModel, ? extends Integer> entry) {
                ServerScan.IpGroup g = scan.group(entry.getIdentifier());
                if (!showAll && !g.flagged()) {
                    return false;
                }
                return q.isEmpty() || (g.ip() + " " + g.country() + " " + g.sampleName() + " " + g.sampleGame()
                        + " " + g.sampleTags() + " " + g.reason()).toLowerCase(Locale.ROOT).contains(q);
            }
        });
    }

    private void setVisibleSelected(boolean value) {
        for (int i = 0; i < scanTable.getRowCount(); i++) {
            scan.setSelected(scanTable.convertRowIndexToModel(i), value);
        }
    }

    /** Replaces the entries of a previous review with the current selection. */
    private void applyReview() {
        blacklist.removeIps(scanIps);
        scanIps.clear();
        for (ServerScan.IpGroup g : scan.selected()) {
            String name = (g.flagged() ? g.reason() : "Manuell") + " | " + g.ip() + " | " + g.sampleName();
            if (name.length() > 120) {
                name = name.substring(0, 120);
            }
            if (blacklist.add(BlacklistEntry.now(name, g.ip()))) {
                scanIps.add(g.ip());
            }
        }
    }

    // ---------------------------------------------------------------- step 5: save

    private JComponent buildSaveStep() {
        JPanel p = new JPanel(new BorderLayout(0, 10));
        JPanel top = page("Blacklist speichern",
                "Das kommt in die Datei. Namen kannst du per Doppelklick ändern, sie erscheinen später im Spiel.");
        saveSummary.putClientProperty("FlatLaf.styleClass", "h3");
        saveSummary.setAlignmentX(Component.LEFT_ALIGNMENT);
        top.add(Box.createVerticalStrut(10));
        top.add(saveSummary);
        top.add(Box.createVerticalStrut(8));

        JButton add = new JButton("IP hinzufügen …");
        add.addActionListener(e -> addSingleEntry());
        JButton paste = new JButton("IP-Liste einfügen …");
        paste.addActionListener(e -> pasteIps());
        JButton remove = new JButton("Markierte entfernen");
        remove.setEnabled(false);
        remove.addActionListener(e -> removeSelected());
        blacklistTable.getSelectionModel().addListSelectionListener(
                e -> remove.setEnabled(blacklistTable.getSelectedRowCount() > 0));
        top.add(row(add, paste, remove));

        blacklistTable.setAutoCreateRowSorter(true);
        blacklistTable.setFillsViewportHeight(true);
        blacklistTable.getColumnModel().getColumn(0).setPreferredWidth(620);
        blacklistTable.getColumnModel().getColumn(1).setPreferredWidth(150);
        blacklistTable.getColumnModel().getColumn(2).setPreferredWidth(130);

        p.add(top, BorderLayout.NORTH);
        p.add(new JScrollPane(blacklistTable), BorderLayout.CENTER);
        p.add(buildSavedBox(), BorderLayout.SOUTH);
        return p;
    }

    private JComponent buildSavedBox() {
        savedLabel.putClientProperty("FlatLaf.styleClass", "h4");
        savedLabel.setForeground(Theme.SUCCESS);
        JLabel howTo = new JLabel("<html><b>So kommt die Liste ins Spiel:</b><br>"
                + "1. Counter-Strike: Source starten und den Serverbrowser öffnen<br>"
                + "2. Tab <b>Server-Blacklist</b> (englisch: Blacklisted Servers) wählen<br>"
                + "3. Unten auf <b>Import servers from file</b> klicken und die gespeicherte Datei auswählen</html>");
        JButton folder = new JButton("Ordner öffnen");
        folder.addActionListener(e -> {
            try {
                Desktop.getDesktop().open(savedFile.getParentFile());
            } catch (Exception ex) {
                error("Ordner konnte nicht geöffnet werden:\n" + savedFile.getParent());
            }
        });
        JButton restart = new JButton("Von vorn beginnen");
        restart.addActionListener(e -> {
            savedBox.setVisible(false);
            showStep(START);
        });
        JPanel text = new JPanel(new BorderLayout(0, 6));
        text.setOpaque(false);
        text.add(savedLabel, BorderLayout.NORTH);
        text.add(howTo, BorderLayout.CENTER);
        JPanel buttons = new JPanel(new GridLayout(2, 1, 0, 6));
        buttons.setOpaque(false);
        buttons.add(folder);
        buttons.add(restart);
        JPanel east = new JPanel(new BorderLayout());
        east.setOpaque(false);
        east.add(buttons, BorderLayout.NORTH);

        savedBox.add(text, BorderLayout.CENTER);
        savedBox.add(east, BorderLayout.EAST);
        savedBox.setBackground(Theme.CARD);
        savedBox.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 4, 0, 0, Theme.SUCCESS),
                BorderFactory.createEmptyBorder(12, 14, 12, 14)));
        savedBox.setVisible(false);
        return savedBox;
    }

    private void updateSaveSummary() {
        int fromScan = scanIps.size();
        int total = blacklist.size();
        saveSummary.setText(total + " Einträge"
                + (fromScan > 0 ? ", davon " + fromScan + " neu aus der Suche" : "")
                + (total > fromScan && fromScan > 0 ? ", " + (total - fromScan) + " aus deiner Datei oder manuell" : ""));
        nextButton.setEnabled(total > 0);
    }

    private void saveFile() {
        File f = FileDialogs.save(this, lastDir(), savedFile != null ? savedFile.getName() : "server_blacklist.txt");
        if (f == null) {
            return;
        }
        rememberDir(f);
        try {
            BlacklistFile.write(f.toPath(), blacklist.entries());
            savedFile = f;
            savedLabel.setText("✔  " + blacklist.size() + " Einträge gespeichert: " + f.getAbsolutePath());
            savedBox.setVisible(true);
        } catch (Exception ex) {
            error("Speichern fehlgeschlagen:\n" + ex.getMessage());
        }
    }

    private void addSingleEntry() {
        JTextField addr = new JTextField(20);
        addr.putClientProperty("JTextField.placeholderText", "z.B. 1.2.3.4");
        JTextField reason = new JTextField("Manuell", 30);
        JPanel p = form(new String[] {"IP-Adresse", "Name / Grund"}, new JComponent[] {addr, reason});
        if (JOptionPane.showConfirmDialog(this, p, "IP hinzufügen", JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) {
            return;
        }
        String ip = Ip.hostOf(addr.getText());
        if (ip == null) {
            error("Das ist keine gültige IP-Adresse: " + addr.getText());
            return;
        }
        String name = reason.getText().isBlank() ? ip : reason.getText().trim() + " " + ip;
        if (!blacklist.add(BlacklistEntry.now(name, ip))) {
            info(ip + " ist schon in der Liste.");
        }
        updateSaveSummary();
    }

    private void pasteIps() {
        JTextArea area = new JTextArea(14, 40);
        JTextField reason = new JTextField("Manuell", 30);
        JPanel p = new JPanel(new BorderLayout(6, 6));
        p.add(new JLabel("Eine IP pro Zeile. Ports und sonstiger Text werden ignoriert."), BorderLayout.NORTH);
        p.add(new JScrollPane(area), BorderLayout.CENTER);
        p.add(form(new String[] {"Name / Grund"}, new JComponent[] {reason}), BorderLayout.SOUTH);
        if (JOptionPane.showConfirmDialog(this, p, "IP-Liste einfügen", JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) {
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
            } else if (blacklist.add(BlacklistEntry.now(reason.getText().trim() + " " + ip, ip))) {
                added++;
            } else {
                dupes++;
            }
        }
        updateSaveSummary();
        info(added + " hinzugefügt, " + dupes + " waren schon drin, " + invalid + " ungültig.");
    }

    private void removeSelected() {
        int[] modelRows = Arrays.stream(blacklistTable.getSelectedRows())
                .map(blacklistTable::convertRowIndexToModel).toArray();
        Set<String> removed = new HashSet<>();
        for (int r : modelRows) {
            removed.add(blacklist.entries().get(r).ip());
        }
        blacklist.removeRows(modelRows);
        scanIps.removeAll(removed);
        updateSaveSummary();
    }

    // ---------------------------------------------------------------- helpers

    private void savePrefs() {
        prefs.put("apiKey", apiKey());
        prefs.putInt("appId", (Integer) appIdSpinner.getValue());
        prefs.putBoolean("cyrillic", russianBox.isSelected());
        prefs.putBoolean("noGameOn", noGameBox.isSelected());
        prefs.putBoolean("noSteamOn", noSteamBox.isSelected());
        prefs.putBoolean("countryOn", countryBox.isSelected());
        prefs.put("countries", countriesField.getText());
        prefs.putBoolean("keywordOn", keywordBox.isSelected());
        prefs.put("keywords", keywordsField.getText());
    }

    private File lastDir() {
        return new File(prefs.get("lastDir", System.getProperty("user.home")));
    }

    private void rememberDir(File f) {
        if (f.getParentFile() != null) {
            prefs.put("lastDir", f.getParentFile().getAbsolutePath());
        }
    }

    /** Vertical page with headline and intro text. */
    private static JPanel page(String headline, String intro) {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        JLabel h = new JLabel(headline);
        h.putClientProperty("FlatLaf.styleClass", "h1");
        h.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.add(h);
        p.add(Box.createVerticalStrut(6));
        JLabel text = new JLabel("<html><div style='width:640px'>" + intro + "</div></html>");
        text.setForeground(Theme.MUTED);
        text.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.add(text);
        return p;
    }

    private static JComponent choiceCard(String title, String description, Runnable action) {
        JButton b = new JButton("<html><span style='font-size:13pt'><b>" + title + "</b></span><br>"
                + "<span style='color:#9AA0A6'>" + description + "</span></html>");
        b.setHorizontalAlignment(SwingConstants.LEFT);
        b.setBorder(BorderFactory.createEmptyBorder(14, 18, 14, 18));
        b.setBackground(Theme.CARD);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setMaximumSize(new Dimension(640, 80));
        b.setAlignmentX(Component.LEFT_ALIGNMENT);
        b.addActionListener(e -> action.run());
        return b;
    }

    private static JComponent filterRow(JCheckBox box, JComponent options, String description) {
        JPanel p = new JPanel(new GridBagLayout());
        p.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 0;
        c.anchor = GridBagConstraints.WEST;
        box.setFont(box.getFont().deriveFont(Font.BOLD));
        p.add(box, c);
        c.gridy = 1;
        c.insets = new Insets(0, 28, 0, 0);
        p.add(muted(description), c);
        if (options != null) {
            c.gridy = 2;
            c.insets = new Insets(4, 24, 0, 0);
            p.add(options, c);
        }
        c.gridx = 1;
        c.weightx = 1;
        p.add(Box.createHorizontalGlue(), c);
        p.setBorder(BorderFactory.createEmptyBorder(6, 0, 8, 0));
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, p.getPreferredSize().height));
        return p;
    }

    private static JLabel numbered(int n, String text) {
        JLabel l = new JLabel("<html><b style='color:#F0A030'>" + n + ".</b>&nbsp;&nbsp;" + text + "</html>");
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        return l;
    }

    private static JComponent indent(JComponent c) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 4));
        p.setOpaque(false);
        p.setBorder(BorderFactory.createEmptyBorder(0, 22, 0, 0));
        p.add(c);
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, p.getPreferredSize().height));
        return p;
    }

    private static JPanel row(JComponent... items) {
        // No leading gap, so rows line up with the headline.
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        p.setOpaque(false);
        for (int i = 0; i < items.length; i++) {
            if (i > 0) {
                p.add(Box.createHorizontalStrut(8));
            }
            p.add(items[i]);
        }
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, p.getPreferredSize().height));
        return p;
    }

    private static JLabel muted(String text) {
        JLabel l = new JLabel(text);
        l.setForeground(Theme.MUTED);
        return l;
    }

    private static JPanel form(String[] labels, JComponent[] fields) {
        JPanel p = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 4, 4, 4);
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

    private static DocumentListener onChange(Runnable r) {
        return new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                r.run();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                r.run();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                r.run();
            }
        };
    }

    private void browse(String url) {
        try {
            Desktop.getDesktop().browse(URI.create(url));
        } catch (Exception ex) {
            info("Bitte im Browser öffnen:\n" + url);
        }
    }

    private void info(String msg) {
        JOptionPane.showMessageDialog(this, msg, "Hinweis", JOptionPane.INFORMATION_MESSAGE);
    }

    private void error(String msg) {
        JOptionPane.showMessageDialog(this, msg, "Fehler", JOptionPane.ERROR_MESSAGE);
    }
}
