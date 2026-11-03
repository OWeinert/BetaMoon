package betamoon.gui.style;

import betamoon.gui.control.GuiTextEditorModel;
import betamoon.gui.framework.GuiContext;
import betamoon.gui.framework.GuiGeometry.Rect;
import betamoon.gui.framework.GuiRenderer;
import betamoon.gui.widget.GuiStyledView;
import betamoon.gui.widget.GuiRadioView;
import betamoon.gui.widget.GuiTextAreaFrame;
import betamoon.gui.control.GuiSelectionOptions;
import betamoon.gui.control.GuiStatusGeometry;
import betamoon.gui.graphics.GraphicsClip;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.Map;

/**
 * Default Java painter. Input and editing remain in the view when this template
 * is replaced.
 */
public final class GuiDefaultTemplate implements GuiVisualTemplate {
    public static final GuiDefaultTemplate INSTANCE = new GuiDefaultTemplate();
    private static final Set<String> PARTS = Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList("content",
            "focus", "track", "thumb", "caret", "selection", "decrement", "increment", "options")));

    @Override
    public Set<String> parts() {
        return PARTS;
    }

    @Override
    public void paint(GuiContext context, GuiStyledView view, GuiControlStyle style) {
        GuiRenderer renderer = context.getRenderer();
        Rect bounds = view.getBounds();
        String kind = view.kind();
        int foreground = color(context, style, "foreground", 0xFFE0E0E0);
        boolean surface = view.isFocusable() && !"radio".equals(kind) || "panel".equals(kind) || "dialog".equals(kind)
                || "tooltip".equals(kind);
        boolean nativeButton = style.flag("nativeButton", false) && ("button".equals(kind) || "choice".equals(kind));
        if (nativeButton) {
            GuiNativeButtonSkin.draw(view.preparedNativeTexture(), bounds, view.isEnabled(),
                    view.interaction("hovered"), (int) Math.round(context.getOpacity() * 255));
            foreground = color(context, style, "foreground", 0xFFE0E0E0);
        } else if (surface) {
            if (style.drawable("backgroundDrawable") == null) {
                renderer.drawRect(bounds, color(context, style, "background", 0xFF202020));
            } else {
                style.drawable("backgroundDrawable").draw(context, bounds);
            }
            border(renderer, bounds, style.number("borderWidth", 1), color(context, style, "border", 0xFF555555));
        }
        if (view.isFocused() && !"radio".equals(kind)) {
            border(renderer, bounds, 1, color(context, style, "focus", 0xFFFFFFA0));
        }
        if ("image".equals(kind)) {
            if (style.drawable("contentDrawable") != null) {
                style.drawable("contentDrawable").draw(context, bounds);
            } else {
                renderer.drawPackagedTexture(view.image(), bounds, 0, 0, 1, 1,
                        (int) Math.round(context.getOpacity() * 255));
            }
        } else if ("divider".equals(kind)) {
            renderer.drawRect(bounds.getLeft(), bounds.getTop(), bounds.getRight(), bounds.getTop() + 1,
                    color(context, style, "border", 0xFF555555));
        } else if ("progress".equals(kind) || "slider".equals(kind)) {
            range(context, view, style);
        } else if ("tabs".equals(kind)) {
            GuiRadioView tabs = (GuiRadioView) view;
            for (int i = 0; i < tabs.options().options.size(); i++) {
                GuiSelectionOptions.Option option = tabs.options().options.get(i);
                Rect tab = tabs.optionBounds(i);
                if (option.key.equals(tabs.selectedKey())) {
                    renderer.drawRect(tab, color(context, style, "selection", 0x8855AAFF));
                }
                renderer.pushClip(tab);
                try {
                    style.font().draw(context, option.label, tab.getLeft() + 4, tab.getTop() + 5,
                            option.enabled ? foreground : 0xFF777777, style.flag("shadow", true));
                } finally {
                    renderer.popClip();
                }
                if (tabs.isFocused() && option.key.equals(tabs.focusKey())) {
                    border(renderer, tab, 1, color(context, style, "focus", 0xFFFFFFA0));
                }
            }
        } else if ("radio".equals(kind)) {
            radio(context, (GuiRadioView) view, style, foreground);
        } else if ("stepper".equals(kind)) {
            stepper(context, view, style, foreground);
        } else if ("textarea".equals(kind)) {
            GuiTextAreaFrame frame = (GuiTextAreaFrame) context.getFrameContent();
            if (frame != null) {
                frame.paint(context, foreground, color(context, style, "selection", 0x8855AAFF),
                        style.flag("shadow", true), view.caretVisible(context));
            }
        } else if ("dropdown".equals(kind) || "contextmenu".equals(kind)) {
            int y = bounds.getTop() + (bounds.getHeight() - style.font().lineHeight(context)) / 2;
            renderer.pushClip(new Rect(bounds.getLeft() + 4, bounds.getTop(),
                    Math.max(bounds.getLeft() + 4, bounds.getRight() - 16), bounds.getBottom()));
            try {
                style.font().draw(context, view.text(), bounds.getLeft() + 4, y, foreground,
                        style.flag("shadow", true));
            } finally {
                renderer.popClip();
            }
            renderer.pushClip(bounds);
            try {
                style.font().draw(context, "dropdown".equals(kind) ? "v" : "...", bounds.getRight() - 14, y, foreground,
                        style.flag("shadow", true));
            } finally {
                renderer.popClip();
            }
        } else if ("textbox".equals(kind) || "numberbox".equals(kind)) {
            editor(context, view, style, foreground);
        } else if (!view.text().isEmpty()) {
            int x = bounds.getLeft();
            int y = bounds.getTop();
            if (view.isFocusable()) {
                x += Math.max(style.number("padding", 4),
                        (bounds.getWidth() - style.font().width(context, view.text())) / 2);
                y += (bounds.getHeight() - style.font().lineHeight(context)) / 2;
            }
            if ("checkbox".equals(kind)) {
                int boxY = bounds.getTop() + (bounds.getHeight() - 12) / 2;
                Rect box = Rect.fromPositionAndSize(bounds.getLeft() + 4, boxY, 12, 12);
                border(renderer, box, 1, foreground);
                if (view.selected()) {
                    renderer.drawRect(box.getLeft() + 3, box.getTop() + 3, box.getRight() - 3, box.getBottom() - 3,
                            color(context, style, "accent", 0xFF55AAFF));
                }
                x = bounds.getLeft() + 22;
            }
            renderer.pushClip(bounds);
            try {
                style.font().draw(context, view.text(), x, y, foreground, style.flag("shadow", true));
            } finally {
                renderer.popClip();
            }
        }
    }

    private static void radio(GuiContext context, GuiRadioView view, GuiControlStyle style, int foreground) {
        GuiRenderer renderer = context.getRenderer();
        String focus = view.focusKey();
        for (int i = 0; i < view.options().options.size(); i++) {
            Rect bounds = view.optionBounds(i);
            if (bounds.isEmpty()
                    || new GraphicsClip(bounds.getLeft(), bounds.getTop(), bounds.getRight(), bounds.getBottom())
                            .transform(context.getElementTransform()).intersect(context.getElementClip()).isEmpty()) {
                continue;
            }
            GuiSelectionOptions.Option option = view.options().options.get(i);
            int color = option.enabled
                    ? foreground
                    : color(context, view.style(GuiStyleSheet.DISABLED), "foreground", 0xFF777777);
            renderer.pushClip(bounds);
            try {
                int x = bounds.getLeft() + 4;
                int y = bounds.getTop() + (bounds.getHeight() - 12) / 2;
                renderer.drawRect(x + 3, y, x + 9, y + 1, color);
                renderer.drawRect(x + 3, y + 11, x + 9, y + 12, color);
                renderer.drawRect(x, y + 3, x + 1, y + 9, color);
                renderer.drawRect(x + 11, y + 3, x + 12, y + 9, color);
                renderer.drawRect(x + 1, y + 1, x + 3, y + 3, color);
                renderer.drawRect(x + 9, y + 1, x + 11, y + 3, color);
                renderer.drawRect(x + 1, y + 9, x + 3, y + 11, color);
                renderer.drawRect(x + 9, y + 9, x + 11, y + 11, color);
                if (option.key.equals(view.selectedKey())) {
                    renderer.drawRect(x + 4, y + 4, x + 8, y + 8, color(context, style, "accent", 0xFF55AAFF));
                }
                style.font().draw(context, option.label, bounds.getLeft() + 22,
                        bounds.getTop() + (bounds.getHeight() - style.font().lineHeight(context)) / 2, color,
                        style.flag("shadow", true));
                if (view.isFocused() && option.key.equals(focus)) {
                    border(renderer, bounds, 1, color(context, style, "focus", 0xFFFFFFA0));
                }
            } finally {
                renderer.popClip();
            }
        }
    }

    private static void stepper(GuiContext context, GuiStyledView view, GuiControlStyle style, int foreground) {
        Rect bounds = view.getBounds();
        int side = view.stepperButtonWidth();
        int y = bounds.getTop() + (bounds.getHeight() - style.font().lineHeight(context)) / 2;
        GuiRenderer renderer = context.getRenderer();
        renderer.pushClip(bounds);
        try {
            renderer.drawRect(bounds.getLeft() + side, bounds.getTop(), bounds.getLeft() + side + 1, bounds.getBottom(),
                    color(context, style, "border", 0xFF555555));
            renderer.drawRect(bounds.getRight() - side - 1, bounds.getTop(), bounds.getRight() - side,
                    bounds.getBottom(), color(context, style, "border", 0xFF555555));
            style.font().draw(context, "-", bounds.getLeft() + (side - style.font().width(context, "-")) / 2, y,
                    foreground, style.flag("shadow", true));
            style.font().draw(context, "+", bounds.getRight() - side + (side - style.font().width(context, "+")) / 2, y,
                    foreground, style.flag("shadow", true));
            renderer.pushClip(Rect.fromPositionAndSize(bounds.getLeft() + side + 2, bounds.getTop(),
                    Math.max(0, bounds.getWidth() - side * 2 - 4), bounds.getHeight()));
            try {
                style.font().draw(context, view.text(),
                        bounds.getLeft() + (bounds.getWidth() - style.font().width(context, view.text())) / 2, y,
                        foreground, style.flag("shadow", true));
            } finally {
                renderer.popClip();
            }
        } finally {
            renderer.popClip();
        }
    }

    private static void range(GuiContext context, GuiStyledView view, GuiControlStyle style) {
        GuiRenderer renderer = context.getRenderer();
        Rect bounds = view.getBounds();
        int accent = color(context, style, "accent", 0xFF55AAFF);
        if ("progress".equals(view.kind())) {
            Map<String, Object> values = view.templateValues(false);
            String presentation = (String) values.get("presentation");
            boolean indeterminate = Boolean.TRUE.equals(values.get("indeterminate"));
            double phase = GuiStatusGeometry.phase(context.getCurrentTimeMillis());
            double fraction = indeterminate ? phase : view.fraction();
            if ("radial".equals(presentation)) {
                renderer.drawSector(bounds, 0, 1, color(context, style, "background", 0xFF202020));
                renderer.drawSector(bounds, indeterminate ? phase : 0, indeterminate ? 0.25 : fraction, accent);
                return;
            }
            if ("label".equals(presentation)) {
                renderer.pushClip(bounds);
                try {
                    style.font().draw(context, view.statusLabel(), bounds.getLeft(), bounds.getTop(),
                            color(context, style, "foreground", 0xFFE0E0E0), style.flag("shadow", true));
                } finally {
                    renderer.popClip();
                }
                return;
            }
            if ("segmented".equals(presentation)) {
                int count = ((Number) values.get("segments")).intValue();
                for (int index = 0; index < count; index++) {
                    int start = (view.vertical() ? bounds.getHeight() : bounds.getWidth()) * index / count;
                    int end = (view.vertical() ? bounds.getHeight() : bounds.getWidth()) * (index + 1) / count;
                    double fill = indeterminate
                            ? index == Math.min(count - 1, (int) (phase * count)) ? 1 : 0
                            : GuiStatusGeometry.segment(fraction, index, count);
                    Rect segment = view.vertical()
                            ? new Rect(bounds.getLeft(), bounds.getBottom() - end + 1, bounds.getRight(),
                                    bounds.getBottom() - start)
                            : new Rect(bounds.getLeft() + start, bounds.getTop(), bounds.getLeft() + end - 1,
                                    bounds.getBottom());
                    renderer.drawRect(segment, color(context, style, "background", 0xFF202020));
                    Rect filled = view.vertical()
                            ? new Rect(segment.getLeft(),
                                    segment.getBottom() - (int) Math.round(segment.getHeight() * fill),
                                    segment.getRight(), segment.getBottom())
                            : new Rect(segment.getLeft(), segment.getTop(),
                                    segment.getLeft() + (int) Math.round(segment.getWidth() * fill),
                                    segment.getBottom());
                    renderer.drawRect(filled, accent);
                }
                return;
            }
            renderer.drawRect(bounds, color(context, style, "background", 0xFF202020));
            if (indeterminate) {
                int extent = view.vertical() ? bounds.getHeight() : bounds.getWidth();
                int start = (int) Math.round((phase * 1.25 - 0.25) * extent);
                int end = Math.min(extent, start + Math.max(1, extent / 4));
                start = Math.max(0, start);
                renderer.drawRect(view.vertical()
                        ? new Rect(bounds.getLeft(), bounds.getBottom() - end, bounds.getRight(),
                                bounds.getBottom() - start)
                        : new Rect(bounds.getLeft() + start, bounds.getTop(), bounds.getLeft() + end,
                                bounds.getBottom()),
                        accent);
                return;
            }
            if (view.vertical()) {
                renderer.drawRect(bounds.getLeft(),
                        bounds.getBottom() - (int) Math.round(bounds.getHeight() * view.fraction()), bounds.getRight(),
                        bounds.getBottom(), accent);
            } else {
                renderer.drawRect(bounds.getLeft(), bounds.getTop(),
                        bounds.getLeft() + (int) Math.round(bounds.getWidth() * view.fraction()), bounds.getBottom(),
                        accent);
            }
        } else if (view.vertical()) {
            int y = bounds.getBottom() - 4 - (int) Math.round(Math.max(0, bounds.getHeight() - 8) * view.fraction());
            renderer.drawRect(bounds.getLeft() + bounds.getWidth() / 2 - 1, bounds.getTop() + 4,
                    bounds.getLeft() + bounds.getWidth() / 2 + 1, bounds.getBottom() - 4, accent);
            renderer.drawRect(bounds.getLeft() + 2, y - 3, bounds.getRight() - 2, y + 3, accent);
        } else {
            int x = bounds.getLeft() + 4 + (int) Math.round(Math.max(0, bounds.getWidth() - 8) * view.fraction());
            renderer.drawRect(bounds.getLeft() + 4, bounds.getTop() + bounds.getHeight() / 2 - 1, bounds.getRight() - 4,
                    bounds.getTop() + bounds.getHeight() / 2 + 1, accent);
            renderer.drawRect(x - 3, bounds.getTop() + 2, x + 3, bounds.getBottom() - 2, accent);
        }
    }

    private static void editor(GuiContext context, GuiStyledView view, GuiControlStyle style, int foreground) {
        GuiTextEditorModel editor = view.editor();
        if (editor == null) {
            return;
        }
        int padding = style.number("padding", 4);
        Rect bounds = view.getBounds();
        int available = Math.max(0, bounds.getWidth() - padding * 2 - 1);
        int offset = Math.min(view.textOffset(), editor.caret());
        if (style.font().width(context, editor.text().substring(offset, editor.caret())) > available) {
            int low = offset;
            int high = editor.caret();
            while (low < high) {
                int middle = low + (high - low) / 2;
                if (style.font().width(context, editor.text().substring(middle, editor.caret())) > available) {
                    low = middle + 1;
                } else {
                    high = middle;
                }
            }
            offset = low;
        }
        view.textOffset(offset);
        int end = fittingEnd(context, style.font(), editor.text(), offset, available);
        int x = bounds.getLeft() + padding;
        int y = bounds.getTop() + (bounds.getHeight() - style.font().lineHeight(context)) / 2;
        GuiRenderer renderer = context.getRenderer();
        renderer.pushClip(bounds);
        try {
            int first = Math.max(offset, Math.min(end, editor.selectionStart()));
            int last = Math.max(first, Math.min(end, editor.selectionEnd()));
            int selectionX = x + style.font().width(context, editor.text().substring(offset, first));
            renderer.drawRect(selectionX, y,
                    selectionX + style.font().width(context, editor.text().substring(first, last)),
                    y + style.font().lineHeight(context), color(context, style, "selection", 0x8855AAFF));
            style.font().draw(context, editor.text().substring(offset, end), x, y, foreground,
                    style.flag("shadow", true));
            if (view.caretVisible(context)) {
                int caretX = x + style.font().width(context, editor.text().substring(offset, editor.caret()));
                renderer.drawRect(caretX, y - 1, caretX + 1, y + style.font().lineHeight(context) + 1, foreground);
            }
        } finally {
            renderer.popClip();
        }
    }

    private static int fittingEnd(GuiContext context, GuiFontFace font, String text, int start, int available) {
        int low = start;
        int high = text.length();
        while (low < high) {
            int middle = low + (high - low + 1) / 2;
            if (font.width(context, text.substring(start, middle)) <= available) {
                low = middle;
            } else {
                high = middle - 1;
            }
        }
        return low;
    }

    private static int color(GuiContext context, GuiControlStyle style, String name, int fallback) {
        return color(context, style.number(name, fallback));
    }

    private static int color(GuiContext context, int value) {
        return value & 0xFFFFFF | (int) Math.round((value >>> 24) * context.getOpacity()) << 24;
    }

    private static void border(GuiRenderer renderer, Rect bounds, int width, int color) {
        int edge = Math.min(width, Math.min(bounds.getWidth(), bounds.getHeight()) / 2);
        renderer.drawRect(bounds.getLeft(), bounds.getTop(), bounds.getRight(), bounds.getTop() + edge, color);
        renderer.drawRect(bounds.getLeft(), bounds.getBottom() - edge, bounds.getRight(), bounds.getBottom(), color);
        renderer.drawRect(bounds.getLeft(), bounds.getTop() + edge, bounds.getLeft() + edge, bounds.getBottom() - edge,
                color);
        renderer.drawRect(bounds.getRight() - edge, bounds.getTop() + edge, bounds.getRight(),
                bounds.getBottom() - edge, color);
    }

    private GuiDefaultTemplate() {
    }
}
