package betamoon.gui.widget;

import betamoon.gui.framework.GuiContainer;
import betamoon.gui.framework.GuiContext;
import betamoon.gui.framework.GuiElement;
import betamoon.gui.framework.GuiGeometry.Constraints;
import betamoon.gui.framework.GuiGeometry.Size;
import betamoon.gui.framework.GuiInputEvent;
import betamoon.gui.framework.GuiMeasurementPreview;
import betamoon.gui.presentation.GuiPresentationNode;
import betamoon.gui.presentation.GuiViewAdapter;
import betamoon.gui.presentation.GuiViewPublication;
import betamoon.gui.presentation.GuiViewStateAdmission;
import betamoon.gui.style.GuiStyleSheet;
import betamoon.gui.style.GuiVisualTemplate;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * Authored content mounts directly into the shared Java viewport; only detached
 * positions are retained.
 */
public final class GuiScrollControl extends GuiStyledView {
    private final GuiContainer content = new GuiContainer();
    private final GuiScrollView scroll = new GuiScrollView(content, GuiScrollView.Policy.VERTICAL);
    private final Runnable releasePositionHandler;
    private Events events;
    private Map<String, Object> properties = Collections.emptyMap();
    private boolean xControlled;
    private boolean yControlled;
    private boolean published;
    private boolean localState;
    private boolean closed;
    private long revision;
    private GuiViewStateAdmission admission;

    public GuiScrollControl(GuiStyleSheet theme, GuiVisualTemplate template, Events events) {
        super("scroll", theme, template, events);
        this.events = Objects.requireNonNull(events, "scroll events");
        add(scroll);
        setFocusable(true);
        releasePositionHandler = scroll.handlePositionWith(this::propose);
    }

    public GuiScrollView viewport() {
        return scroll;
    }

    @Override
    public GuiContainer childContainer() {
        return content;
    }

    @Override
    public void validateValues(Map<String, Object> values) {
        super.validateValues(values);
        policy(values);
    }

    @Override
    public void publish(GuiPresentationNode node) {
        super.publish(node);
        publishScroll(node.values(), node::controlled);
    }

    @Override
    public GuiScrollControl configure(Map<String, Object> values) {
        super.configure(values);
        published = false;
        publishScroll(GuiBuiltInViews.properties("scroll").withDefaults(values).baseline(), name -> false);
        return this;
    }

    private void publishScroll(Map<String, Object> values, Predicate<String> controlled) {
        requireOpen();
        boolean nextX = controlled.test("scrollX");
        boolean nextY = controlled.test("scrollY");
        localState |= xControlled && !nextX || yControlled && !nextY;
        properties = values;
        scroll.setPolicy(policy(values));
        scroll.setScrollbarsVisible(!Boolean.FALSE.equals(values.get("scrollbars")));
        int x = !published || nextX ? integer(values, "scrollX") : scroll.getScrollX();
        int y = !published || nextY ? integer(values, "scrollY") : scroll.getScrollY();
        xControlled = nextX;
        yControlled = nextY;
        localState |= !nextX && x != 0 || !nextY && y != 0;
        scroll.publishScrollPosition(x, y);
        published = true;
        revision++;
        requestLayout();
    }

