package betamoon.gui.widget;

import betamoon.gui.control.GuiClipboard;
import betamoon.gui.control.GuiTextAreaEditing;
import betamoon.gui.control.GuiTextAreaLayout;
import betamoon.gui.control.GuiTextEditorModel;
import betamoon.gui.framework.GuiContainer;
import betamoon.gui.framework.GuiContext;
import betamoon.gui.framework.GuiElement;
import betamoon.gui.framework.GuiFrameContent;
import betamoon.gui.framework.GuiGeometry.Constraints;
import betamoon.gui.framework.GuiGeometry.Rect;
import betamoon.gui.framework.GuiGeometry.Size;
import betamoon.gui.framework.GuiInputEvent;
import betamoon.gui.framework.GuiMeasurementPreview;
import betamoon.gui.graphics.GraphicsTransform;
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

/**
 * Plain multiline input over the shared editor state, scene and scroll
 * gestures.
 */
public final class GuiTextAreaView extends GuiStyledView {
    private final GuiScrollView scroll = new GuiScrollView(new GuiElement() {
    }, GuiScrollView.Policy.VERTICAL);
    private final Runnable releasePositionHandler;
    private GuiTextEditorModel editor;
    private GuiTextAreaLayout layout;
    private GuiFontFace layoutFont;
    private int layoutWidth = -1;
    private boolean layoutWrap;
    private Map<String, Object> values = Collections.emptyMap();
    private Events events;
    private GuiViewStateAdmission admission;
    private int x;
    private int y;
    private int preferredX = -1;
    private boolean upstream;
    private boolean edited;
    private boolean controlled;
    private boolean closed;
    private boolean rejected;
    private long revision;
    private long blinkStart;
    private GuiTextAreaLayout dragLayout;
    private GraphicsTransform dragTransform;

    public GuiTextAreaView(GuiStyleSheet theme, GuiVisualTemplate template, Events events) {
        super("textarea", theme, template, events);
        this.events = Objects.requireNonNull(events, "textarea events");
        add(scroll);
        scroll.setVirtualContent(() -> x, () -> y);
        releasePositionHandler = scroll.handlePositionWith(this::proposeScroll);
        setFocusable(true);
    }

    @Override
    public GuiContainer childContainer() {
        return null;
    }

    public GuiScrollView viewport() {
        return scroll;
    }

    @Override
    public void validateValues(Map<String, Object> values) {
        super.validateValues(values);
        new GuiTextEditorModel((String) values.get("text"), integer(values, "maximumLength", 4096), true);
    }

    @Override
    public GuiTextAreaView configure(Map<String, Object> values) {
        super.configure(values);
        editor = null;
        publishArea(GuiBuiltInViews.properties("textarea").withDefaults(values).baseline(), name -> false);
        return this;
    }

    @Override
    public void publish(GuiPresentationNode node) {
        super.publish(node);
        publishArea(node.values(), node::controlled);
    }

    private void publishArea(Map<String, Object> values, Predicate<String> controlled) {
        Map<String, Object> previous = captureState();
        GuiTextEditorModel candidate = projectedEditor(values, controlled.test("text"), previous);
        boolean compatible = compatible(previous, candidate);
        this.values = values;
        this.controlled = controlled.test("text");
        editor = candidate;
        if (!compatible) {
            x = 0;
            y = 0;
            preferredX = -1;
            upstream = false;
            edited = false;
        }
        scroll.setPolicy(wrap() ? GuiScrollView.Policy.VERTICAL : GuiScrollView.Policy.BOTH);
        scroll.setScrollbarsVisible(!Boolean.FALSE.equals(values.get("scrollbars")));
        revision++;
        requestLayout();
    }

    private GuiTextEditorModel projectedEditor(Map<String, Object> values, boolean controlled,
            Map<String, Object> current) {
        GuiTextEditorModel candidate = new GuiTextEditorModel((String) values.get("text"),
                integer(values, "maximumLength", 4096), true);
        if (compatible(current, candidate)) {
            GuiTextEditorModel.Snapshot saved = savedEditor(current);
            candidate.restoreSnapshot(saved);
            if (controlled) {
                candidate.observe((String) values.get("text"));
            } else if (this.controlled) {
                candidate.restore(saved, false);
            }
        }
        return candidate;
    }

