package betamoon.gui.framework;

import betamoon.gui.framework.GuiGeometry.Constraints;
import betamoon.gui.framework.GuiGeometry.Insets;
import betamoon.gui.framework.GuiGeometry.Rect;
import betamoon.gui.framework.GuiGeometry.Size;
import java.util.ArrayList;
import java.util.List;

/** Width-filling fixed/responsive columns over ordinary retained children. */
public final class GuiGridLayout extends GuiContainer {
    private int columns;
    private int minimumCellWidth;
    private int gap;
    private Insets padding = Insets.NONE;

    public GuiGridLayout(int columns, int minimumCellWidth, int gap) {
        setMetrics(columns, minimumCellWidth, gap);
    }

    public void setMetrics(int columns, int minimumCellWidth, int gap) {
        if (columns < 0 || columns > 64 || minimumCellWidth < 0 || minimumCellWidth > 16384
                || (columns == 0) == (minimumCellWidth == 0) || gap < 0 || gap > 1024) {
            throw new IllegalArgumentException("Grid chooses fixed columns 1..64 or a responsive minimum cell width");
        }
        this.columns = columns;
        this.minimumCellWidth = minimumCellWidth;
        this.gap = gap;
        requestLayout();
    }

    public void setPadding(Insets value) {
        if (value == null || (long) value.getLeft() + value.getRight() > 16384
                || (long) value.getTop() + value.getBottom() > 16384) {
            throw new IllegalArgumentException("Grid padding exceeds its geometry budget");
        }
        padding = value;
        requestLayout();
    }

    public int columnsFor(int width) {
        if (width < 0 || width > 16384) {
            throw new IllegalArgumentException("Grid width requires 0..16384");
        }
        return columns != 0 ? columns : Math.max(1, Math.min(64, (width + gap) / (minimumCellWidth + gap)));
    }

    @Override
    protected Size measureElement(GuiContext context, Constraints constraints) {
        int width = Math.min(16384, constraints.getMaxWidth());
        int available = Math.max(0, width - padding.getHorizontal());
        List<GuiElement> children = participating();
        int count = columnsFor(available);
        int[] heights = rowHeights(context, children, count, available, constraints.getMaxHeight());
        return constraints.constrain(new Size(width, contentHeight(heights)));
    }

    @Override
    protected void arrangeChildren(GuiContext context) {
        Rect content = getBounds().inset(padding);
        List<GuiElement> children = participating();
        int count = columnsFor(content.getWidth());
        int[] heights = rowHeights(context, children, count, content.getWidth(), 16384);
        contentHeight(heights);
        int cells = Math.max(0, content.getWidth() - gap * (count - 1));
        int base = cells / count;
        int remainder = cells % count;
        int top = content.getTop();
        for (int index = 0; index < children.size(); index++) {
            int column = index % count;
            int row = index / count;
            if (column == 0 && row > 0) {
                top += heights[row - 1] + gap;
            }
            int left = content.getLeft() + column * (base + gap) + Math.min(column, remainder);
            int width = base + (column < remainder ? 1 : 0);
            children.get(index).arrange(context, Rect.fromPositionAndSize(left, top, width, heights[row]));
        }
    }

    private List<GuiElement> participating() {
        List<GuiElement> result = new ArrayList<>();
        for (GuiElement child : getChildren()) {
            if (!child.isCollapsed()) {
                result.add(child);
                if (result.size() > 4096) {
                    throw new IllegalArgumentException("Grid exceeds 4096 participating children");
                }
            }
        }
        return result;
    }

    private int contentHeight(int[] heights) {
        int height = padding.getVertical() + gap * Math.max(0, heights.length - 1);
        for (int row : heights) {
            height += row;
        }
        if (height > 16384) {
            throw new IllegalArgumentException(
                    "Grid content exceeds 16384 pixels; use a bounded scroll/collection view");
        }
        return height;
    }

    private int[] rowHeights(GuiContext context, List<GuiElement> children, int count, int width, int height) {
        if (!children.isEmpty() && gap * (count - 1) > width) {
            throw new IllegalArgumentException("Grid column gaps do not fit the available width");
        }
        int cells = Math.max(0, width - gap * (count - 1));
        int base = cells / count;
        int remainder = cells % count;
        int[] heights = new int[(children.size() + count - 1) / count];
        for (int index = 0; index < children.size(); index++) {
            int column = index % count;
            Size size = children.get(index).measure(context,
                    Constraints.upTo(base + (column < remainder ? 1 : 0), Math.min(16384, height)));
            heights[index / count] = Math.max(heights[index / count], size.getHeight());
        }
        return heights;
    }
}
