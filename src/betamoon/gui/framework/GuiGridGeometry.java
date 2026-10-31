package betamoon.gui.framework;

import betamoon.gui.framework.GuiGeometry.Constraints;
import betamoon.gui.framework.GuiGeometry.Insets;
import betamoon.gui.framework.GuiGeometry.Rect;
import betamoon.gui.framework.GuiGeometry.Size;
import java.util.ArrayList;
import java.util.List;

/**
 * Shared fixed/responsive cell measurement and arrangement over the existing
 * retained children.
 */
public final class GuiGridGeometry {
    private final int columns;
    private final int minimumCellWidth;
    private final int gap;
    private final Insets padding;

    public GuiGridGeometry(int columns, int minimumCellWidth, int gap, Insets padding) {
        if (columns < 0 || columns > 64 || minimumCellWidth < 0 || minimumCellWidth > 16384
                || (columns == 0) == (minimumCellWidth == 0) || gap < 0 || gap > 1024) {
            throw new IllegalArgumentException("Grid chooses fixed columns 1..64 or a responsive minimum cell width");
        }
        this.columns = columns;
        this.minimumCellWidth = minimumCellWidth;
        this.gap = gap;
        if (padding == null || (long) padding.getLeft() + padding.getRight() > 16384
                || (long) padding.getTop() + padding.getBottom() > 16384) {
            throw new IllegalArgumentException("Grid padding exceeds its geometry budget");
        }
        this.padding = padding;
    }

    public Insets padding() {
        return padding;
    }

    public int fixedColumns() {
        return columns;
    }

    public int minimumCellWidth() {
        return minimumCellWidth;
    }

    public GuiGridGeometry withPadding(Insets value) {
        return new GuiGridGeometry(columns, minimumCellWidth, gap, value);
    }

    public int columnsFor(int width) {
        if (width < 0 || width > 16384) {
            throw new IllegalArgumentException("Grid width requires 0..16384");
        }
        return columns != 0 ? columns : Math.max(1, Math.min(64, (width + gap) / (minimumCellWidth + gap)));
    }

    public Size measure(GuiContext context, Constraints constraints, List<GuiElement> source) {
        int width = Math.min(16384, constraints.getMaxWidth());
        int available = Math.max(0, width - padding.getHorizontal());
        List<GuiElement> children = participating(source);
        int count = columnsFor(available);
        int[] heights = rowHeights(context, children, count, available, 16384);
        return constraints.constrain(new Size(width, contentHeight(heights)));
    }

    public void arrange(GuiContext context, Rect bounds, List<GuiElement> source) {
        Rect content = bounds.inset(padding);
        List<GuiElement> children = participating(source);
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

    private List<GuiElement> participating(List<GuiElement> source) {
        List<GuiElement> result = new ArrayList<>();
        for (GuiElement child : source) {
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
