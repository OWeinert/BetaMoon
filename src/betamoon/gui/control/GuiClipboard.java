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
        return read(maximumLength, false);
    }

    public static String read(int maximumLength, boolean multiline) {
        try {
            Object value = Toolkit.getDefaultToolkit().getSystemClipboard().getData(DataFlavor.stringFlavor);
            if (value instanceof String) {
                return filter((String) value, maximumLength, multiline);
            }
        } catch (Exception ignored) {
            // Clipboard ownership, headless sessions and unsupported formats are
            // recoverable.
        }
        return "";
    }

    public static String filter(String text, int maximumLength, boolean multiline) {
        if (text == null || maximumLength < 0) {
            throw new IllegalArgumentException("Clipboard text and a nonnegative limit are required");
        }
        StringBuilder result = new StringBuilder(Math.min(maximumLength, text.length()));
        for (int i = 0; i < text.length() && result.length() < maximumLength; i++) {
            char character = text.charAt(i);
            if (multiline && character == '\r') {
                if (i + 1 < text.length() && text.charAt(i + 1) == '\n') {
                    i++;
                }
                result.append('\n');
            } else if (character >= 32 && character != 127 || multiline && (character == '\n' || character == '\t')) {
                result.append(character);
            }
        }
        return result.toString();
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
