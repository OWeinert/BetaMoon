package betamoon.gui.control;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.ToIntFunction;

/**
 * Immutable plain-text rows and pixel positions, shared by textarea paint and
 * input.
 */
public final class GuiTextAreaLayout {
    public static final class Row {
        public final int start;
        public final int end;
        public final boolean newline;

        private Row(int start, int end, boolean newline) {
            this.start = start;
            this.end = end;
            this.newline = newline;
        }
    }

    public final String text;
    public final int lineHeight;
    public final int width;
    public final int height;
    public final List<Row> rows;
    private final int[] pixels;
    private final int[] starts;

    public GuiTextAreaLayout(String text, int availableWidth, boolean wrap, int lineHeight,
            ToIntFunction<String> measure) {
        if (text == null || text.length() > 65536 || text.indexOf('\r') >= 0 || lineHeight < 1 || lineHeight > 16384) {
            throw new IllegalArgumentException("Invalid bounded textarea text or font height");
        }
        this.text = text;
        this.lineHeight = lineHeight;
        pixels = new int[text.length() + 1];
        for (int i = 0; i < text.length();) {
            int next = next(i);
            String glyph = text.substring(i, next);
            int advance = text.charAt(i) == '\n' ? 0 : measure.applyAsInt(glyph.equals("\t") ? "    " : glyph);
            if (advance < 0 || (long) pixels[i] + advance > 1000000000) {
                throw new IllegalArgumentException("Textarea font advances exceed bounded geometry");
            }
            for (int unit = i + 1; unit < next; unit++) {
                pixels[unit] = pixels[i];
            }
            pixels[next] = pixels[i] + advance;
            i = next;
        }
        List<Row> rows = new ArrayList<>();
        int start = 0;
        int cursor = 0;
        int lastSpace = -1;
        int widest = 0;
        while (cursor < text.length()) {
            if (text.charAt(cursor) == '\n') {
                rows.add(new Row(start, cursor, true));
                widest = Math.max(widest, pixels[cursor] - pixels[start]);
                start = ++cursor;
                lastSpace = -1;
                continue;
            }
            int next = next(cursor);
            if (wrap && cursor > start && pixels[next] - pixels[start] > Math.max(1, availableWidth)) {
                int end = lastSpace >= start ? lastSpace + 1 : cursor;
                rows.add(new Row(start, end, false));
                widest = Math.max(widest, pixels[end] - pixels[start]);
                start = end;
                cursor = end;
                lastSpace = -1;
                continue;
            }
            if (text.charAt(cursor) == ' ' || text.charAt(cursor) == '\t') {
                lastSpace = cursor;
            }
            cursor = next;
        }
        rows.add(new Row(start, text.length(), false));
        widest = Math.max(widest, pixels[text.length()] - pixels[start]);
        if ((long) rows.size() * lineHeight > 1000000000 || widest >= 1000000000) {
            throw new IllegalArgumentException("Textarea content exceeds bounded geometry");
        }
        this.rows = Collections.unmodifiableList(rows);
        starts = new int[rows.size()];
        for (int i = 0; i < rows.size(); i++) {
            starts[i] = rows.get(i).start;
        }
        width = widest + 1;
        height = rows.size() * lineHeight;
    }

    public int rowAt(int position, boolean upstream) {
        if (position < 0 || position > text.length()) {
            throw new IllegalArgumentException("Caret is outside textarea content");
        }
        int found = Arrays.binarySearch(starts, position);
        int row = found >= 0 ? found : -found - 2;
        return upstream && row > 0 && rows.get(row - 1).end == position ? row - 1 : row;
    }

    public int xAt(int row, int position) {
        Row value = rows.get(row);
        return pixels[Math.max(value.start, Math.min(value.end, position))] - pixels[value.start];
    }

    public int positionAt(int row, int x) {
        Row value = rows.get(Math.max(0, Math.min(rows.size() - 1, row)));
        if (x <= 0) {
            return value.start;
        }
        int low = value.start;
        int high = value.end;
        while (low < high) {
            int middle = low + (high - low + 1) / 2;
            if (pixels[middle] - pixels[value.start] <= x) {
                low = middle;
            } else {
                high = middle - 1;
            }
        }
        if (low > value.start && low < text.length() && Character.isLowSurrogate(text.charAt(low))
                && Character.isHighSurrogate(text.charAt(low - 1))) {
            low--;
        }
        int next = Math.min(value.end, next(low));
        return next > low && x * 2L >= (long) pixels[low] + pixels[next] - pixels[value.start] * 2L ? next : low;
    }

    public int next(int position) {
        return position >= text.length() ? text.length() : position + Character.charCount(text.codePointAt(position));
    }

    public int previous(int position) {
        return position <= 0 ? 0 : text.offsetByCodePoints(position, -1);
    }

    public String display(int start, int end) {
        return text.substring(start, end).replace("\t", "    ");
    }
}
