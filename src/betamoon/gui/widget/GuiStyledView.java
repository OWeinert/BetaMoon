package betamoon.gui.widget;

import betamoon.gui.control.GuiActivation;
import betamoon.gui.control.GuiClipboard;
import betamoon.gui.control.GuiNumericRange;
import betamoon.gui.control.GuiTextEditorModel;
import betamoon.gui.framework.GuiContainer;
import betamoon.gui.framework.GuiContext;
import betamoon.gui.framework.GuiElement;
import betamoon.gui.framework.GuiGeometry.Constraints;
import betamoon.gui.framework.GuiGeometry.Rect;
import betamoon.gui.framework.GuiGeometry.Size;
import betamoon.gui.framework.GuiInputEvent;
import betamoon.gui.framework.GuiViewportLayout;
import betamoon.gui.graphics.GraphicsTransform;
import betamoon.gui.presentation.GuiPresentationNode;
import betamoon.gui.presentation.GuiViewAdapter;
import betamoon.gui.style.GuiControlStyle;
import betamoon.gui.style.GuiStyleSheet;
import betamoon.gui.style.GuiVisualTemplate;
import betamoon.gui.style.GuiNativeButtonSkin;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import org.lwjgl.input.Keyboard;

/**
 * Shared retained primitives and controls. Java and parsed Lua documents
 * instantiate this same view.
 */
public final class GuiStyledView extends GuiContainer implements GuiViewAdapter, GuiViewportLayout {
    @FunctionalInterface
    public interface Events {
        boolean propose(String event, Object value);
    }

    private final String kind;
    private final GuiStyleSheet theme;
    private final GuiVisualTemplate template;
    private Events events;
    private final GuiActivation activation = new GuiActivation(GuiActivation.Timing.RELEASE);
    private GuiNumericRange range = new GuiNumericRange(0, 100, 1);
    private GuiTextEditorModel editor;
    private String text = "";
    private String image = "";
    private String variant = "default";
    private String align = "stretch";
    private String justify = "start";
    private double preferredWidth;
    private double preferredHeight;
    private double minimumWidth;
    private double minimumHeight;
    private double layoutX;
    private double layoutY;
    private double anchorX = 0.5;
    private double anchorY = 0.5;
    private double grow;
    private double padding;
    private double gap;
    private double value;
    private boolean selected;
    private boolean valueControlled;
    private boolean selectedControlled;
    private boolean textControlled;
    private boolean fill;
    private boolean hovered;
    private boolean dragging;
    private boolean published;
    private boolean closed;
    private int textOffset;
    private long caretStart;
    private List<?> options = Collections.singletonList("");
    private boolean vertical;
    private int preparedNativeTexture = -1;
    private boolean clipChildren;

    public GuiStyledView(String kind, GuiStyleSheet theme, GuiVisualTemplate template, Events events) {
        this.kind = Objects.requireNonNull(kind, "view kind");
        this.theme = Objects.requireNonNull(theme, "view theme");
        this.template = Objects.requireNonNull(template, "view template");
        this.events = Objects.requireNonNull(events, "view events");
        for (String part : requiredParts(kind)) {
            if (!template.parts().contains(part)) {
                throw new IllegalArgumentException(kind + " template is missing required part: " + part);
            }
        }
        setFocusable(Arrays.asList("button", "checkbox", "choice", "slider", "textbox").contains(kind));
    }

    private static Iterable<String> requiredParts(String kind) {
        if ("slider".equals(kind)) {
            return Arrays.asList("track", "thumb", "focus");
        }
        if ("textbox".equals(kind)) {
            return Arrays.asList("content", "caret", "selection", "focus");
        }
        return Arrays.asList("button", "checkbox", "choice").contains(kind)
                ? Arrays.asList("content", "focus")
                : Collections.emptyList();
    }

    @Override
    public GuiElement element() {
        return this;
    }