    private static boolean compatible(Map<String, Object> state, GuiTextEditorModel candidate) {
        if (!state.containsKey("editor")) {
            return false;
        }
        GuiTextEditorModel.Snapshot saved = savedEditor(state);
        return saved.multiline && saved.maximumLength == candidate.maximumLength();
    }

    @Override
    protected Size measureElement(GuiContext context, Constraints constraints) {
        GuiViewPublication pending = GuiMeasurementPreview.values(this);
        Map<String, Object> values = pending == null ? this.values : pending.values;
        int width = dimension(values, "width", "minWidth", 160);
        int height = dimension(values, "height", "minHeight", 60);
        Size size = constraints.constrain(new Size(width, height));
        GuiTextEditorModel model = pending == null
                ? editor
                : projectedEditor(values, pending.controlled("text"), captureState());
        if (model != null) {
            int inset = inset(values);
            GuiFontFace font = style(0).font();
            if (pending == null) {
                layoutFor(model, Math.max(0, size.getWidth() - inset * 2), context);
            } else {
                new GuiTextAreaLayout(model.text(), Math.max(1, size.getWidth() - inset * 2 - 1),
                        !Boolean.FALSE.equals(values.get("wrap")), font.lineHeight(context),
                        glyph -> font.width(context, glyph));
            }
        }
        return size;
    }

    @Override
    protected void arrangeChildren(GuiContext context) {
        if (editor == null) {
            return;
        }
        int inset = inset(values);
        Rect viewport = Rect.fromPositionAndSize(getLeft() + inset, getTop() + inset,
                Math.max(0, getWidth() - inset * 2), Math.max(0, getHeight() - inset * 2));
        GuiTextAreaLayout previous = layout;
        GuiTextAreaLayout next = layoutFor(editor, viewport.getWidth(), context);
        boolean reflow = previous != null && next != previous;
        if (reflow && dragLayout != null) {
            context.cancelGesturesIn(this);
        }
        layout = next;
        if (reflow && isFocused()) {
            int[] offsets = reveal(editor, next, viewport, x, y, upstream);
            x = offsets[0];
            y = offsets[1];
        }
        scroll.setContentSize(next.width, next.height);
        scroll.arrange(context, viewport);
        x = scroll.getScrollX();
        y = scroll.getScrollY();
        acceptLayout(next);
    }

    private GuiTextAreaLayout layoutFor(GuiTextEditorModel model, int width, GuiContext context) {
        GuiFontFace font = style(0).font();
        int available = Math.max(1, width - 1);
        if (layout != null && layout.text.equals(model.text()) && layoutWidth == available && layoutWrap == wrap()
                && layoutFont == font && layout.lineHeight == font.lineHeight(context)) {
            return layout;
        }
        return new GuiTextAreaLayout(model.text(), available, wrap(), font.lineHeight(context),
                glyph -> font.width(context, glyph));
    }

    private void acceptLayout(GuiTextAreaLayout layout) {
        this.layout = layout;
        layoutWidth = Math.max(1, scroll.getWidth() - 1);
        layoutWrap = wrap();
        layoutFont = style(0).font();
    }

    @Override
    protected GuiFrameContent sampleFrameContent() {
        return layout == null || editor == null ? null : frame();
    }

    public GuiTextAreaFrame frame() {
        return new GuiTextAreaFrame(layout, scroll.getBounds(), style(0).font(), x, y, editor, upstream);
    }

    @Override
    protected Rect focusBounds() {
        return layout == null || editor == null ? getBounds() : frame().visibleCaretBounds();
    }

    @Override
    protected void revealFocus(GuiContext context) {
        if (editor != null && layout != null) {
            int[] offsets = reveal(editor, layout, scroll.getBounds(), x, y, upstream);
            proposeScroll(wrap() ? 0 : offsets[0], offsets[1]);
        }
    }

    @Override
    public Object templateLayoutState() {
        return Arrays.asList(layout, x, y);
    }

    @Override
    public GuiTextEditorModel editor() {
        return editor;
    }

    @Override
    public String text() {
        return editor == null ? "" : editor.text();
    }

    @Override
    public boolean caretVisible(GuiContext context) {
        return isFocused() && (context.getCurrentTimeMillis() - blinkStart) / 500 % 2 == 0;
    }

    @Override
    protected int states() {
        return super.states()
                | (rejected || editor != null && (editor.invalid() || editor.conflict()) ? GuiStyleSheet.INVALID : 0);
    }

