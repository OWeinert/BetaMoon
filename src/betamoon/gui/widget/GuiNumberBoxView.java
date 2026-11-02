package betamoon.gui.widget;

import betamoon.gui.control.GuiClipboard;
import betamoon.gui.control.GuiNumericEntry;
import betamoon.gui.control.GuiTextAreaEditing;
import betamoon.gui.control.GuiTextAreaLayout;
import betamoon.gui.control.GuiTextEditorModel;
import betamoon.gui.framework.GuiContainer;
import betamoon.gui.framework.GuiContext;
import betamoon.gui.framework.GuiFrameContent;
import betamoon.gui.framework.GuiGeometry.Constraints;
import betamoon.gui.framework.GuiGeometry.Rect;
import betamoon.gui.framework.GuiGeometry.Size;
import betamoon.gui.framework.GuiInputEvent;
import betamoon.gui.framework.GuiMeasurementPreview;
import betamoon.gui.presentation.GuiPresentationNode;
import betamoon.gui.presentation.GuiViewPublication;
import betamoon.gui.presentation.GuiViewStateAdmission;
import betamoon.gui.style.GuiFontFace;
import betamoon.gui.style.GuiStyleSheet;
import betamoon.gui.style.GuiVisualTemplate;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import org.lwjgl.input.Keyboard;

/**
 * Single-line numeric drafts using shared editing, numeric policy and retained
 * state admission.
 */
public final class GuiNumberBoxView extends GuiStyledView {
    private Events events;
    private Map<String, Object> values = Collections.emptyMap();
    private GuiViewStateAdmission admission;
    private GuiNumericEntry policy;
    private GuiTextEditorModel editor;
    private double value;
    private boolean controlled;
    private boolean closed;
    private boolean rejected;
    private int offset;
    private long revision;
    private long blinkStart;
    private GuiTextAreaLayout line;
    private GuiFontFace font;

    public GuiNumberBoxView(GuiStyleSheet theme, GuiVisualTemplate template, Events events) {
        super("numberbox", theme, template, events);
        this.events = events;
        setFocusable(true);
    }

    @Override
    public GuiContainer childContainer() {
        return null;
    }

    @Override
    public void validateValues(Map<String, Object> values) {
        super.validateValues(values);
        policy(values).accept(number(values, "value", 0));
    }

    @Override
    public GuiNumberBoxView configure(Map<String, Object> values) {
        super.configure(values);
        editor = null;
        publishNumber(GuiBuiltInViews.properties("numberbox").withDefaults(values).baseline(), name -> false);
        return this;
    }

    @Override
    public void publish(GuiPresentationNode node) {
        super.publish(node);
        publishNumber(node.values(), node::controlled);
    }

    private void publishNumber(Map<String, Object> values, Predicate<String> controlled) {
        Map<String, Object> next = projectState(values, controlled, captureState());
        this.values = values;
        policy = policy(values);
        this.controlled = controlled.test("value");
        restore(next);
    }

    @Override
    protected Size measureElement(GuiContext context, Constraints constraints) {
        GuiViewPublication pending = GuiMeasurementPreview.values(this);
        Map<String, Object> values = pending == null ? this.values : pending.values;
        return constraints.constrain(
                new Size(dimension(values, "width", "minWidth", 120), dimension(values, "height", "minHeight", 20)));
    }

    @Override
    protected void arrangeChildren(GuiContext context) {
        if (editor == null) {
            return;
        }
        GuiTextAreaLayout line = line(context);
        int available = Math.max(0, getWidth() - style(0).number("padding", 4) * 2 - 1);
        offset = Math.min(offset, editor.caret());
        while (offset < editor.caret() && line.xAt(0, editor.caret()) - line.xAt(0, offset) > available) {
            offset = line.next(offset);
        }
    }