    @Override
    public void validateValues(Map<String, Object> values) {
        theme.resolve(String.valueOf(values.getOrDefault("variant", "default")), 0).requireActive();
        if (!Arrays.asList("start", "center", "end", "stretch").contains(values.getOrDefault("align", "stretch"))
                || !Arrays.asList("start", "center", "end").contains(values.getOrDefault("justify", "start"))
                || !Arrays.asList("visible", "hidden", "collapsed")
                        .contains(values.getOrDefault("visibility", "visible"))) {
            throw new IllegalArgumentException("Invalid alignment, justification or visibility");
        }
        if ("slider".equals(kind) || "progress".equals(kind)) {
            new GuiNumericRange(number(values, "minimum", 0), number(values, "maximum", 100),
                    number(values, "step", 1));
            if (!Arrays.asList("horizontal", "vertical").contains(values.getOrDefault("orientation", "horizontal"))) {
                throw new IllegalArgumentException("Orientation requires horizontal or vertical");
            }
        }
        if ("choice".equals(kind)) {
            List<?> choices = (List<?>) values.get("options");
            if (choices.isEmpty() || number(values, "value", 1) > choices.size()) {
                throw new IllegalArgumentException("Choice requires options and a selected index within them");
            }
        }
        if ("textbox".equals(kind) && string(values.get("text"), "").length() > number(values, "maximumLength", 4096)) {
            throw new IllegalArgumentException("Textbox value exceeds maximumLength");
        }
    }

    @Override
    public void publish(GuiPresentationNode node) {
        publishValues(node.values(), node::controlled);
    }

    /**
     * Java authors configure the same validated properties used by parsed
     * documents.
     */
    public GuiStyledView configure(Map<String, Object> values) {
        Map<String, Object> normalized = GuiBuiltInViews.properties(kind).withDefaults(values).baseline();
        validateValues(normalized);
        published = false;
        editor = null;
        publishValues(normalized, name -> false);
        return this;
    }

    private void publishValues(Map<String, Object> values, Predicate<String> controlled) {
        double width = number(values, "width", 0);
        double height = number(values, "height", 0);
        double nextPadding = number(values, "padding", 0);
        double nextGap = number(values, "gap", 0);
        String nextText = string(values.get("text"), "");
        String nextVariant = string(values.get("variant"), "default");
        String nextAlign = string(values.get("align"), "stretch");
        String nextJustify = string(values.get("justify"), "start");
        if (preferredWidth != width || preferredHeight != height || minimumWidth != number(values, "minWidth", 0)
                || minimumHeight != number(values, "minHeight", 0) || padding != nextPadding || gap != nextGap
                || !text.equals(nextText) || !variant.equals(nextVariant) || !align.equals(nextAlign)
                || !justify.equals(nextJustify)) {
            requestLayout();
        }
        preferredWidth = width;
        preferredHeight = height;
        minimumWidth = number(values, "minWidth", 0);
        minimumHeight = number(values, "minHeight", 0);
        padding = nextPadding;
        gap = nextGap;
        text = nextText;
        variant = nextVariant;
        align = nextAlign;
        justify = nextJustify;
        if (layoutX != number(values, "x", 0) || layoutY != number(values, "y", 0)
                || anchorX != number(values, "anchorX", 0.5) || anchorY != number(values, "anchorY", 0.5)
                || grow != number(values, "grow", 0) || fill != Boolean.TRUE.equals(values.get("fill"))
                || vertical != "vertical".equals(values.get("orientation"))) {
            requestLayout();
        }
        layoutX = number(values, "x", 0);
        layoutY = number(values, "y", 0);
        anchorX = number(values, "anchorX", 0.5);
        anchorY = number(values, "anchorY", 0.5);
        grow = number(values, "grow", 0);
        vertical = "vertical".equals(values.get("orientation"));
        fill = Boolean.TRUE.equals(values.get("fill"));
        image = string(values.get("image"), "");
        valueControlled = controlled.test("value");
        selectedControlled = controlled.test("selected");
        textControlled = controlled.test("text");
        if (!published || valueControlled) {
            value = number(values, "value", 0);
        }
        if (!published || selectedControlled) {
            selected = Boolean.TRUE.equals(values.get("selected"));
        }
        if ("choice".equals(kind)) {
            options = (List<?>) values.get("options");
            value = Math.max(1, Math.min(options.size(), value));
        }
        if ("slider".equals(kind) || "progress".equals(kind)) {
            double minimum = number(values, "minimum", 0);
            double maximum = number(values, "maximum", 100);
            double step = number(values, "step", 1);
            if (range.minimum != minimum || range.maximum != maximum || range.step != step) {
                range = new GuiNumericRange(minimum, maximum, step);
            }
            value = "slider".equals(kind) ? range.snap(value) : Math.max(minimum, Math.min(maximum, value));
        }
        if ("textbox".equals(kind)) {
            if (editor == null || editor.maximumLength() != (int) number(values, "maximumLength", 4096)) {
                editor = new GuiTextEditorModel(text, (int) number(values, "maximumLength", 4096));
            } else if (textControlled) {
                editor.observe(text);
            }
        }
        setEnabled(!Boolean.FALSE.equals(values.get("enabled")));
        boolean nextClip = Boolean.TRUE.equals(values.get("clip"));
        if (clipChildren != nextClip) {
            clipChildren = nextClip;
            requestLayout();
        }
        setOpacity(number(values, "opacity", 1));
        setVisualTransform(new GraphicsTransform(number(values, "translateX", 0), number(values, "translateY", 0),
                number(values, "scaleX", 1), number(values, "scaleY", 1)));
        published = true;
    }

