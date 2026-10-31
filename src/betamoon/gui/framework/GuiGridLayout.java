package betamoon.gui.framework;

import betamoon.gui.framework.GuiGeometry.Constraints;
import betamoon.gui.framework.GuiGeometry.Insets;
import betamoon.gui.framework.GuiGeometry.Size;

/** Width-filling fixed/responsive columns over ordinary retained children. */
public final class GuiGridLayout extends GuiContainer {
    private GuiGridGeometry geometry;

    public GuiGridLayout(int columns, int minimumCellWidth, int gap) {
        setMetrics(columns, minimumCellWidth, gap);
    }

    public void setMetrics(int columns, int minimumCellWidth, int gap) {
        geometry = new GuiGridGeometry(columns, minimumCellWidth, gap,
                geometry == null ? Insets.NONE : geometry.padding());
        requestLayout();
    }

    public void setPadding(Insets value) {
        geometry = geometry.withPadding(value);
        requestLayout();
    }

    public int columnsFor(int width) {
        return geometry.columnsFor(width);
    }

    @Override
    protected Size measureElement(GuiContext context, Constraints constraints) {
        return geometry.measure(context, constraints, getChildren());
    }

    @Override
    protected void arrangeChildren(GuiContext context) {
        geometry.arrange(context, getBounds(), getChildren());
    }
}
