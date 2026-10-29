package betamoon.gui.framework;

import betamoon.gui.framework.GuiGeometry.Rect;
import betamoon.gui.graphics.GraphicsClip;
import betamoon.gui.graphics.GraphicsTransform;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * One immutable geometry sample shared by a scene's painting and subsequent
 * input.
 */
public final class GuiFrameSnapshot {
    public final long revision;
    public final int width;
    public final int height;
    public final int framebufferWidth;
    public final int framebufferHeight;

    private final List<Node> order = new ArrayList<>();
    private final Map<GuiElement, Node> nodes = new IdentityHashMap<>();

    GuiFrameSnapshot(GuiElement root, long revision, GuiContext context) {
        this.revision = revision;
        width = context.getScreenWidth();
        height = context.getScreenHeight();
        framebufferWidth = context.getDisplayWidth();
        framebufferHeight = context.getDisplayHeight();
        GraphicsClip viewport = new GraphicsClip(0, 0, width, height);
        collect(root, null, GraphicsTransform.IDENTITY, viewport);
    }

    public Node node(GuiElement element) {
        return nodes.get(element);
    }

    List<GuiElement> hitPath(double x, double y) {
        for (int i = order.size() - 1; i >= 0; i--) {
            Node node = order.get(i);
            if (node.hitClip.contains(x, y) && node.bounds.contains(x, y) && node.eligible()) {
                List<GuiElement> path = new ArrayList<>();
                for (Node current = node; current != null; current = current.parent) {
                    path.add(current.element);
                }
                return path;
            }
        }
        return Collections.emptyList();
    }

    List<GuiElement> focusable() {
        List<GuiElement> result = new ArrayList<>();
        for (Node node : order) {
            if (node.eligible() && node.element.isFocusable()) {
                result.add(node.element);
            }
        }
        return result;
    }

    GuiInputEvent event(GuiElement element, GuiInputEvent event) {
        Node node = nodes.get(element);
        if (node == null || !event.isPointerEvent()) {
            return event;
        }
        return event.at((int) Math.floor(node.transform.localX(event.getMouseX())),
                (int) Math.floor(node.transform.localY(event.getMouseY())));
    }

    boolean permits(GuiElement element) {
        Node node = nodes.get(element);
        return node != null && node.eligible();
    }

    private void collect(GuiElement element, Node parent, GraphicsTransform inherited, GraphicsClip hitClip) {
        if (!element.isVisible()) {
            return;
        }
        GraphicsTransform local = element.arrangedTransform();
        GraphicsTransform transform = inherited.compose(local);
        Rect rect = element.getBounds();
        GraphicsClip bounds = new GraphicsClip(rect.getLeft(), rect.getTop(), rect.getRight(), rect.getBottom())
                .transform(transform);
        Node node = new Node(element, parent, transform, local, bounds, hitClip.intersect(bounds));
        nodes.put(element, node);
        order.add(node);
        if (element instanceof GuiContainer) {
            for (GuiElement child : ((GuiContainer) element).getChildren()) {
                collect(child, node, transform, node.hitClip);
            }
        }
    }

    public static final class Node {
        public final GraphicsTransform transform;
        public final GraphicsTransform localTransform;
        public final GraphicsClip bounds;
        public final GraphicsClip hitClip;

        private final GuiElement element;
        private final Node parent;
        private final long attachment;

        private Node(GuiElement element, Node parent, GraphicsTransform transform, GraphicsTransform localTransform,
                GraphicsClip bounds, GraphicsClip hitClip) {
            this.element = element;
            this.parent = parent;
            this.transform = transform;
            this.localTransform = localTransform;
            this.bounds = bounds;
            this.hitClip = hitClip;
            attachment = element.attachmentRevision();
        }

        boolean eligible() {
            return element.attachmentRevision() == attachment && element.isVisible() && element.isEnabled()
                    && (parent == null || parent.eligible());
        }
    }
}