    @Override
    protected void updateElement(GuiContext context) {
        if (!closed) {
            theme.resolve(variant, 0).requireActive();
            template.prepare(context, this);
            if ("button".equals(kind) || "choice".equals(kind)) {
                preparedNativeTexture = context.getRenderer().preparePackagedTexture(GuiNativeButtonSkin.TEXTURE);
            }
            if (!image.isEmpty()) {
                context.getRenderer().preparePackagedTexture(image);
            }
        }
    }

    @Override
    protected Size measureElement(GuiContext context, Constraints constraints) {
        GuiControlStyle style = theme.resolve(variant, 0);
        int inset = isFocusable() ? style.number("padding", 4) : 0;
        int width = 0;
        int height = 0;
        if (Arrays.asList("text", "button", "checkbox", "choice", "textbox", "dialog", "tooltip").contains(kind)) {
            width = style.font().width(context, text()) + inset * 2 + ("checkbox".equals(kind) ? 20 : 0);
            height = style.font().lineHeight(context) + inset * 2;
        }
        boolean horizontal = "row".equals(kind);
        boolean flow = horizontal || "column".equals(kind);
        int primary = 0;
        int cross = 0;
        int count = 0;
        for (GuiElement child : getChildren()) {
            if (child.isCollapsed()) {
                continue;
            }
            Size size = child.measure(context,
                    Constraints.upTo(Math.min(16384, Math.max(0, constraints.getMaxWidth() - (int) padding * 2)),
                            Math.min(16384, Math.max(0, constraints.getMaxHeight() - (int) padding * 2))));
            if (flow) {
                primary += horizontal ? size.getWidth() : size.getHeight();
                cross = Math.max(cross, horizontal ? size.getHeight() : size.getWidth());
            } else {
                width = Math.max(width, size.getWidth());
                height = Math.max(height, size.getHeight());
            }
            count++;
        }
        if (flow) {
            primary += (int) gap * Math.max(0, count - 1);
            width = (horizontal ? primary : cross) + (int) padding * 2;
            height = (horizontal ? cross : primary) + (int) padding * 2;
        }
        if (isFocusable()) {
            width = Math.max(width, "textbox".equals(kind) || "slider".equals(kind) ? 120 : 40);
            height = Math.max(height, 20);
        }
        if ("progress".equals(kind)) {
            width = Math.max(width, vertical ? 8 : 100);
            height = Math.max(height, vertical ? 100 : 8);
        }
        if ("divider".equals(kind)) {
            width = Math.max(width, 1);
            height = Math.max(height, 1);
        }
        return constraints.constrain(new Size(
                Math.max((int) Math.round(minimumWidth), preferredWidth > 0 ? (int) Math.round(preferredWidth) : width),
                Math.max((int) Math.round(minimumHeight),
                        preferredHeight > 0 ? (int) Math.round(preferredHeight) : height)));
    }

