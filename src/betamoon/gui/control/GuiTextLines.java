package betamoon.gui.control;

import java.util.Arrays;

/** Immutable hard-line boundaries; all positions/columns count UTF-16 units. */
public final class GuiTextLines {
    private final String text;
    private final int[] starts;

    public GuiTextLines(String text) {
        if (text == null || text.indexOf('\r') >= 0) {
            throw new IllegalArgumentException("Text lines require LF-normalized content");
        }
        this.text = text;
        int count = 1;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                count++;
            }
        }
        starts = new int[count];
        int line = 1;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                starts[line++] = i + 1;
            }
        }
    }

    public int count() {
        return starts.length;
    }

    public int start(int line) {
        requireLine(line);
        return starts[line];
    }

    public int end(int line) {
        requireLine(line);
        return line + 1 < starts.length ? starts[line + 1] - 1 : text.length();
    }

    public int lineAt(int position) {
        if (position < 0 || position > text.length()) {
            throw new IllegalArgumentException("Text position is outside the document");
        }
        int found = Arrays.binarySearch(starts, position);
        return found >= 0 ? found : -found - 2;
    }

    public int columnAt(int position) {
        return position - start(lineAt(position));
    }

    public int position(int line, int column) {
        if (column < 0) {
            throw new IllegalArgumentException("Text columns cannot be negative");
        }
        return start(line) + Math.min(column, end(line) - start(line));
    }

    public static String normalize(String text) {
        if (text == null) {
            throw new IllegalArgumentException("Text cannot be null");
        }
        return text.indexOf('\r') < 0 ? text : text.replace("\r\n", "\n").replace('\r', '\n');
    }

    private void requireLine(int line) {
        if (line < 0 || line >= starts.length) {
            throw new IllegalArgumentException("Line index is outside the document");
        }
    }
}