    @Override
    protected Size measureElement(GuiContext context, Constraints constraints) {
        if (content.getChildren().size() != 1) {
            throw new IllegalArgumentException("A scroll control requires exactly one concrete child tree");
        }
        GuiViewPublication pending = GuiMeasurementPreview.values(this);
        Map<String, Object> values = pending == null ? properties : pending.values;
        int padding = integer(values, "padding");
        double preferredWidth = ((Number) values.getOrDefault("width", 0)).doubleValue();
        double preferredHeight = ((Number) values.getOrDefault("height", 0)).doubleValue();
        int width = preferredWidth == 0
                ? constraints.getMaxWidth()
                : Math.min(Math.max(rounded(values, "minWidth"), (int) Math.round(preferredWidth)),
                        constraints.getMaxWidth());
        int height = preferredHeight == 0
                ? constraints.getMaxHeight()
                : Math.min(Math.max(rounded(values, "minHeight"), (int) Math.round(preferredHeight)),
                        constraints.getMaxHeight());
        int viewportWidth = Math.max(0, width - padding * 2);
        int viewportHeight = Math.max(0, height - padding * 2);
        GuiScrollView.Policy policy = policy(values);
        int contentWidth = integer(values, "contentWidth");
        int contentHeight = integer(values, "contentHeight");
        int maximumWidth = contentWidth > 0
                ? Math.max(viewportWidth, contentWidth)
                : policy == GuiScrollView.Policy.VERTICAL ? viewportWidth : 16385;
        int maximumHeight = contentHeight > 0
                ? Math.max(viewportHeight, contentHeight)
                : policy == GuiScrollView.Policy.HORIZONTAL ? viewportHeight : 16385;
        Size measured = content.measure(context, Constraints.upTo(maximumWidth, maximumHeight));
        if (measured.getWidth() > 16384 || measured.getHeight() > 16384) {
            throw new IllegalArgumentException(
                    "Measured scroll content exceeds 16384 pixels; use a virtual collection");
        }
        int resolvedWidth = preferredWidth == 0
                ? Math.max(20, Math.min(width, Math.max(contentWidth, measured.getWidth()) + padding * 2))
                : width;
        int resolvedHeight = preferredHeight == 0
                ? Math.max(20, Math.min(height, Math.max(contentHeight, measured.getHeight()) + padding * 2))
                : height;
        return constraints.constrain(new Size(Math.max(rounded(values, "minWidth"), resolvedWidth),
                Math.max(rounded(values, "minHeight"), resolvedHeight)));
    }

    @Override
    protected void arrangeChildren(GuiContext context) {
        scroll.setContentSize(Math.max(integer(properties, "contentWidth"), content.getMeasuredSize().getWidth()),
                Math.max(integer(properties, "contentHeight"), content.getMeasuredSize().getHeight()));
        super.arrangeChildren(context);
    }

    @Override
    protected boolean onInput(GuiContext context, GuiInputEvent event) {
        if (closed) {
            return false;
        }
        return event.getType() == GuiInputEvent.Type.KEY_TYPED && scroll.onInput(context, event);
    }

    private void propose(int x, int y) {
        requireOpen();
        boolean retain = localState || !xControlled && x != scroll.getScrollX()
                || !yControlled && y != scroll.getScrollY();
        Map<String, Object> next = state(xControlled ? scroll.getScrollX() : x, yControlled ? scroll.getScrollY() : y,
                xControlled, yControlled, retain);
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("scrollX", x);
        event.put("scrollY", y);
        try (GuiViewStateAdmission.Proposal proposal = admission == null
                ? GuiViewStateAdmission.UNRESTRICTED
                : admission.begin(next)) {
            if (!events.propose("change", Collections.unmodifiableMap(event))) {
                return;
            }
            scroll.publishScrollPosition(xControlled ? scroll.getScrollX() : x, yControlled ? scroll.getScrollY() : y);
            localState = retain;
            revision++;
            proposal.commit(captureState());
        } catch (GuiViewStateAdmission.Refusal refused) {
            // Keep the accepted viewport; an eligible outer viewport can handle an unmoving
            // wheel gesture.
        }
    }

    @Override
    public Map<String, Object> captureState() {
        return state(scroll.getScrollX(), scroll.getScrollY(), xControlled, yControlled, localState);
    }