    @Override
    public Rect viewportBounds(GuiContext context, Rect viewport) {
        int width = Math.min(16384,
                fill ? viewport.getWidth() : Math.min(viewport.getWidth(), getMeasuredSize().getWidth()));
        int height = Math.min(16384,
                fill ? viewport.getHeight() : Math.min(viewport.getHeight(), getMeasuredSize().getHeight()));
        return Rect.fromPositionAndSize(
                viewport.getLeft() + (int) Math.round((viewport.getWidth() - width) * anchorX + layoutX),
                viewport.getTop() + (int) Math.round((viewport.getHeight() - height) * anchorY + layoutY), width,
                height);
    }

    @Override
    protected void arrangeChildren(GuiContext context) {
        int left = getLeft() + (int) padding;
        int top = getTop() + (int) padding;
        int width = Math.max(0, getWidth() - (int) padding * 2);
        int height = Math.max(0, getHeight() - (int) padding * 2);
        boolean horizontal = "row".equals(kind);
        boolean flow = horizontal || "column".equals(kind);
        int fixed = 0;
        int count = 0;
        double weight = 0;
        for (GuiElement child : getChildren()) {
            if (child.isCollapsed()) {
                continue;
            }
            double grow = child instanceof GuiStyledView ? ((GuiStyledView) child).grow : 0;
            weight += grow;
            if (grow == 0) {
                fixed += horizontal ? child.getMeasuredSize().getWidth() : child.getMeasuredSize().getHeight();
            }
            count++;
        }
        int available = horizontal ? width : height;
        int remaining = Math.max(0, available - fixed - (int) gap * Math.max(0, count - 1));
        int cursor = weight > 0 || "start".equals(justify) ? 0 : "center".equals(justify) ? remaining / 2 : remaining;
        for (GuiElement child : getChildren()) {
            if (child.isCollapsed()) {
                continue;
            }
            GuiStyledView styled = child instanceof GuiStyledView ? (GuiStyledView) child : null;
            double grow = styled == null ? 0 : styled.grow;
            int primary = horizontal ? child.getMeasuredSize().getWidth() : child.getMeasuredSize().getHeight();
            if (grow > 0 && weight > 0) {
                primary = (int) Math.round(remaining * grow / weight);
                remaining -= primary;
                weight -= grow;
            }
            boolean stretchWidth = "stretch".equals(align) && (styled == null || styled.preferredWidth == 0);
            boolean stretchHeight = "stretch".equals(align) && (styled == null || styled.preferredHeight == 0);
            int childWidth = flow && horizontal
                    ? primary
                    : stretchWidth ? width : Math.min(width, child.getMeasuredSize().getWidth());
            int childHeight = flow && !horizontal
                    ? primary
                    : stretchHeight ? height : Math.min(height, child.getMeasuredSize().getHeight());
            int x = horizontal && flow ? cursor : aligned(width, childWidth);
            int y = !horizontal && flow ? cursor : aligned(height, childHeight);
            if (!flow && styled != null) {
                x += (int) Math.round(styled.layoutX);
                y += (int) Math.round(styled.layoutY);
            }
            child.arrange(context, Rect.fromPositionAndSize(left + x, top + y, childWidth, childHeight));
            cursor += primary + (int) gap;
        }
    }

    private int aligned(int available, int size) {
        return "center".equals(align) ? (available - size) / 2 : "end".equals(align) ? available - size : 0;
    }

    @Override
    protected void renderBeforeChildren(GuiContext context) {
        hovered = isHovered(context);
        template.paint(context, this, theme.resolve(variant, states()));
        if (clipChildren) {
            context.getRenderer().pushClip(getBounds());
        }
    }

    @Override
    protected void renderAfterChildren(GuiContext context) {
        if (clipChildren) {
            context.getRenderer().popClip();
        }
    }

    @Override
    protected Rect childInputClip() {
        return clipChildren ? getBounds() : null;
    }

    private int states() {
        return (hovered ? GuiStyleSheet.HOVERED : 0) | (activation.isPressed() || dragging ? GuiStyleSheet.PRESSED : 0)
                | (isFocused() ? GuiStyleSheet.FOCUSED : 0) | (selected ? GuiStyleSheet.SELECTED : 0)
                | (editor != null && (editor.invalid() || editor.conflict()) ? GuiStyleSheet.INVALID : 0)
                | (!isEnabled() ? GuiStyleSheet.DISABLED : 0);
    }