    private GuiTextAreaLayout line(GuiContext context) {
        GuiFontFace font = style(0).font();
        if (line == null || !line.text.equals(editor.text()) || this.font != font) {
            line = new GuiTextAreaLayout(editor.text(), 1000000000, false, Math.max(1, font.lineHeight(context)),
                    glyph -> font.width(context, glyph));
            this.font = font;
        }
        return line;
    }

    @Override
    protected GuiFrameContent sampleFrameContent() {
        return editor == null
                ? null
                : new DisplayedNumber(editor.text(), offset, getBounds(), style(0).font(),
                        style(0).number("padding", 4));
    }

    private static final class DisplayedNumber implements GuiFrameContent {
        final String text;
        final int offset;
        final Rect bounds;
        final GuiFontFace font;
        final int padding;

        DisplayedNumber(String text, int offset, Rect bounds, GuiFontFace font, int padding) {
            this.text = text;
            this.offset = offset;
            this.bounds = bounds;
            this.font = font;
            this.padding = padding;
        }

        public boolean contains(double x, double y) {
            return true;
        }
    }

    @Override
    protected boolean onInput(GuiContext context, GuiInputEvent event) {
        if (closed || editor == null) {
            return false;
        }
        if (event.getType() == GuiInputEvent.Type.POINTER_DOWN && event.getButton() == 0) {
            DisplayedNumber frame = (DisplayedNumber) context.getFrameContent();
            if (frame == null || !frame.text.equals(editor.text()) || frame.offset != offset
                    || !frame.bounds.equals(getBounds()) || frame.font != style(0).font()
                    || frame.padding != style(0).number("padding", 4)) {
                return true;
            }
            requestFocus(context);
            GuiTextEditorModel candidate = editor.copy();
            GuiTextAreaLayout line = line(context);
            candidate.move(
                    line.positionAt(0,
                            event.getMouseX() - getLeft() - style(0).number("padding", 4) + line.xAt(0, offset)),
                    event.isShiftDown());
            apply(candidate, value, null, context);
            return true;
        }
        if (event.getType() != GuiInputEvent.Type.KEY_TYPED || !isFocused()) {
            return false;
        }
        if (!event.isControlDown()
                && (event.getKeyCode() == Keyboard.KEY_RETURN || event.getKeyCode() == Keyboard.KEY_NUMPADENTER)) {
            commit();
            return true;
        }
        GuiTextAreaEditing edit = GuiTextAreaEditing.key(editor, line(context), false, -1, event, 1,
                () -> GuiClipboard.read(GuiNumericEntry.MAXIMUM_LENGTH).trim());
        if (!edit.handled) {
            return false;
        }
        if (!GuiNumericEntry.draft(edit.editor.text())) {
            rejected = true;
            requestFrame();
            return true;
        }
        if (apply(edit.editor, value, edit.clipboardWrite, context) && edit.cancel) {
            events.propose("cancel", value);
        }
        return true;
    }

    private boolean apply(GuiTextEditorModel candidate, double value, String clipboard, GuiContext context) {
        try (GuiViewStateAdmission.Proposal proposal = begin(state(candidate, value, offset))) {
            editor = candidate;
            this.value = value;
            revision++;
            rejected = false;
            blinkStart = context.getCurrentTimeMillis();
            proposal.commit(captureState());
            requestLayout();
        } catch (GuiViewStateAdmission.Refusal refused) {
            rejected = true;
            requestFrame();
            return false;
        }
        if (clipboard != null) {
            GuiClipboard.write(clipboard);
        }
        return true;
    }