    @Override
    public boolean interaction(String name) {
        return name.equals("invalid")
                ? rejected || editor != null && (editor.invalid() || editor.conflict())
                : super.interaction(name);
    }

    @Override
    protected Object templateValue(String name, boolean targets) {
        return name.equals("text") ? text() : super.templateValue(name, targets);
    }

    @Override
    protected boolean onInput(GuiContext context, GuiInputEvent event) {
        if (event.getType() == GuiInputEvent.Type.POINTER_CANCEL) {
            return endDrag(context);
        }
        if (closed || editor == null) {
            return false;
        }
        if (event.getType() == GuiInputEvent.Type.KEY_TYPED && isFocused()) {
            GuiTextAreaLayout current = layoutFor(editor, scroll.getWidth(), context);
            GuiTextAreaEditing edit = GuiTextAreaEditing.key(editor, current, upstream, preferredX, event,
                    Math.max(1, scroll.getHeight() / current.lineHeight),
                    () -> GuiClipboard.read(editor.maximumLength(), true));
            if (!edit.handled) {
                return false;
            }
            GuiTextAreaLayout next = layoutFor(edit.editor, scroll.getWidth(), context);
            int[] offsets = reveal(edit.editor, next, scroll.getBounds(), x, y, edit.upstream);
            String notification = edit.cancel ? "cancel" : !editor.text().equals(edit.editor.text()) ? "change" : null;
            boolean changed = apply(edit.editor, next, offsets[0], offsets[1], edit.preferredX, edit.upstream,
                    edit.cancel ? false : edited || notification != null, notification, edit.clipboardWrite, context);
            if (changed) {
                context.revealFocusedRegion();
            }
            return true;
        }
        if (event.getType() == GuiInputEvent.Type.POINTER_DOWN && event.getButton() == 0) {
            GuiTextAreaFrame frame = (GuiTextAreaFrame) context.getFrameContent();
            if (!current(frame) || !frame.viewport.contains(event.getMouseX(), event.getMouseY())) {
                return true;
            }
            requestFocus(context);
            if (movePointer(frame, event.getMouseX(), event.getMouseY(), false, context)) {
                dragLayout = layout;
                dragTransform = context.getElementTransform();
                capturePointer(context);
            }
            return true;
        }
        if (event.getType() == GuiInputEvent.Type.POINTER_DRAG && dragLayout != null) {
            GraphicsTransform transform = context.getElementTransform();
            if (!event.isMouseDown() || dragLayout != layout || transform.x != dragTransform.x
                    || transform.y != dragTransform.y || transform.scaleX != dragTransform.scaleX
                    || transform.scaleY != dragTransform.scaleY) {
                return endDrag(context);
            }
            int nextX = wrap() ? 0 : x + outside(event.getMouseX(), scroll.getLeft(), scroll.getRight());
            int nextY = y + outside(event.getMouseY(), scroll.getTop(), scroll.getBottom());
            nextX = clamp(nextX, Math.max(0, layout.width - scroll.getWidth()));
            nextY = clamp(nextY, Math.max(0, layout.height - scroll.getHeight()));
            GuiTextAreaFrame dragged = new GuiTextAreaFrame(layout, scroll.getBounds(), style(0).font(), nextX, nextY,
                    editor, upstream);
            movePointer(dragged, event.getMouseX(), event.getMouseY(), true, context);
            return true;
        }
        if (event.getType() == GuiInputEvent.Type.POINTER_UP && event.getButton() == 0) {
            return endDrag(context);
        }
        return false;
    }

    private boolean current(GuiTextAreaFrame frame) {
        return frame != null && frame.layout == layout && frame.layout.text.equals(editor.text())
                && frame.viewport.equals(scroll.getBounds()) && frame.scrollX == x && frame.scrollY == y
                && frame.font == style(0).font();
    }

    private boolean movePointer(GuiTextAreaFrame frame, int x, int y, boolean selecting, GuiContext context) {
        GuiTextEditorModel candidate = editor.copy();
        int position = frame.positionAt(x, y);
        candidate.move(position, selecting);
        boolean upstream = position == frame.layout.rows.get(frame.rowAt(y)).end;
        return apply(candidate, frame.layout, frame.scrollX, frame.scrollY, -1, upstream, edited, null, null, context);
    }

    private boolean endDrag(GuiContext context) {
        boolean dragging = dragLayout != null;
        dragLayout = null;
        dragTransform = null;
        releasePointer(context);
        return dragging;
    }