    @Override
    protected boolean onInput(GuiContext context, GuiInputEvent event) {
        if (closed || !isFocusable()) {
            return false;
        }
        if ("textbox".equals(kind)) {
            return edit(context, event);
        }
        if ("slider".equals(kind)) {
            return slide(context, event);
        }
        int result = activation.input(event, contains(event.getMouseX(), event.getMouseY()), isFocused());
        if ((result & GuiActivation.CAPTURE) != 0) {
            requestFocus(context);
            capturePointer(context);
        }
        if ((result & GuiActivation.RELEASE) != 0) {
            releasePointer(context);
        }
        if ((result & GuiActivation.ACTIVATE) != 0) {
            if ("checkbox".equals(kind)) {
                if (events.propose("change", !selected) && !selectedControlled) {
                    selected = !selected;
                }
            } else if ("choice".equals(kind)) {
                double next = (int) value % options.size() + 1;
                if (events.propose("change", next) && !valueControlled) {
                    value = next;
                    requestLayout();
                }
            } else {
                events.propose("activate", null);
            }
        }
        return (result & GuiActivation.CONSUMED) != 0;
    }

    private boolean slide(GuiContext context, GuiInputEvent event) {
        if (event.getType() == GuiInputEvent.Type.POINTER_CANCEL || event.getType() == GuiInputEvent.Type.POINTER_UP) {
            boolean previous = dragging;
            dragging = false;
            releasePointer(context);
            return previous;
        }
        if (event.getType() == GuiInputEvent.Type.POINTER_DOWN && event.getButton() == 0) {
            dragging = true;
            requestFocus(context);
            capturePointer(context);
        }
        if (dragging && (event.getType() == GuiInputEvent.Type.POINTER_DOWN
                || event.getType() == GuiInputEvent.Type.POINTER_DRAG)) {
            double ratio = vertical
                    ? (getBottom() - 4 - event.getMouseY()) / (double) Math.max(1, getHeight() - 8)
                    : (event.getMouseX() - getLeft() - 4) / (double) Math.max(1, getWidth() - 8);
            ratio = Math.max(0, Math.min(1, ratio));
            propose(range.minimum * (1 - ratio) + range.maximum * ratio);
            return true;
        }
        if (event.getType() == GuiInputEvent.Type.KEY_TYPED && isFocused()) {
            switch (event.getKeyCode()) {
                case Keyboard.KEY_LEFT:
                case Keyboard.KEY_DOWN:
                    propose(value - range.step);
                    return true;
                case Keyboard.KEY_RIGHT:
                case Keyboard.KEY_UP:
                    propose(value + range.step);
                    return true;
                case Keyboard.KEY_HOME:
                    propose(range.minimum);
                    return true;
                case Keyboard.KEY_END:
                    propose(range.maximum);
                    return true;
                default:
                    return false;
            }
        }
        return false;
    }

    private void propose(double proposed) {
        double next = range.snap(proposed);
        if (events.propose("change", next) && !valueControlled) {
            value = next;
        }
    }