    private void commit() {
        if (editor.pending()) {
            return;
        }
        double next;
        try {
            next = policy.parse(editor.text());
        } catch (IllegalArgumentException invalid) {
            markInvalid();
            return;
        }
        GuiTextEditorModel candidate = editor.copy();
        String canonical = GuiNumericEntry.format(next);
        if (!candidate.text().equals(canonical)) {
            candidate.selectAll();
            candidate.insert(canonical);
        }
        candidate.commit(true, controlled && next != value);
        GuiTextEditorModel rejected = editor.copy();
        rejected.commit(false);
        try (GuiViewStateAdmission.Proposal refusal = begin(state(rejected, value, offset));
                GuiViewStateAdmission.Proposal proposal = begin(state(candidate, controlled ? value : next, offset))) {
            if (next != value && !events.propose("change", next)) {
                editor = rejected;
            } else {
                editor = candidate;
                if (!controlled) {
                    value = next;
                }
            }
            proposal.commit(captureState());
            refusal.commit(captureState());
            revision++;
            this.rejected = false;
            requestLayout();
        } catch (GuiViewStateAdmission.Refusal refused) {
            this.rejected = true;
            requestFrame();
        }
    }

    private void markInvalid() {
        GuiTextEditorModel candidate = editor.copy();
        candidate.commit(false);
        try (GuiViewStateAdmission.Proposal proposal = begin(state(candidate, value, offset))) {
            editor = candidate;
            revision++;
            proposal.commit(captureState());
        } catch (GuiViewStateAdmission.Refusal refused) {
            rejected = true;
        }
        requestFrame();
    }

    private GuiViewStateAdmission.Proposal begin(Map<String, Object> state) {
        try {
            return admission == null ? GuiViewStateAdmission.UNRESTRICTED : admission.begin(state);
        } catch (IllegalArgumentException refused) {
            throw new GuiViewStateAdmission.Refusal(refused);
        }
    }

    @Override
    protected void onFocusChanged(GuiContext context, boolean focused) {
        super.onFocusChanged(context, focused);
        blinkStart = context.getCurrentTimeMillis();
        if (!focused && !closed && editor != null && !editor.text().equals(editor.committed())) {
            commit();
        }
    }

    @Override
    protected int states() {
        return super.states() | (invalid() ? GuiStyleSheet.INVALID : 0);
    }

    @Override
    public boolean interaction(String name) {
        return name.equals("invalid") ? invalid() : super.interaction(name);
    }

    private boolean invalid() {
        return rejected || editor != null && (editor.invalid() || editor.conflict());
    }

    @Override
    public String text() {
        return editor == null ? "" : editor.text();
    }

    @Override
    public GuiTextEditorModel editor() {
        return editor;
    }

    @Override
    public boolean caretVisible(GuiContext context) {
        return isFocused() && (context.getCurrentTimeMillis() - blinkStart) / 500 % 2 == 0;
    }

    @Override
    public int textOffset() {
        return offset;
    }

    @Override
    public void textOffset(int value) {
        offset = value;
    }

    @Override
    protected Object templateValue(String name, boolean targets) {
        return name.equals("value") ? value : name.equals("text") ? text() : super.templateValue(name, targets);
    }

    @Override
    public Map<String, Object> templateValues(boolean targets) {
        Map<String, Object> result = new LinkedHashMap<>(super.templateValues(targets));
        result.put("text", text());
        result.put("value", value);
        return Collections.unmodifiableMap(result);
    }

    @Override
    public void admitStateWith(GuiViewStateAdmission admission) {
        if (closed || this.admission != null) {
            throw new IllegalStateException("Install numeric entry admission once before activation");
        }
        this.admission = Objects.requireNonNull(admission, "numeric entry state admission");
    }

    @Override
    public Map<String, Object> captureState() {
        return editor == null ? Collections.emptyMap() : state(editor, value, offset);
    }

    @Override
    public Map<String, Object> projectState(Map<String, Object> values, Predicate<String> controlled,
            Map<String, Object> current) {
        GuiNumericEntry policy = policy(values);
        double declared = policy.accept(number(values, "value", 0));
        double value = controlled.test("value") || editor == null && !current.containsKey("value")
                ? declared
                : policy.reconcile(current.containsKey("value") ? savedValue(current) : this.value);
        GuiTextEditorModel candidate = new GuiTextEditorModel(GuiNumericEntry.format(value),
                GuiNumericEntry.MAXIMUM_LENGTH);
        if (current.containsKey("editor")) {
            candidate.restore(savedEditor(current), true);
            if (!controlled.test("value") && this.controlled) {
                candidate.restore(candidate.snapshot(), false);
            }
        }
        return state(candidate, value, Math.min(savedOffset(current), candidate.text().length()));
    }