    @Override
    public Map<String, Object> projectState(Map<String, Object> values, Predicate<String> controlled,
            Map<String, Object> current) {
        int x = !published
                ? integer(values, "scrollX")
                : ((Number) current.getOrDefault("scrollX", scroll.getScrollX())).intValue();
        int y = !published
                ? integer(values, "scrollY")
                : ((Number) current.getOrDefault("scrollY", scroll.getScrollY())).intValue();
        boolean retain = localState || !current.isEmpty() || xControlled && !controlled.test("scrollX")
                || yControlled && !controlled.test("scrollY") || !published && (x != 0 || y != 0);
        return state(x, y, controlled.test("scrollX"), controlled.test("scrollY"), retain);
    }

    @Override
    public void admitStateWith(GuiViewStateAdmission admission) {
        requireOpen();
        if (this.admission != null) {
            throw new IllegalStateException("Install scroll admission once");
        }
        this.admission = Objects.requireNonNull(admission, "scroll state admission");
    }

    @Override
    public Runnable prepareStateRestore(Map<String, Object> values) {
        requireOpen();
        for (String key : values.keySet()) {
            if (!key.equals("scrollX") && !key.equals("scrollY")) {
                throw new IllegalArgumentException("Unknown scroll state field: " + key);
            }
        }
        int x = restored(values, "scrollX");
        int y = restored(values, "scrollY");
        long expected = revision;
        return () -> {
            requireOpen();
            if (revision != expected) {
                throw new IllegalStateException("Scroll candidate changed before state restoration");
            }
            scroll.publishScrollPosition(xControlled ? scroll.getScrollX() : x, yControlled ? scroll.getScrollY() : y);
            localState = !values.isEmpty();
            revision++;
        };
    }

    @Override
    public void retainFrom(GuiViewAdapter previous) {
        if (previous instanceof GuiScrollControl && !previous.captureState().isEmpty()) {
            prepareStateRestore(previous.captureState()).run();
        }
    }

    @Override
    protected Object templateValue(String name, boolean targets) {
        if (name.equals("scrollX")) {
            return scroll.getScrollX();
        }
        if (name.equals("scrollY")) {
            return scroll.getScrollY();
        }
        return super.templateValue(name, targets);
    }

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            releasePositionHandler.run();
            admission = null;
            properties = Collections.emptyMap();
            events = (event, value) -> false;
            super.close();
        }
    }

    private void requireOpen() {
        if (closed) {
            throw new IllegalStateException("Scroll control has closed");
        }
    }

    private static Map<String, Object> state(int x, int y, boolean xControlled, boolean yControlled, boolean retain) {
        if (!retain) {
            return Collections.emptyMap();
        }
        Map<String, Object> values = new LinkedHashMap<>();
        if (!xControlled) {
            values.put("scrollX", x);
        }
        if (!yControlled) {
            values.put("scrollY", y);
        }
        return Collections.unmodifiableMap(values);
    }

    private static int restored(Map<String, Object> values, String name) {
        Object value = values.getOrDefault(name, 0);
        if (!(value instanceof Number) || !Double.isFinite(((Number) value).doubleValue())
                || ((Number) value).doubleValue() != Math.rint(((Number) value).doubleValue())
                || ((Number) value).doubleValue() < 0 || ((Number) value).doubleValue() > 16384) {
            throw new IllegalArgumentException("Saved scroll positions require integers 0..16384");
        }
        return ((Number) value).intValue();
    }

    private static int rounded(Map<String, Object> values, String name) {
        return (int) Math.round(((Number) values.getOrDefault(name, 0)).doubleValue());
    }

    private static int integer(Map<String, Object> values, String name) {
        return ((Number) values.getOrDefault(name, 0)).intValue();
    }

    private static GuiScrollView.Policy policy(Map<String, Object> values) {
        String direction = (String) values.getOrDefault("direction", "vertical");
        switch (direction) {
            case "vertical":
                return GuiScrollView.Policy.VERTICAL;
            case "horizontal":
                return GuiScrollView.Policy.HORIZONTAL;
            case "both":
                return GuiScrollView.Policy.BOTH;
            default:
                throw new IllegalArgumentException("Scroll direction requires vertical, horizontal or both");
        }
    }
}
