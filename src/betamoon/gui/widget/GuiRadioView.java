package betamoon.gui.widget;

import betamoon.gui.control.GuiSelectionOptions;
import betamoon.gui.framework.GuiContext;
import betamoon.luaapi.utils.LuaCallbackScope;
import betamoon.gui.framework.GuiGeometry.Constraints;
import betamoon.gui.framework.GuiGeometry.Rect;
import betamoon.gui.framework.GuiGeometry.Size;
import betamoon.gui.framework.GuiInputEvent;
import betamoon.gui.framework.GuiFrameContent;
import betamoon.gui.graphics.GraphicsTransform;
import betamoon.gui.framework.GuiMeasurementPreview;
import betamoon.gui.presentation.GuiPresentationNode;
import betamoon.gui.presentation.GuiViewPublication;
import betamoon.gui.presentation.GuiViewStateAdmission;
import betamoon.gui.style.GuiControlStyle;
import betamoon.gui.style.GuiStyleSheet;
import betamoon.gui.style.GuiVisualTemplate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import org.lwjgl.input.Keyboard;

/**
 * A keyed radio group is one retained focus stop with bounded, shared option
 * geometry.
 */
public final class GuiRadioView extends GuiStyledView {
    private GuiSelectionOptions options = new GuiSelectionOptions(Collections.emptyList());
    private Map<String, Object> values = Collections.emptyMap();
    private Events events;
    private GuiViewStateAdmission admission;
    private String selected = "";
    private String pressed;
    private List<Object> pressedGeometry;
    private GraphicsTransform pressedTransform;
    private long revision;
    private long pressedRevision;
    private boolean controlled;
    private boolean published;
    private boolean closed;
    private boolean revealPending;

    public GuiRadioView(GuiStyleSheet theme, GuiVisualTemplate template, Events events) {
        super("radio", theme, template, events);
        this.events = events;
    }

    @Override
    public void validateValues(Map<String, Object> values) {
        super.validateValues(values);
        new GuiSelectionOptions(values.get("options"));
        GuiSelectionOptions.key(values.get("selectedKey"), true);
        if (!Arrays.asList("vertical", "horizontal").contains(values.get("orientation"))) {
            throw new IllegalArgumentException("Radio orientation requires vertical or horizontal");
        }
    }

    @Override
    public GuiRadioView configure(Map<String, Object> values) {
        super.configure(values);
        published = false;
        publishRadio(GuiBuiltInViews.properties("radio").withDefaults(values).baseline(), name -> false);
        return this;
    }

    @Override
    public void publish(GuiPresentationNode node) {
        super.publish(node);
        publishRadio(node.values(), node::controlled);
    }

    private void publishRadio(Map<String, Object> next, Predicate<String> controlled) {
        boolean nextControlled = controlled.test("selectedKey");
        GuiSelectionOptions nextOptions = new GuiSelectionOptions(next.get("options"));
        String nextSelection = nextOptions
                .selection(!published || nextControlled ? (String) next.get("selectedKey") : selected);
        if (!Objects.equals(values.get("options"), next.get("options")) || !selected.equals(nextSelection)
                || this.controlled != nextControlled) {
            revision++;
        }
        revealPending |= !selected.equals(nextSelection);
        values = next;
        options = nextOptions;
        selected = nextSelection;
        this.controlled = nextControlled;
        published = true;
        setFocusable(!options.navigate("", 1, true).isEmpty());
        requestLayout();
    }

    @Override
    protected void updateElement(GuiContext context) {
        super.updateElement(context);
        if (revealPending && LuaCallbackScope.uiStateWriteAllowed()) {
            revealPending = false;
            if (isFocused()) {
                context.revealFocusedRegion();
            }
        }
    }

    @Override
    protected Size measureElement(GuiContext context, Constraints constraints) {
        Size base = super.measureElement(context, constraints);
        GuiViewPublication pending = GuiMeasurementPreview.values(this);
        Map<String, Object> values = pending == null ? this.values : pending.values;
        GuiSelectionOptions options = pending == null ? this.options : new GuiSelectionOptions(values.get("options"));
        GuiControlStyle style = style(0);
        int labelWidth = 0;
        for (GuiSelectionOptions.Option option : options.options) {
            labelWidth = Math.max(labelWidth, style.font().width(context, option.label));
        }
        int count = options.options.size();
        int padding = integer(values, "padding", 0);
        int gap = integer(values, "gap", 0);
        int height = integer(values, "optionHeight", 20);
        boolean horizontal = "horizontal".equals(values.get("orientation"));
        long width = (long) (horizontal ? count : Math.min(1, count)) * (labelWidth + 24)
                + (horizontal ? Math.max(0, count - 1) * gap : 0) + padding * 2;
        height = (horizontal ? Math.min(1, count) : count) * height + (horizontal ? 0 : Math.max(0, count - 1) * gap)
                + padding * 2;
        return constraints.constrain(new Size(
                positive(values, "width")
                        ? base.getWidth()
                        : (int) Math.min(Integer.MAX_VALUE, Math.max(base.getWidth(), width)),
                positive(values, "height") ? base.getHeight() : Math.max(base.getHeight(), height)));
    }

