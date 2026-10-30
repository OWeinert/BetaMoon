package betamoon.gui.drawing;

import betamoon.gui.framework.GuiContext;
import betamoon.gui.framework.GuiElement;
import betamoon.gui.framework.GuiFrameContent;
import betamoon.gui.framework.GuiGeometry.Constraints;
import betamoon.gui.framework.GuiGeometry.Rect;
import betamoon.gui.framework.GuiGeometry.Size;
import betamoon.gui.framework.GuiInputEvent;
import betamoon.gui.framework.GuiViewportLayout;
import betamoon.gui.graphics.GraphicsTransform;
import betamoon.gui.presentation.GuiPresentationNode;
import betamoon.gui.presentation.GuiViewAdapter;
import betamoon.gui.presentation.GuiViewStateAdmission;
import betamoon.luaapi.utils.LuaCallbackScope;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Open leaf adapter: prepared Java paint/hit geometry with ordinary scene input
 * and lifecycle.
 */
public class GuiPaintView extends GuiElement implements GuiViewAdapter, GuiViewportLayout {
    @FunctionalInterface
    public interface Source {
        GuiPaintProgram prepare(int width, int height, Map<String, Object> properties);

        default void accept(GuiPaintProgram program) {
        }

    }

    @FunctionalInterface
    public interface InputHandler {
        Decision input(GuiInputEvent event, String region);
    }

    public static final class Decision {
        public static final Decision IGNORE = new Decision(false, false, false, false);
        public static final Decision CONSUME = new Decision(true, false, false, false);
        public final boolean consumed;
        public final boolean capture;
        public final boolean release;
        public final boolean focus;

        public Decision(boolean consumed, boolean capture, boolean release, boolean focus) {
            if ((capture || release || focus) && !consumed || capture && release) {
                throw new IllegalArgumentException(
                        "Input ownership operations require consumption and cannot conflict");
            }
            this.consumed = consumed;
            this.capture = capture;
            this.release = release;
            this.focus = focus;
        }
    }

    private Source source;
    private InputHandler input;
    private Consumer<Throwable> failures;
    private GuiPresentationNode node;
    private Map<String, Object> targets = Collections.emptyMap();
    private Map<String, Object> accepted = Collections.emptyMap();
    private GuiPaintProgram program;
    private static final GuiFrameContent EMPTY_FRAME = (x, y) -> false;
    private GuiPaintProgram.Frame lastFrame;
    private Throwable pendingFrameFailure;
    private boolean frameFailureReported;
    private int preparedWidth = -1;
    private int preparedHeight = -1;
    private boolean dirty = true;
    private boolean closed;
    private boolean useCurrentValues;
    private boolean hovered;
    private boolean pressed;
    private double width;
    private double height;
    private double minimumWidth;
    private double minimumHeight;
    private double x;
    private double y;
    private double anchorX = 0.5;
    private double anchorY = 0.5;
    private boolean fill;

    public GuiPaintView(Source source, InputHandler input, Consumer<Throwable> failures, boolean focusable) {
        this.source = Objects.requireNonNull(source, "prepared paint source");
        this.input = Objects.requireNonNull(input, "custom input handler");
        this.failures = Objects.requireNonNull(failures, "custom paint failure reporter");
        setFocusable(focusable);
    }

    @Override
    public GuiElement element() {
        return this;
    }

    public void invalidatePaint() {
        if (!closed) {
            dirty = true;
        }
    }

    @Override
    public void publish(GuiPresentationNode node) {
        this.node = node;
        Map<String, Object> next = new LinkedHashMap<>();
        for (String name : node.definition.properties.descriptors().keySet()) {
            next.put(name, node.target(name));
        }
        if (!targets.equals(next)) {
            targets = Collections.unmodifiableMap(next);
            dirty = true;
        }
        if (width != node.number("width", 0) || height != node.number("height", 0)
                || minimumWidth != node.number("minWidth", 0) || minimumHeight != node.number("minHeight", 0)
                || x != node.number("x", 0) || y != node.number("y", 0) || anchorX != node.number("anchorX", 0.5)
                || anchorY != node.number("anchorY", 0.5) || fill != Boolean.TRUE.equals(node.get("fill"))) {
            requestLayout();
        }
        width = node.number("width", 0);
        height = node.number("height", 0);
        minimumWidth = node.number("minWidth", 0);
        minimumHeight = node.number("minHeight", 0);
        x = node.number("x", 0);
        y = node.number("y", 0);
        anchorX = node.number("anchorX", 0.5);
        anchorY = node.number("anchorY", 0.5);
        fill = Boolean.TRUE.equals(node.get("fill"));
        setEnabled(!Boolean.FALSE.equals(node.get("enabled")));
        setOpacity(node.number("opacity", 1));
        setVisualTransform(new GraphicsTransform(node.number("translateX", 0), node.number("translateY", 0),
                node.number("scaleX", 1), node.number("scaleY", 1)));
        requestFrame();
    }

    @Override
    protected Size measureElement(GuiContext context, Constraints constraints) {
        return constraints
                .constrain(new Size(Math.max((int) Math.round(minimumWidth), width > 0 ? (int) Math.round(width) : 16),
                        Math.max((int) Math.round(minimumHeight), height > 0 ? (int) Math.round(height) : 16)));
    }

