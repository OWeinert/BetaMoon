package betamoon.gui.framework;

import betamoon.gui.framework.GuiGeometry.Constraints;
import betamoon.gui.framework.GuiGeometry.Rect;
import betamoon.gui.framework.GuiGeometry.Size;
import betamoon.gui.presentation.GuiPresentationTree;

/**
 * Scene-owned, viewport-fitted portal; outside gestures remain consumed through
 * dismissal.
 */
final class GuiAnchoredPopover extends GuiContainer implements GuiViewportLayout, GuiLayer.Document {
    private final GuiElement anchor;
    private final GuiElement content;
    private final boolean atPointer;
    private final int x;
    private final int y;
    private GuiLayer layer;
    private Rect panel = Rect.EMPTY;

    GuiAnchoredPopover(GuiElement anchor, GuiElement content, boolean atPointer, int x, int y) {
        this.anchor = anchor;
        inheritInputFrom(anchor);
        this.content = add(content);
        this.atPointer = atPointer;
        this.x = x;
        this.y = y;
    }

    void bind(GuiLayer layer) {
        this.layer = layer;
    }

    public Rect viewportBounds(GuiContext context, Rect viewport) {
        return viewport;
    }

    @Override
    protected void updateElement(GuiContext context) {
        if (layer != null && context.visibleBounds(anchor).isEmpty()) {
            layer.close();
        }
        requestLayout();
    }

    @Override
    protected void arrangeChildren(GuiContext context) {
        Rect anchor = context.visibleBounds(this.anchor);
        Size size = content.measure(context,
                Constraints.upTo(Math.max(0, getWidth() - 8), Math.max(0, getHeight() - 8)));
        int width = Math.min(Math.max(size.getWidth(), atPointer ? 0 : anchor.getWidth()), Math.max(0, getWidth() - 8));
        int height = Math.min(size.getHeight(), Math.max(0, getHeight() - 8));
        int left = Math.max(getLeft() + 4, Math.min(atPointer ? x : anchor.getLeft(), getRight() - width - 4));
        int below = atPointer ? y : anchor.getBottom();
        int above = (atPointer ? y : anchor.getTop()) - height;
        int top = below + height <= getBottom() - 4 ? below : Math.max(getTop() + 4, above);
        panel = Rect.fromPositionAndSize(left, top, width, height);
        content.arrange(context, panel);
    }

    @Override
    protected boolean onInputCapture(GuiContext context, GuiInputEvent event) {
        if (event.getType() == GuiInputEvent.Type.POINTER_DOWN
                && !panel.contains(event.getMouseX(), event.getMouseY())) {
            layer.close();
            return true;
        }
        return false;
    }

    @Override
    protected boolean onInput(GuiContext context, GuiInputEvent event) {
        return onInputCapture(context, event);
    }

    public GuiPresentationTree document() {
        return content instanceof GuiLayer.Document ? ((GuiLayer.Document) content).document() : null;
    }

    public void replace(GuiPresentationTree replacement) {
        ((GuiLayer.Document) content).replace(replacement);
    }
}