    private boolean apply(GuiTextEditorModel candidate, GuiTextAreaLayout next, int x, int y, int preferredX,
            boolean upstream, boolean edited, String notification, String clipboardWrite, GuiContext context) {
        try (GuiViewStateAdmission.Proposal proposal = begin(state(candidate, x, y, preferredX, upstream, edited))) {
            if (notification != null && !events.propose(notification, candidate.text())) {
                rejected = true;
                requestFrame();
                return false;
            }
            editor = candidate;
            this.x = x;
            this.y = y;
            this.preferredX = preferredX;
            this.upstream = upstream;
            this.edited = edited;
            acceptLayout(next);
            revision++;
            rejected = false;
            blinkStart = context.getCurrentTimeMillis();
            scroll.publishScrollPosition(x, y);
            requestLayout();
            proposal.commit(captureState());
        } catch (GuiViewStateAdmission.Refusal refused) {
            rejected = true;
            requestFrame();
            return false;
        }
        if (clipboardWrite != null) {
            GuiClipboard.write(clipboardWrite);
        }
        return true;
    }

    private void proposeScroll(int x, int y) {
        if (editor == null || this.x == x && this.y == y) {
            return;
        }
        try (GuiViewStateAdmission.Proposal proposal = begin(state(editor, x, y, preferredX, upstream, edited))) {
            this.x = x;
            this.y = y;
            scroll.publishScrollPosition(x, y);
            revision++;
            proposal.commit(captureState());
            requestFrame();
        } catch (GuiViewStateAdmission.Refusal refused) {
            // Leave an exhausted/refused gesture available to an outer viewport.
        }
    }

    @Override
    protected void onFocusChanged(GuiContext context, boolean focused) {
        super.onFocusChanged(context, focused);
        blinkStart = context.getCurrentTimeMillis();
        if (!focused && !closed && editor != null && edited && !editor.pending()) {
            commit();
        }
    }

    private void commit() {
        GuiTextEditorModel candidate = editor.copy();
        candidate.commit(true, controlled && !candidate.text().equals(candidate.committed()));
        try (GuiViewStateAdmission.Proposal proposal = begin(state(candidate, x, y, preferredX, upstream, false))) {
            boolean accepted = events.propose("commit", editor.text());
            candidate = editor.copy();
            candidate.commit(accepted, controlled && !candidate.text().equals(candidate.committed()));
            editor = candidate;
            edited = !accepted;
            rejected = false;
            revision++;
            proposal.commit(captureState());
            requestFrame();
        } catch (GuiViewStateAdmission.Refusal refused) {
            rejected = true;
            requestFrame();
        }
    }

    private GuiViewStateAdmission.Proposal begin(Map<String, Object> state) {
        if (admission == null) {
            return GuiViewStateAdmission.UNRESTRICTED;
        }
        try {
            return admission.begin(state);
        } catch (IllegalArgumentException refused) {
            throw new GuiViewStateAdmission.Refusal(refused);
        }
    }

    @Override
    public void admitStateWith(GuiViewStateAdmission admission) {
        if (closed || this.admission != null) {
            throw new IllegalStateException("Install textarea admission once before activation");
        }
        this.admission = Objects.requireNonNull(admission, "textarea state admission");
    }

    @Override
    public Map<String, Object> captureState() {
        return editor == null ? Collections.emptyMap() : state(editor, x, y, preferredX, upstream, edited);
    }

    @Override
    public Map<String, Object> projectState(Map<String, Object> values, Predicate<String> controlled,
            Map<String, Object> current) {
        GuiTextEditorModel model = projectedEditor(values, controlled.test("text"), current);
        boolean compatible = compatible(current, model);
        return state(model, compatible ? integer(current, "scrollX", x) : 0,
                compatible ? integer(current, "scrollY", y) : 0,
                compatible ? integer(current, "preferredX", preferredX) : -1,
                compatible && Boolean.TRUE.equals(current.get("upstream")),
                compatible && Boolean.TRUE.equals(current.get("edited")));
    }

