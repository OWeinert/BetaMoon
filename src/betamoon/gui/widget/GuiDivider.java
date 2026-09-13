package betamoon.gui.widget;

import betamoon.gui.framework.GuiContext;
import betamoon.gui.framework.GuiElement;
import betamoon.gui.framework.GuiLayouts.Axis;

/** One-pixel themed separator. */
public final class GuiDivider extends GuiElement {
    private final Axis axis;
    private int color;

    public GuiDivider(Axis axis, int color) {
        if (axis == null) {
            throw new IllegalArgumentException("Divider axis cannot be null");
        }
        this.axis = axis;
        this.color = color;
    }

    public void setColor(int color) {
        this.color = color;
    }

    @Override
    protected void renderElement(GuiContext context) {
        if (axis == Axis.HORIZONTAL) {
            context.getRenderer().drawHorizontalLine(getLeft(), getRight(), getTop(), color);
        } else {
            context.getRenderer().drawVerticalLine(getTop(), getBottom(), getLeft(), color);
        }
    }
}
