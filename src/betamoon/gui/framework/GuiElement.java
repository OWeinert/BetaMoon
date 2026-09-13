package betamoon.gui.framework;

import betamoon.gui.framework.GuiGeometry.Constraints;
import betamoon.gui.framework.GuiGeometry.Rect;
import betamoon.gui.framework.GuiGeometry.Size;
import java.util.List;

/** Base class for every retained GUI element. */
public abstract class GuiElement {
    private Rect bounds = Rect.EMPTY;
    private Size measuredSize = Size.ZERO;
    private GuiContainer parent;
    private GuiScene scene;
    private boolean visible = true;
    private boolean enabled = true;
    private boolean focusable;

    public final Rect getBounds() {
        return bounds;
    }

    public final int getLeft() {
        return bounds.getLeft();
    }

    public final int getTop() {
        return bounds.getTop();
    }

    public final int getRight() {
        return bounds.getRight();
    }

    public final int getBottom() {
        return bounds.getBottom();
    }

    public final int getWidth() {
        return bounds.getWidth();
    }

    public final int getHeight() {
        return bounds.getHeight();
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
        if (this.visible == visible) {
            return;
        }
        this.visible = visible;
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

    public final boolean contains(int x, int y) {
        return visible && bounds.contains(x, y);
    }

    public final Size measure(GuiContext context, Constraints constraints) {
        if (!visible) {
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
        this.bounds = bounds;
        if (visible) {
            arrangeElement(context);
        }
    }

    protected void arrangeElement(GuiContext context) {
    }

    public final void render(GuiContext context) {
        if (!visible || bounds.isEmpty()) {
            return;
        }
        renderElement(context);
    }

    protected void renderElement(GuiContext context) {
    }

    protected boolean onInput(GuiContext context, GuiInputEvent event) {
        return false;
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

    final boolean dispatchInput(GuiContext context, GuiInputEvent event) {
        return visible && enabled && onInput(context, event);
    }

    boolean collectHitPath(int x, int y, List<GuiElement> path) {
        if (!contains(x, y)) {
            return false;
        }
        path.add(this);
        return true;
    }

    void collectFocusableElements(List<GuiElement> elements) {
        if (visible && enabled && focusable) {
            elements.add(this);
        }
    }

    final void attach(GuiContainer parent, GuiScene scene) {
        this.parent = parent;
        attachScene(scene);
    }

    void attachScene(GuiScene scene) {
        this.scene = scene;
    }

    final void detach() {
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
