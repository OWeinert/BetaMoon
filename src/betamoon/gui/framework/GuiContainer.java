package betamoon.gui.framework;

import betamoon.gui.framework.GuiGeometry.Constraints;
import betamoon.gui.framework.GuiGeometry.Rect;
import betamoon.gui.framework.GuiGeometry.Size;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Element that owns and renders an ordered collection of children. */
public class GuiContainer extends GuiElement {
    private final List<GuiElement> children = new ArrayList<GuiElement>();
    private final List<GuiElement> readOnlyChildren = Collections.unmodifiableList(children);

    public final <T extends GuiElement> T add(T child) {
        if (child == null) {
            throw new IllegalArgumentException("GUI child cannot be null");
        }
        if (child == this || isAncestor(child)) {
            throw new IllegalArgumentException("A GUI element cannot contain itself or one of its ancestors");
        }
        if (child.getParent() != null) {
            throw new IllegalStateException("GUI child already belongs to a container");
        }
        GuiScene scene = scene();
        if (scene != null && scene.deferMutation(() -> add(child))) {
            return child;
        }
        children.add(child);
        child.attach(this, scene());
        requestLayout();
        return child;
    }

    public void remove(GuiElement child) {
        GuiScene scene = scene();
        if (scene != null && scene.deferMutation(() -> remove(child))) {
            return;
        }
        if (children.remove(child)) {
            child.detach();
            requestLayout();
        }
    }

    public void clear() {
        GuiScene scene = scene();
        if (scene != null && scene.deferMutation(this::clear)) {
            return;
        }
        for (int i = children.size() - 1; i >= 0; i--) {
            children.get(i).detach();
        }
        children.clear();
        requestLayout();
    }

    public final List<GuiElement> getChildren() {
        return readOnlyChildren;
    }

    @Override
    void update(GuiContext context) {
        super.update(context);
        for (GuiElement child : children) {
            child.update(context);
        }
    }

    @Override
    void prepareVisuals(GuiContext context) {
        super.prepareVisuals(context);
        for (GuiElement child : children) {
            child.prepareVisuals(context);
        }
    }

    @Override
    protected Size measureElement(GuiContext context, Constraints constraints) {
        int width = 0;
        int height = 0;
        Constraints childConstraints = Constraints.upTo(constraints.getMaxWidth(), constraints.getMaxHeight());
        for (int i = 0; i < children.size(); i++) {
            Size size = children.get(i).measure(context, childConstraints);
            width = Math.max(width, size.getWidth());
            height = Math.max(height, size.getHeight());
        }
        return new Size(width, height);
    }

    @Override
    protected void arrangeElement(GuiContext context) {
        arrangeChildren(context);
    }

    /** Default containers overlay every child across their complete bounds. */
    protected void arrangeChildren(GuiContext context) {
        Rect bounds = getBounds();
        for (int i = 0; i < children.size(); i++) {
            children.get(i).arrange(context, bounds);
        }
    }

    @Override
    protected void renderElement(GuiContext context) {
        renderBeforeChildren(context);
        for (int i = 0; i < children.size(); i++) {
            children.get(i).render(context);
        }
        renderAfterChildren(context);
    }

    protected void renderBeforeChildren(GuiContext context) {
    }

    protected void renderAfterChildren(GuiContext context) {
    }

    @Override
    void collectFocusableElements(List<GuiElement> elements) {
        if (!isAvailable()) {
            return;
        }
        if (isFocusable()) {
            elements.add(this);
        }
        for (int i = 0; i < children.size(); i++) {
            children.get(i).collectFocusableElements(elements);
        }
    }

    @Override
    void attachScene(GuiScene scene) {
        super.attachScene(scene);
        for (int i = 0; i < children.size(); i++) {
            children.get(i).attach(this, scene);
        }
    }

    private boolean isAncestor(GuiElement possibleAncestor) {
        GuiContainer current = getParent();
        while (current != null) {
            if (current == possibleAncestor) {
                return true;
            }
            current = current.getParent();
        }
        return false;
    }

    private GuiScene scene() {
        GuiContainer current = this;
        while (current.getParent() != null) {
            current = current.getParent();
        }
        return current instanceof SceneRoot ? ((SceneRoot) current).getScene() : null;
    }

    static final class SceneRoot extends GuiContainer {
        private final GuiScene scene;

        SceneRoot(GuiScene scene) {
            this.scene = scene;
            attachScene(scene);
        }

        GuiScene getScene() {
            return scene;
        }

        @Override
        protected void renderElement(GuiContext context) {
            for (GuiElement child : getChildren()) {
                if (scene.dimBefore(child)) {
                    context.getRenderer().drawRect(0, 0, context.getScreenWidth(), context.getScreenHeight(),
                            0x88000000);
                }
                child.render(context);
            }
        }

        @Override
        protected void arrangeChildren(GuiContext context) {
            for (GuiElement child : getChildren()) {
                Rect bounds = child instanceof GuiViewportLayout
                        ? ((GuiViewportLayout) child).viewportBounds(context, getBounds())
                        : getBounds();
                child.arrange(context, bounds);
            }
        }
    }
}