    @Override
    public Rect viewportBounds(GuiContext context, Rect viewport) {
        int width = Math.min(16384, fill ? viewport.getWidth() : getMeasuredSize().getWidth());
        int height = Math.min(16384, fill ? viewport.getHeight() : getMeasuredSize().getHeight());
        return Rect.fromPositionAndSize(
                viewport.getLeft() + (int) Math.round((viewport.getWidth() - width) * anchorX + x),
                viewport.getTop() + (int) Math.round((viewport.getHeight() - height) * anchorY + y), width, height);
    }

    @Override
    protected void prepareVisualsElement(GuiContext context) {
        if (!closed && pendingFrameFailure != null) {
            Throwable failure = pendingFrameFailure;
            pendingFrameFailure = null;
            failures.accept(failure);
        }
        if (closed || !dirty && preparedWidth == getWidth() && preparedHeight == getHeight()) {
            return;
        }
        dirty = false;
        preparedWidth = getWidth();
        preparedHeight = getHeight();
        try (LuaCallbackScope scope = LuaCallbackScope.binding()) {
            GuiPaintProgram candidate = Objects.requireNonNull(source.prepare(getWidth(), getHeight(), targets),
                    "prepared paint source result");
            candidate.sample(getWidth(), getHeight(), targets);
            source.accept(candidate);
            program = candidate;
            frameFailureReported = false;
            accepted = targets;
            useCurrentValues = true;
            requestFrame();
        } catch (VirtualMachineError | ThreadDeath fatal) {
            throw fatal;
        } catch (Throwable failure) {
            useCurrentValues = false;
            failures.accept(failure);
            if (program == null) {
                throw new IllegalArgumentException("Custom view has no usable prepared paint", failure);
            }
        }
    }

    @Override
    protected GuiFrameContent sampleFrameContent() {
        if (closed || program == null) {
            return null;
        }
        try {
            lastFrame = program.sample(getWidth(), getHeight(),
                    useCurrentValues && node != null ? node.values() : accepted, retainFrameProperties());
            frameFailureReported = false;
        } catch (VirtualMachineError | ThreadDeath fatal) {
            throw fatal;
        } catch (Throwable failure) {
            if (!frameFailureReported) {
                pendingFrameFailure = failure;
                frameFailureReported = true;
            }
            if (lastFrame != null) {
                try {
                    lastFrame.requireActive();
                } catch (RuntimeException expired) {
                    lastFrame = null;
                }
            }
        }
        return lastFrame == null ? EMPTY_FRAME : lastFrame;
    }

    @Override
    protected void renderElement(GuiContext context) {
        GuiFrameContent frame = context.getFrameContent();
        if (frame instanceof GuiPaintProgram.Frame) {
            GuiPaintProgram.Frame prepared = (GuiPaintProgram.Frame) frame;
            hovered = isHovered(context)
                    && prepared.contains(context.getMouseX() - getLeft(), context.getMouseY() - getTop());
            context.getRenderer().pushClip(getBounds());
            try {
                paintFrame(context, prepared);
            } finally {
                context.getRenderer().popClip();
            }
        }
    }

    protected boolean retainFrameProperties() {
        return false;
    }

    protected void paintFrame(GuiContext context, GuiPaintProgram.Frame frame) {
        frame.paint(context, getLeft(), getTop());
    }

    protected Map<String, Object> publishedValues() {
        return node == null ? accepted : node.values();
    }

    @Override
    protected boolean onInput(GuiContext context, GuiInputEvent event) {
        if (closed) {
            return false;
        }
        GuiPaintProgram.Frame frame = context.getFrameContent() instanceof GuiPaintProgram.Frame
                ? (GuiPaintProgram.Frame) context.getFrameContent()
                : lastFrame;
        String region = frame == null ? null : frame.hit(event.getMouseX() - getLeft(), event.getMouseY() - getTop());
        Decision decision;
        try {
            decision = Objects.requireNonNull(input.input(event, region), "custom input result");
        } catch (GuiViewStateAdmission.Refusal refused) {
            pressed = false;
            context.cancelGesturesIn(this);
            releasePointer(context);
            return true;
        }
        if (closed) {
            return decision.consumed;
        }
        if (decision.focus) {
            requestFocus(context);
        }
        if (decision.capture) {
            pressed = true;
            capturePointer(context);
        }
        if (decision.release || event.getType() == GuiInputEvent.Type.POINTER_CANCEL
                || event.getType() == GuiInputEvent.Type.POINTER_UP) {
            pressed = false;
            releasePointer(context);
        }
        return decision.consumed;
    }

    @Override
    public boolean interaction(String name) {
        switch (name) {
            case "hovered":
                return hovered;
            case "pressed":
                return pressed;
            case "focused":
                return isFocused();
            case "disabled":
                return !isEnabled();
            default:
                return false;
        }
    }

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            source = null;
            input = null;
            failures = null;
            node = null;
            program = null;
            lastFrame = null;
            pendingFrameFailure = null;
            targets = Collections.emptyMap();
            accepted = Collections.emptyMap();
            setEnabled(false);
        }
    }
}