    @Override
    public Runnable prepareStateRestore(Map<String, Object> state) {
        if (closed || editor == null) {
            throw new IllegalStateException("Cannot restore a closed/unpublished numeric entry");
        }
        for (String name : state.keySet()) {
            if (!Arrays.asList("value", "editor", "textOffset").contains(name)) {
                throw new IllegalArgumentException("Unknown numeric entry state: " + name);
            }
        }
        Map<String, Object> next = projectState(values, name -> controlled, state);
        long expected = revision;
        return () -> {
            if (closed || revision != expected) {
                throw new IllegalStateException("Numeric entry restore became stale");
            }
            restore(next);
        };
    }

    private void restore(Map<String, Object> state) {
        value = savedValue(state);
        editor = new GuiTextEditorModel(GuiNumericEntry.format(value), GuiNumericEntry.MAXIMUM_LENGTH);
        editor.restoreSnapshot(savedEditor(state));
        offset = savedOffset(state);
        revision++;
        requestLayout();
    }

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            editor = null;
            admission = null;
            font = null;
            line = null;
            values = Collections.emptyMap();
            events = (event, value) -> false;
            super.close();
        }
    }

    private static GuiNumericEntry policy(Map<String, Object> values) {
        return new GuiNumericEntry(number(values, "minimum", 0), number(values, "maximum", 100),
                number(values, "step", 0));
    }

    private static double number(Map<String, Object> values, String name, double fallback) {
        return ((Number) values.getOrDefault(name, fallback)).doubleValue();
    }

    private static int dimension(Map<String, Object> values, String preferred, String minimum, int fallback) {
        double value = number(values, preferred, 0);
        return Math.max((int) Math.round(number(values, minimum, 0)), value > 0 ? (int) Math.round(value) : fallback);
    }

    private static double savedValue(Map<String, Object> state) {
        Object value = state.get("value");
        double number = value instanceof Number ? ((Number) value).doubleValue() : Double.NaN;
        if (!Double.isFinite(number) || Math.abs(number) > 1.0E12) {
            throw new IllegalArgumentException("Invalid numeric entry state value");
        }
        return number;
    }

    private static int savedOffset(Map<String, Object> state) {
        Object value = state.getOrDefault("textOffset", 0);
        double number = value instanceof Number ? ((Number) value).doubleValue() : Double.NaN;
        if (!Double.isFinite(number) || number != Math.rint(number) || number < 0
                || number > GuiNumericEntry.MAXIMUM_LENGTH) {
            throw new IllegalArgumentException("Invalid numeric entry text offset");
        }
        return (int) number;
    }

    @SuppressWarnings("unchecked")
    private static GuiTextEditorModel.Snapshot savedEditor(Map<String, Object> state) {
        Object value = state.get("editor");
        if (!(value instanceof Map)) {
            throw new IllegalArgumentException("Numeric entry requires a detached editor record");
        }
        GuiTextEditorModel.Snapshot result = GuiTextEditorModel.Snapshot.read((Map<String, Object>) value);
        if (result.multiline || result.maximumLength != GuiNumericEntry.MAXIMUM_LENGTH
                || !GuiNumericEntry.draft(result.committed) || !GuiNumericEntry.draft(result.draft)
                || new GuiNumericEntry(-1.0E12, 1.0E12, 0).parse(result.committed) != savedValue(state)) {
            throw new IllegalArgumentException("Numeric entry editor constraints are incompatible");
        }
        return result;
    }

    private static Map<String, Object> state(GuiTextEditorModel editor, double value, int offset) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("value", value);
        state.put("editor", editor.snapshot().values());
        state.put("textOffset", Math.min(offset, editor.text().length()));
        return Collections.unmodifiableMap(state);
    }
}