    public GuiSelectionOptions options() {
        return options;
    }

    public String selectedKey() {
        return selected;
    }

    public String focusKey() {
        return selected.isEmpty() ? options.navigate("", 1, true) : selected;
    }

    public Rect optionBounds(int index) {
        if (index < 0 || index >= options.options.size()) {
            return Rect.EMPTY;
        }
        int padding = integer(values, "padding", 0);
        int gap = integer(values, "gap", 0);
        int height = integer(values, "optionHeight", 20);
        int left = getLeft() + padding;
        int top = getTop() + padding;
        int width = Math.max(0, getWidth() - padding * 2);
        if ("horizontal".equals(values.get("orientation"))) {
            int count = options.options.size();
            int available = Math.max(0, width - Math.max(0, count - 1) * gap);
            int start = available * index / count;
            width = available * (index + 1) / count - start;
            left += start + index * gap;
        } else {
            top += index * (height + gap);
        }
        return Rect.fromPositionAndSize(left, top, width, height).intersect(getBounds());
    }

    public List<Map<String, Object>> templateOptions() {
        List<Map<String, Object>> result = new ArrayList<>();
        String focus = focusKey();
        for (int i = 0; i < options.options.size(); i++) {
            GuiSelectionOptions.Option option = options.options.get(i);
            Rect bounds = optionBounds(i);
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("key", option.key);
            value.put("x", bounds.getLeft() - getLeft());
            value.put("y", bounds.getTop() - getTop());
            value.put("width", bounds.getWidth());
            value.put("height", bounds.getHeight());
            value.put("focusTarget", option.key.equals(focus));
            result.add(Collections.unmodifiableMap(value));
        }
        return Collections.unmodifiableList(result);
    }

    @Override
    protected Rect focusBounds() {
        String focus = focusKey();
        for (int i = 0; i < options.options.size(); i++) {
            if (options.options.get(i).key.equals(focus)) {
                return optionBounds(i);
            }
        }
        return getBounds();
    }

    @Override
    protected Object templateValue(String name, boolean targets) {
        return name.equals("selectedKey") ? selected : super.templateValue(name, targets);
    }

    @Override
    protected boolean onInput(GuiContext context, GuiInputEvent event) {
        if (event.getType() == GuiInputEvent.Type.POINTER_CANCEL) {
            boolean captured = pressed != null;
            pressed = null;
            pressedGeometry = null;
            pressedTransform = null;
            releasePointer(context);
            return captured;
        }
        if (closed || !isFocusable()) {
            return false;
        }
        if (event.getType() == GuiInputEvent.Type.KEY_TYPED && isFocused()) {
            String next;
            switch (event.getKeyCode()) {
                case Keyboard.KEY_LEFT:
                case Keyboard.KEY_UP:
                    next = options.navigate(selected, -1, false);
                    break;
                case Keyboard.KEY_RIGHT:
                case Keyboard.KEY_DOWN:
                    next = options.navigate(selected, 1, false);
                    break;
                case Keyboard.KEY_HOME:
                    next = options.navigate("", 1, true);
                    break;
                case Keyboard.KEY_END:
                    next = options.navigate("", -1, true);
                    break;
                case Keyboard.KEY_SPACE:
                case Keyboard.KEY_RETURN:
                case Keyboard.KEY_NUMPADENTER:
                    next = focusKey();
                    break;
                default:
                    return false;
            }
            select(next);
            context.revealFocusedRegion();
            return true;
        }
        if (event.getType() == GuiInputEvent.Type.POINTER_DOWN && event.getButton() == 0) {
            RadioFrame frame = (RadioFrame) context.getFrameContent();
            if (frame == null || frame.revision != revision || !frame.geometry.equals(geometry())) {
                return true;
            }
            String key = frame.hit(event.getMouseX(), event.getMouseY());
            requestFocus(context);
            if (key != null) {
                pressed = key;
                pressedRevision = revision;
                pressedGeometry = frame.geometry;
                pressedTransform = context.getElementTransform();
                capturePointer(context);
            }
            return contains(event.getMouseX(), event.getMouseY());
        }
        if (event.getType() == GuiInputEvent.Type.POINTER_UP && event.getButton() == 0 && pressed != null) {
            String key = pressed;
            pressed = null;
            releasePointer(context);
            if (pressedRevision == revision && Objects.equals(pressedGeometry, geometry())
                    && sameTransform(pressedTransform, context.getElementTransform())
                    && key.equals(hit(event.getMouseX(), event.getMouseY()))) {
                select(key);
            }
            pressedGeometry = null;
            pressedTransform = null;
            return true;
        }
        return pressed != null && event.getType() == GuiInputEvent.Type.POINTER_DRAG;
    }

