package betamoon.gui.widget;

import betamoon.gui.control.GuiTextAreaLayout;
import betamoon.gui.control.GuiTextEditorModel;
import betamoon.gui.framework.GuiContext;
import betamoon.gui.framework.GuiFrameContent;
import betamoon.gui.framework.GuiGeometry.Rect;
import betamoon.gui.framework.GuiRenderer;
import betamoon.gui.style.GuiFontFace;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Immutable displayed textarea geometry; one layout drives paint, selection and
 * pointer positions.
 */
public final class GuiTextAreaFrame implements GuiFrameContent {
    public final GuiTextAreaLayout layout;
    public final Rect viewport;
    public final GuiFontFace font;
    public final int scrollX;
    public final int scrollY;
    private final int caret;
    private final int selectionStart;
    private final int selectionEnd;
    private final boolean upstream;

    public GuiTextAreaFrame(GuiTextAreaLayout layout, Rect viewport, GuiFontFace font, int x, int y,
            GuiTextEditorModel editor, boolean upstream) {
        this.layout = layout;
        this.viewport = viewport;
        this.font = font;
        scrollX = x;
        scrollY = y;
        caret = editor.caret();
        selectionStart = editor.selectionStart();
        selectionEnd = editor.selectionEnd();
        this.upstream = upstream;
    }

    public boolean contains(double x, double y) {
        return true;
    }

    public int rowAt(int y) {
        return Math.max(0,
                Math.min(layout.rows.size() - 1, Math.floorDiv(y - viewport.getTop() + scrollY, layout.lineHeight)));
    }

    public int positionAt(int x, int y) {
        return layout.positionAt(rowAt(y), x - viewport.getLeft() + scrollX);
    }

    public Rect caretBounds() {
        int row = layout.rowAt(caret, upstream);
        return Rect.fromPositionAndSize(viewport.getLeft() + layout.xAt(row, caret) - scrollX,
                viewport.getTop() + row * layout.lineHeight - scrollY, 1, layout.lineHeight);
    }

    public Rect visibleCaretBounds() {
        Rect caret = caretBounds();
        int left = Math.max(viewport.getLeft(), Math.min(viewport.getRight(), caret.getLeft()));
        int top = Math.max(viewport.getTop(), Math.min(viewport.getBottom(), caret.getTop()));
        return Rect.fromPositionAndSize(left, top,
                caret.getLeft() >= viewport.getLeft() && caret.getRight() <= viewport.getRight() ? 1 : 0,
                Math.max(0, Math.min(viewport.getBottom(), caret.getBottom()) - top));
    }

    private int firstRow() {
        return Math.max(0, Math.min(layout.rows.size(), scrollY / layout.lineHeight));
    }

    private int lastRow() {
        return Math.max(firstRow(), Math.min(layout.rows.size(),
                (int) (((long) scrollY + viewport.getHeight() + layout.lineHeight - 1) / layout.lineHeight)));
    }

    private Rect selection(int row) {
        GuiTextAreaLayout.Row line = layout.rows.get(row);
        int first = Math.max(line.start, Math.min(line.end, selectionStart));
        int last = Math.max(first, Math.min(line.end, selectionEnd));
        int left = layout.xAt(row, first);
        int right = layout.xAt(row, last);
        if (line.newline && selectionStart <= line.end && selectionEnd > line.end) {
            right += 4;
        }
        return Rect.fromPositionAndSize(viewport.getLeft() + left - scrollX,
                viewport.getTop() + row * layout.lineHeight - scrollY, right - left, layout.lineHeight);
    }

    public void paint(GuiContext context, int foreground, int selectedColor, boolean shadow, boolean caretVisible) {
        if (viewport.isEmpty()) {
            return;
        }
        GuiRenderer renderer = context.getRenderer();
        renderer.pushClip(viewport);
        try {
            for (int row = firstRow(); row < lastRow(); row++) {
                Rect selection = selection(row);
                if (!selection.isEmpty()) {
                    renderer.drawRect(selection, selectedColor);
                }
                GuiTextAreaLayout.Row line = layout.rows.get(row);
                int start = Math.max(line.start, layout.previous(layout.positionAt(row, scrollX)));
                int end = Math.min(line.end, layout.next(layout.positionAt(row, scrollX + viewport.getWidth())));
                int baseline = viewport.getTop() + row * layout.lineHeight - scrollY;
                textRuns(row, start, end, (text, offset) -> font.draw(context, text,
                        viewport.getLeft() + offset - scrollX, baseline, foreground, shadow));
            }
            if (caretVisible) {
                renderer.drawRect(visibleCaretBounds(), foreground);
            }
        } finally {
            renderer.popClip();
        }
    }

    public Map<String, Object> metrics(Rect root) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("viewport", rectangle(viewport, root));
        result.put("caret", rectangle(visibleCaretBounds(), root));
        result.put("scrollX", scrollX);
        result.put("scrollY", scrollY);
        List<Map<String, Object>> lines = new ArrayList<>();
        List<Map<String, Object>> selection = new ArrayList<>();
        for (int row = firstRow(); row < lastRow(); row++) {
            GuiTextAreaLayout.Row line = layout.rows.get(row);
            int start = Math.max(line.start, layout.previous(layout.positionAt(row, scrollX)));
            int end = Math.min(line.end, layout.next(layout.positionAt(row, scrollX + viewport.getWidth())));
            int baseline = viewport.getTop() + row * layout.lineHeight - scrollY - root.getTop();
            textRuns(row, start, end, (text, offset) -> {
                Map<String, Object> value = new LinkedHashMap<>();
                value.put("text", text);
                value.put("x", viewport.getLeft() + offset - scrollX - root.getLeft());
                value.put("y", baseline);
                lines.add(Collections.unmodifiableMap(value));
            });
            Rect selected = selection(row).intersect(viewport);
            if (!selected.isEmpty()) {
                selection.add(rectangle(selected, root));
            }
        }
        result.put("lines", Collections.unmodifiableList(lines));
        result.put("selection", Collections.unmodifiableList(selection));
        return Collections.unmodifiableMap(result);
    }

    private void textRuns(int row, int start, int end, BiConsumer<String, Integer> draw) {
        int cursor = start;
        while (cursor < end) {
            int marker = layout.text.indexOf('\u00a7', cursor);
            int stop = marker < 0 || marker >= end ? end : marker;
            if (stop > cursor) {
                draw.accept(layout.display(cursor, stop), layout.xAt(row, cursor));
            }
            if (stop < end && layout.xAt(row, stop + 1) > layout.xAt(row, stop)) {
                draw.accept("\u00a7", layout.xAt(row, stop));
            }
            cursor = stop == end ? end : stop + 1;
        }
    }

    private static Map<String, Object> rectangle(Rect rect, Rect root) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("x", rect.getLeft() - root.getLeft());
        value.put("y", rect.getTop() - root.getTop());
        value.put("width", rect.getWidth());
        value.put("height", rect.getHeight());
        return Collections.unmodifiableMap(value);
    }
}
