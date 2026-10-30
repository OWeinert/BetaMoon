package betamoon.gui.framework;

import betamoon.gui.framework.GuiGeometry.Constraints;
import betamoon.gui.framework.GuiGeometry.Rect;
import betamoon.gui.framework.GuiGeometry.Size;
import betamoon.gui.graphics.GraphicsTransform;
import java.util.List;

/** Base class for every retained GUI element. */
public abstract class GuiElement {
    private Rect bounds = Rect.EMPTY;
    private Rect inputBounds;
    private Size measuredSize = Size.ZERO;
    private GuiContainer parent;
    private GuiScene scene;
    private boolean visible = true;
    private boolean enabled = true;
    private boolean focusable;
    private boolean collapsed;
    private double opacity = 1;

    public final double getOpacity() {
        return opacity;
    }

    public final void setOpacity(double value) {
        if (!Double.isFinite(value) || value < 0 || value > 1) {
            throw new IllegalArgumentException("GUI opacity must be in 0..1");
        }
        if (opacity != value) {
            opacity = value;
            if (scene != null) {
                scene.requestFrame();
                if (value == 0) {
                    scene.releaseOwnership(this);
                }
            }
        }
    }

    public final boolean isCollapsed() {
        return collapsed;
    }

    /**
     * Hidden views retain layout space; legacy setVisible(false) still collapses.
     */
    public final void setHidden(boolean hidden) {
        setAvailability(!hidden, false);
    }
    private GraphicsTransform visualTransform = GraphicsTransform.IDENTITY;
    private long attachmentRevision;

    /** Visual translation/scale about arranged bounds; does not move siblings. */
    public final void setVisualTransform(GraphicsTransform transform) {
        if (transform == null) {
            throw new IllegalArgumentException("GUI transform cannot be null");
        }
        if (visualTransform.x != transform.x || visualTransform.y != transform.y
                || visualTransform.scaleX != transform.scaleX || visualTransform.scaleY != transform.scaleY) {
            visualTransform = transform;
            if (scene != null) {
                scene.requestFrame();
            }
        }
    }

    final GraphicsTransform arrangedTransform() {
        if (visualTransform == GraphicsTransform.IDENTITY) {
            return GraphicsTransform.IDENTITY;
        }
        return new GraphicsTransform(getLeft() + visualTransform.x - getLeft() * visualTransform.scaleX,
                getTop() + visualTransform.y - getTop() * visualTransform.scaleY, visualTransform.scaleX,
                visualTransform.scaleY);
    }

    final long attachmentRevision() {
        return attachmentRevision;
    }

    public final Rect getBounds() {
        return inputBounds == null ? bounds : inputBounds;
    }

    /**
     * Legacy containers restrict child input to their bounds; unclipped retained
     * views may opt out.
     */
    protected Rect childInputClip() {
        return bounds;
    }

    public final int getLeft() {
        return getBounds().getLeft();
    }

    public final int getTop() {
        return getBounds().getTop();
    }

    public final int getRight() {
        return getBounds().getRight();
    }

    public final int getBottom() {
        return getBounds().getBottom();
    }

    public final int getWidth() {
        return getBounds().getWidth();
    }

    public final int getHeight() {
        return getBounds().getHeight();
    }

    public final Size getMeasuredSize() {
        return measuredSize;
    }

    public final GuiContainer getParent() {
        return parent;
    }

    public final boolean isVisible() {
        return visible;
    }

    public final void setVisible(boolean visible) {
        setAvailability(visible, !visible);
    }

    private void setAvailability(boolean visible, boolean collapsed) {
        if (this.visible == visible && this.collapsed == collapsed) {
            return;
        }
        this.visible = visible;
        this.collapsed = collapsed;
        if (!visible && scene != null) {
            scene.releaseOwnership(this);
        }
        requestLayout();
    }

    public final boolean isEnabled() {
        return enabled;
    }

    public final void setEnabled(boolean enabled) {
        if (this.enabled == enabled) {
            return;
        }
        this.enabled = enabled;
        if (!enabled && scene != null) {
            scene.releaseOwnership(this);
        }
    }

    public final boolean isFocusable() {
        return focusable;
    }

    protected final void setFocusable(boolean focusable) {
        this.focusable = focusable;
        if (!focusable && scene != null) {
            scene.clearFocus(this);
        }
    }

    public final boolean isFocused() {
        return scene != null && scene.getFocusedElement() == this;
    }

    /**
     * Called once per actual focus transition, including detachment and keyboard
     * navigation.
     */
    protected void onFocusChanged(GuiContext context, boolean focused) {
    }

    public final boolean contains(int x, int y) {
        return visible && getBounds().contains(x, y);
    }

