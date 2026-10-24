package blacklist;

import com.formdev.flatlaf.util.SystemFileChooser;
import java.awt.Component;
import java.io.File;

/** Native OS file dialogs (Explorer on Windows) via FlatLaf's SystemFileChooser. */
final class FileDialogs {

    private FileDialogs() {
    }

    /** Returns the chosen file (with .txt extension) or null if cancelled. The dialog asks before overwriting. */
    static File save(Component parent, File dir, String fileName) {
        SystemFileChooser c = chooser("Blacklist speichern", dir);
        c.setSelectedFile(new File(dir, fileName));
        c.putPlatformProperty(SystemFileChooser.WINDOWS_DEFAULT_EXTENSION, "txt");
        if (c.showSaveDialog(parent) != SystemFileChooser.APPROVE_OPTION || c.getSelectedFile() == null) {
            return null;
        }
        File f = c.getSelectedFile();
        return f.getName().toLowerCase().endsWith(".txt") ? f : new File(f.getParentFile(), f.getName() + ".txt");
    }

    /** Returns the chosen file or null if cancelled. */
    static File open(Component parent, File dir) {
        SystemFileChooser c = chooser("Blacklist öffnen", dir);
        if (c.showOpenDialog(parent) != SystemFileChooser.APPROVE_OPTION) {
            return null;
        }
        return c.getSelectedFile();
    }

    private static SystemFileChooser chooser(String title, File dir) {
        SystemFileChooser c = new SystemFileChooser();
        c.setDialogTitle(title);
        c.setCurrentDirectory(dir);
        c.setFileFilter(new SystemFileChooser.FileNameExtensionFilter("Blacklist (*.txt)", "txt"));
        return c;
    }
}
