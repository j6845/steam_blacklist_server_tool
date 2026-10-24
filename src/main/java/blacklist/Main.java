package blacklist;

import javax.swing.SwingUtilities;

public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            Theme.setup();
            new MainWindow().setVisible(true);
        });
    }
}
