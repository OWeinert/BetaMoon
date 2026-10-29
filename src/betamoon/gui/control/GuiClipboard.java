package betamoon.gui.control;

import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;

/**
 * Clipboard availability is optional; all editing remains usable when the
 * desktop refuses access.
 */
public final class GuiClipboard {
    public static String read(int maximumLength) {
        try {
            Object value = Toolkit.getDefaultToolkit().getSystemClipboard().getData(DataFlavor.stringFlavor);
            if (value instanceof String) {
                String text = (String) value;
                StringBuilder result = new StringBuilder(Math.min(maximumLength, text.length()));
                for (int i = 0; i < text.length() && result.length() < maximumLength; i++) {
                    char character = text.charAt(i);
                    if (character >= 32 && character != 127) {
                        result.append(character);
                    }
                }
                return result.toString();
            }
        } catch (Exception ignored) {
            // Clipboard ownership, headless sessions and unsupported formats are
            // recoverable.
        }
        return "";
    }

    public static void write(String text) {
        try {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
        } catch (RuntimeException ignored) {
            // Temporary desktop ownership failure must not interrupt input routing.
        }
    }

    private GuiClipboard() {
    }
}