    @Override
    protected GuiFrameContent sampleFrameContent() {
        List<Rect> bounds = new ArrayList<>();
        for (int i = 0; i < options.options.size(); i++) {
            bounds.add(optionBounds(i));
        }
        return new RadioFrame(revision, geometry(), options, bounds);
    }

    private static final class RadioFrame implements GuiFrameContent {
        private final long revision;
        private final List<Object> geometry;
        private final GuiSelectionOptions options;
        private final List<Rect> bounds;

        private RadioFrame(long revision, List<Object> geometry, GuiSelectionOptions options, List<Rect> bounds) {
            this.revision = revision;
            this.geometry = geometry;
            this.options = options;
            this.bounds = Collections.unmodifiableList(bounds);
        }

        public boolean contains(double x, double y) {
            return true;
        }

        private String hit(int x, int y) {
            for (int i = 0; i < bounds.size(); i++) {
                if (options.options.get(i).enabled && bounds.get(i).contains(x, y)) {
                    return options.options.get(i).key;
                }
            }
            return null;
        }
    }

    private static boolean sameTransform(GraphicsTransform a, GraphicsTransform b) {
        return a != null && a.x == b.x && a.y == b.y && a.scaleX == b.scaleX && a.scaleY == b.scaleY;
    }

    private String hit(int x, int y) {
        for (int i = 0; i < options.options.size(); i++) {
            GuiSelectionOptions.Option option = options.options.get(i);
            if (option.enabled && optionBounds(i).contains(x, y)) {
                return option.key;
            }
        }
        return null;
    }

    private List<Object> geometry() {
        return Arrays.asList(getBounds(), values.get("padding"), values.get("gap"), values.get("orientation"),
                values.get("optionHeight"), values.get("translateX"), values.get("translateY"), values.get("scaleX"),
                values.get("scaleY"));
    }

    private boolean select(String key) {
        if (key.isEmpty() || key.equals(selected)) {
            return true;
        }
        try (GuiViewStateAdmission.Proposal proposal = begin(controlled ? captureState() : state(key))) {
            if (!events.propose("change", key)) {
                return false;
            }
            if (!controlled) {
                selected = key;
                revision++;
                requestFrame();
            }
            proposal.commit(captureState());
            return true;
        } catch (GuiViewStateAdmission.Refusal refused) {
            return false;
        }
    }

    private GuiViewStateAdmission.Proposal begin(Map<String, Object> next) {
        if (admission == null) {
            return GuiViewStateAdmission.UNRESTRICTED;
        }
        try {
            return admission.begin(next);
        } catch (IllegalArgumentException refused) {
            throw new GuiViewStateAdmission.Refusal(refused);
        }
    }

    @Override
    public Map<String, Object> captureState() {
        return controlled ? Collections.emptyMap() : state(selected);
    }

    @Override
    public Map<String, Object> projectState(Map<String, Object> values, Predicate<String> controlled,
            Map<String, Object> current) {
        if (controlled.test("selectedKey")) {
            return Collections.emptyMap();
        }
        String next = !published
                ? (String) values.get("selectedKey")
                : (String) current.getOrDefault("selectedKey", selected);
        return state(new GuiSelectionOptions(values.get("options")).selection(next));
    }

    @Override
    public Runnable prepareStateRestore(Map<String, Object> state) {
        if (closed || state.size() > 1 || !state.isEmpty() && !state.containsKey("selectedKey")) {
            throw new IllegalArgumentException("Invalid radio selection snapshot");
        }
        String next = options.selection(GuiSelectionOptions.key(state.getOrDefault("selectedKey", selected), true));
        long expected = revision;
        return () -> {
            if (closed || revision != expected) {
                throw new IllegalStateException("Radio selection restore became stale");
            }
            if (!controlled && !next.equals(selected)) {
                selected = next;
                revision++;
                requestFrame();
            }
        };
    }

    @Override
    public void admitStateWith(GuiViewStateAdmission admission) {
        if (closed || this.admission != null) {
            throw new IllegalStateException("Install radio admission once before activation");
        }
        this.admission = Objects.requireNonNull(admission, "radio state admission");
    }

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            admission = null;
            pressed = null;
            pressedGeometry = null;
            pressedTransform = null;
            events = (event, value) -> false;
            selected = "";
            options = new GuiSelectionOptions(Collections.emptyList());
            values = Collections.emptyMap();
            super.close();
        }
    }

    private static Map<String, Object> state(String key) {
        return Collections.singletonMap("selectedKey", key);
    }

    private static boolean positive(Map<String, Object> values, String key) {
        Object value = values.get(key);
        return value instanceof Number && ((Number) value).doubleValue() > 0;
    }

    private static int integer(Map<String, Object> values, String key, int fallback) {
        Object value = values.get(key);
        return value instanceof Number ? ((Number) value).intValue() : fallback;
    }
}