    @Override
    public Runnable prepareStateRestore(Map<String, Object> state) {
        if (closed || editor == null) {
            throw new IllegalStateException("Cannot restore a closed/unpublished textarea");
        }
        for (String name : state.keySet()) {
            if (!Arrays.asList("editor", "scrollX", "scrollY", "preferredX", "upstream", "edited").contains(name)) {
                throw new IllegalArgumentException("Unknown textarea state field: " + name);
            }
        }
        GuiTextEditorModel candidate = new GuiTextEditorModel(editor.committed(), editor.maximumLength(), true);
        boolean compatible = !state.containsKey("editor") || candidate.restore(savedEditor(state), controlled);
        int x = offset(state, "scrollX", 0);
        int y = offset(state, "scrollY", 0);
        int preferred = offset(state, "preferredX", -1);
        boolean upstream = flag(state, "upstream");
        boolean edited = flag(state, "edited");
        long expected = revision;
        return () -> {
            if (closed || revision != expected) {
                throw new IllegalStateException("Textarea restore became stale");
            }
            editor = candidate;
            this.x = compatible ? x : 0;
            this.y = compatible ? y : 0;
            preferredX = compatible ? preferred : -1;
            this.upstream = compatible && upstream;
            this.edited = compatible && edited;
            revision++;
            requestLayout();
        };
    }

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            releasePositionHandler.run();
            admission = null;
            editor = null;
            layout = null;
            layoutFont = null;
            dragLayout = null;
            dragTransform = null;
            values = Collections.emptyMap();
            events = (event, value) -> false;
            super.close();
        }
    }

    private boolean wrap() {
        return !Boolean.FALSE.equals(values.get("wrap"));
    }

    private int inset(Map<String, Object> values) {
        return integer(values, "padding", 0) + style(0).number("padding", 4);
    }

    private static int[] reveal(GuiTextEditorModel model, GuiTextAreaLayout layout, Rect viewport, int x, int y,
            boolean upstream) {
        int row = layout.rowAt(model.caret(), upstream);
        int left = layout.xAt(row, model.caret());
        int top = row * layout.lineHeight;
        x = left < x ? left : left + 1 > x + viewport.getWidth() ? left + 1 - viewport.getWidth() : x;
        y = top < y
                ? top
                : top + layout.lineHeight > y + viewport.getHeight()
                        ? top + layout.lineHeight - viewport.getHeight()
                        : y;
        return new int[]{clamp(x, Math.max(0, layout.width - viewport.getWidth())),
                clamp(y, Math.max(0, layout.height - viewport.getHeight()))};
    }

    private static int clamp(int value, int maximum) {
        return Math.max(0, Math.min(maximum, value));
    }

    private static int outside(int value, int minimum, int maximum) {
        return value < minimum ? value - minimum : value >= maximum ? value - maximum + 1 : 0;
    }

    private static int dimension(Map<String, Object> values, String preferred, String minimum, int fallback) {
        double value = ((Number) values.getOrDefault(preferred, 0)).doubleValue();
        return Math.max((int) Math.round(((Number) values.getOrDefault(minimum, 0)).doubleValue()),
                value > 0 ? (int) Math.round(value) : fallback);
    }

    private static int integer(Map<String, Object> values, String name, int fallback) {
        Object value = values.get(name);
        return value instanceof Number ? ((Number) value).intValue() : fallback;
    }

    private static int offset(Map<String, Object> state, String name, int minimum) {
        Object value = state.getOrDefault(name, minimum);
        double number = value instanceof Number ? ((Number) value).doubleValue() : Double.NaN;
        if (!Double.isFinite(number) || number != Math.rint(number) || number < minimum || number > 1000000000) {
            throw new IllegalArgumentException("Invalid textarea offset: " + name);
        }
        return (int) number;
    }

    private static boolean flag(Map<String, Object> state, String name) {
        Object value = state.getOrDefault(name, false);
        if (!(value instanceof Boolean)) {
            throw new IllegalArgumentException("Invalid textarea flag: " + name);
        }
        return (Boolean) value;
    }

    @SuppressWarnings("unchecked")
    private static GuiTextEditorModel.Snapshot savedEditor(Map<String, Object> state) {
        Object value = state.get("editor");
        if (!(value instanceof Map)) {
            throw new IllegalArgumentException("Textarea editor state requires a detached record");
        }
        return GuiTextEditorModel.Snapshot.read((Map<String, Object>) value);
    }

    private static Map<String, Object> state(GuiTextEditorModel editor, int x, int y, int preferredX, boolean upstream,
            boolean edited) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("editor", editor.snapshot().values());
        result.put("scrollX", x);
        result.put("scrollY", y);
        result.put("preferredX", preferredX);
        result.put("upstream", upstream);
        result.put("edited", edited);
        return Collections.unmodifiableMap(result);
    }
}
