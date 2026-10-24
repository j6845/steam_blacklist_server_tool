package blacklist;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLaf;
import java.awt.Color;
import java.util.Map;
import javax.swing.UIManager;

/** Fixed dark theme (FlatLaf) with an orange accent. */
final class Theme {

    static final Color ACCENT = new Color(0xF0A030);
    static final Color MUTED = new Color(0x9AA0A6);
    static final Color SUCCESS = new Color(0x6CC24A);
    static final Color ERROR = new Color(0xFF6B6B);
    static final Color CARD = new Color(0x2B2D30);

    private Theme() {
    }

    static void setup() {
        FlatLaf.setGlobalExtraDefaults(Map.of("@accentColor", "#F0A030"));
        FlatDarkLaf.setup();
        // Dark text on the orange accent reads better than white.
        UIManager.put("Button.default.foreground", new Color(0x1B1B1B));
        UIManager.put("CheckBox.icon.checkmarkColor", new Color(0x1B1B1B));
        UIManager.put("Table.selectionBackground", new Color(0x4D3A1E));
        UIManager.put("Table.selectionForeground", Color.WHITE);
        UIManager.put("Table.rowHeight", 26);
        UIManager.put("Table.showHorizontalLines", true);
        UIManager.put("Button.arc", 8);
        UIManager.put("Component.arc", 8);
        UIManager.put("ScrollBar.showButtons", false);
        UIManager.put("TitlePane.unifiedBackground", true);
    }
}