    private boolean edit(GuiContext context, GuiInputEvent event) {
        if (editor == null) {
            return false;
        }
        if (event.getType() == GuiInputEvent.Type.POINTER_DOWN && event.getButton() == 0) {
            requestFocus(context);
            GuiControlStyle style = theme.resolve(variant, 0);
            int start = getLeft() + style.number("padding", 4);
            int index = textOffset;
            while (index < editor.text().length() && style.font().width(context,
                    editor.text().substring(textOffset, index + 1)) < event.getMouseX() - start) {
                index++;
            }
            editor.move(index, false);
            return true;
        }
        if (event.getType() != GuiInputEvent.Type.KEY_TYPED || !isFocused()) {
            return false;
        }
        int key = event.getKeyCode();
        boolean control = Keyboard.isCreated()
                && (Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL));
        if (control && key == Keyboard.KEY_A) {
            editor.selectAll();
        } else if (control && (key == Keyboard.KEY_C || key == Keyboard.KEY_X)) {
            GuiClipboard.write(editor.text().substring(editor.selectionStart(), editor.selectionEnd()));
            if (key == Keyboard.KEY_X) {
                editor.insert("");
            }
        } else if (control && key == Keyboard.KEY_V) {
            editor.insert(GuiClipboard.read(editor.maximumLength()));
        } else if (key == Keyboard.KEY_LEFT || key == Keyboard.KEY_RIGHT) {
            editor.move(editor.caret() + (key == Keyboard.KEY_LEFT ? -1 : 1), event.isShiftDown());
        } else if (key == Keyboard.KEY_HOME || key == Keyboard.KEY_END) {
            editor.move(key == Keyboard.KEY_HOME ? 0 : editor.text().length(), event.isShiftDown());
        } else if (key == Keyboard.KEY_BACK || key == Keyboard.KEY_DELETE) {
            editor.erase(key == Keyboard.KEY_BACK);
        } else if (key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER) {
            editor.commit(events.propose("commit", editor.text()), textControlled);
            caretStart = context.getCurrentTimeMillis();
            return true;
        } else if (key == Keyboard.KEY_ESCAPE) {
            editor.cancel();
            events.propose("cancel", editor.text());
            caretStart = context.getCurrentTimeMillis();
            return true;
        } else if (event.getTypedChar() >= 32 && event.getTypedChar() != 127) {
            editor.insert(String.valueOf(event.getTypedChar()));
        } else {
            return false;
        }
        caretStart = context.getCurrentTimeMillis();
        if (key != Keyboard.KEY_LEFT && key != Keyboard.KEY_RIGHT && key != Keyboard.KEY_HOME && key != Keyboard.KEY_END
                && !(control && (key == Keyboard.KEY_A || key == Keyboard.KEY_C))) {
            requestLayout();
            events.propose("change", editor.text());
        }
        return true;
    }

    @Override
    protected void onFocusChanged(GuiContext context, boolean focused) {
        if (!focused) {
            activation.cancelKeyboard();
            if (!closed && editor != null && !editor.pending() && !editor.text().equals(editor.committed())) {
                editor.commit(events.propose("commit", editor.text()), textControlled);
            }
        }
        caretStart = context.getCurrentTimeMillis();
    }

    @Override
    public boolean interaction(String name) {
        switch (name) {
            case "hovered":
                return hovered;
            case "pressed":
                return activation.isPressed() || dragging;
            case "focused":
                return isFocused();
            case "selected":
                return selected;
            case "disabled":
                return !isEnabled();
            case "invalid":
                return editor != null && (editor.invalid() || editor.conflict());
            default:
                return false;
        }
    }

    @Override
    public void retainFrom(GuiViewAdapter previous) {
        if (previous instanceof GuiStyledView) {
            GuiStyledView old = (GuiStyledView) previous;
            if (!valueControlled) {
                value = "choice".equals(kind)
                        ? Math.max(1, Math.min(options.size(), old.value))
                        : "slider".equals(kind)
                                ? range.snap(old.value)
                                : Math.max(range.minimum, Math.min(range.maximum, old.value));
            }
            if (!selectedControlled) {
                selected = old.selected;
            }
            if (editor != null && old.editor != null) {
                editor.retainFrom(old.editor);
            }
            textOffset = editor == null ? 0 : Math.min(old.textOffset, editor.text().length());
        }
    }

    @Override
    public void close() {
        closed = true;
        events = (event, value) -> false;
    }

    public String kind() {
        return kind;
    }

    public String text() {
        return editor != null ? editor.text() : "choice".equals(kind) ? text + options.get((int) value - 1) : text;
    }

    public String image() {
        return image;
    }

    public double fraction() {
        return range.fraction(value);
    }

    public boolean selected() {
        return selected;
    }

    public boolean caretVisible(GuiContext context) {
        return isFocused() && (context.getCurrentTimeMillis() - caretStart) / 500 % 2 == 0;
    }

    public GuiTextEditorModel editor() {
        return editor;
    }

    public int textOffset() {
        return textOffset;
    }

    public void textOffset(int value) {
        textOffset = value;
    }

    public boolean vertical() {
        return vertical;
    }

    public int preparedNativeTexture() {
        return preparedNativeTexture;
    }

    private static String string(Object value, String fallback) {
        return value instanceof String ? (String) value : fallback;
    }

    private static double number(Map<String, Object> values, String name, double fallback) {
        Object value = values.get(name);
        return value instanceof Number ? ((Number) value).doubleValue() : fallback;
    }
}