    public final Size measure(GuiContext context, Constraints constraints) {
        if (collapsed) {
            measuredSize = Size.ZERO;
            return measuredSize;
        }
        if (constraints == null) {
            throw new IllegalArgumentException("GUI constraints cannot be null");
        }
        measuredSize = constraints.constrain(measureElement(context, constraints));
        return measuredSize;
    }

    protected Size measureElement(GuiContext context, Constraints constraints) {
        return Size.ZERO;
    }

    public final void arrange(GuiContext context, Rect bounds) {
        if (bounds == null) {
            throw new IllegalArgumentException("GUI bounds cannot be null");
        }
        if (!this.bounds.equals(bounds) && scene != null) {
            scene.requestFrame();
        }
        this.bounds = bounds;
        if (!collapsed) {
            arrangeElement(context);
        }
    }

    protected void arrangeElement(GuiContext context) {
    }

    /** Publishes dynamic values outside measurement and painting. */
    protected void updateElement(GuiContext context) {
    }

    void update(GuiContext context) {
        updateElement(context);
    }

    /** Runs after update/layout, never from frame sampling or paint. */
    protected void prepareVisualsElement(GuiContext context) {
    }

    void prepareVisuals(GuiContext context) {
        prepareVisualsElement(context);
    }

    /** Java-only frozen data. Returned geometry belongs to one displayed frame. */
    protected GuiFrameContent sampleFrameContent() {
        return null;
    }

    public final void render(GuiContext context) {
        if (!visible || opacity == 0 || bounds.isEmpty()) {
            return;
        }
        GuiFrameSnapshot.Node previous = context.enterElement(this);
        GuiFrameSnapshot.Node node = context.frameNode(this);
        boolean transformed = false;
        try {
            context.getRenderer().pushTransform(node == null ? GraphicsTransform.IDENTITY : node.localTransform);
            transformed = true;
            renderElement(context);
        } finally {
            if (transformed) {
                context.getRenderer().popTransform();
            }
            context.leaveElement(previous);
        }
    }

    protected void renderElement(GuiContext context) {
    }

    protected boolean onInput(GuiContext context, GuiInputEvent event) {
        return false;
    }

    /** Ancestors may intercept before target and bubbling handlers. */
    protected boolean onInputCapture(GuiContext context, GuiInputEvent event) {
        return false;
    }

    final boolean dispatchCapture(GuiContext context, GuiInputEvent event) {
        return dispatch(context, event, true);
    }

    protected final boolean isHovered(GuiContext context) {
        return context != null && contains(context.getMouseX(), context.getMouseY())
                && context.getRenderer().isPointVisible(context.getMouseX(), context.getMouseY());
    }

    protected final void capturePointer(GuiContext context) {
        if (context != null) {
            context.capturePointer(this);
        }
    }

    protected final void releasePointer(GuiContext context) {
        if (context != null) {
            context.releasePointer(this);
        }
    }

    protected final void requestFocus(GuiContext context) {
        if (context != null) {
            context.requestFocus(this);
        }
    }

    protected final void requestLayout() {
        if (scene != null) {
            scene.requestLayout();
        }
    }

    protected final void requestFrame() {
        if (scene != null) {
            scene.requestFrame();
        }
    }

    final boolean dispatchInput(GuiContext context, GuiInputEvent event) {
        return dispatch(context, event, false);
    }

    private boolean dispatch(GuiContext context, GuiInputEvent event, boolean capture) {
        if (!isAvailable()) {
            return false;
        }
        Rect previousBounds = inputBounds;
        GuiFrameSnapshot.Node previous = context.enterInputElement(this);
        GuiFrameSnapshot.Node node = context.currentFrameNode();
        inputBounds = node == null ? null : node.layoutBounds;
        try {
            return capture ? onInputCapture(context, event) : onInput(context, event);
        } finally {
            inputBounds = previousBounds;
            context.leaveElement(previous);
        }
    }

    final void cancelPointer(GuiContext context, int button) {
        onInput(context, GuiInputEvent.pointerCancel(button));
    }

    final boolean isAvailable() {
        return visible && enabled && opacity > 0 && (parent == null || parent.isAvailable());
    }

    void collectFocusableElements(List<GuiElement> elements) {
        if (visible && enabled && opacity > 0 && focusable) {
            elements.add(this);
        }
    }

    final void attach(GuiContainer parent, GuiScene scene) {
        if (this.parent != parent || this.scene != scene) {
            attachmentRevision++;
        }
        this.parent = parent;
        attachScene(scene);
    }

    void attachScene(GuiScene scene) {
        this.scene = scene;
    }

    final void detach() {
        attachmentRevision++;
        if (scene != null) {
            scene.releaseOwnership(this);
        }
        this.parent = null;
        attachScene(null);
    }

    final boolean belongsTo(GuiScene scene) {
        return this.scene == scene;
    }
}
