package betamoon.gui.collection;

import java.util.List;

/**
 * Fixed-height realization geometry, independent of row factories and native
 * rendering.
 */
public final class GuiCollectionWindow {
    public static final int MAX_REALIZED_ROWS = 512;
    public final int first;
    public final int lastExclusive;
    public final int offset;
    public final int contentHeight;
    public final int rowHeight;
    public final int viewportHeight;
    public final String anchor;
    private final int anchorIndex;
    private final int anchorInset;

    private GuiCollectionWindow(List<GuiCollectionModel.Row> rows, int rowHeight, int height, int overscan,
            int requestedOffset) {
        if (rows == null || rows.size() > GuiCollectionModel.MAX_RECORDS || rowHeight < 1 || rowHeight > 1024
                || height < 0 || height > 16384 || overscan < 0 || overscan > 8) {
            throw new IllegalArgumentException("Invalid fixed-height collection viewport or overscan budget");
        }
        this.rowHeight = rowHeight;
        viewportHeight = height;
        contentHeight = rows.size() * rowHeight;
        offset = Math.max(0, Math.min(requestedOffset, Math.max(0, contentHeight - Math.max(1, height))));
        anchorIndex = rows.isEmpty() ? 0 : offset / rowHeight;
        anchorInset = offset % rowHeight;
        anchor = rows.isEmpty() ? null : rows.get(anchorIndex).record.key;
        first = height == 0 ? anchorIndex : Math.max(0, anchorIndex - overscan);
        lastExclusive = height == 0
                ? first
                : Math.min(rows.size(), (offset + height + rowHeight - 1) / rowHeight + overscan);
        if (lastExclusive - first > MAX_REALIZED_ROWS) {
            throw new IllegalArgumentException("Collection viewport exceeds 512 realized rows");
        }
    }

    public static GuiCollectionWindow create(List<GuiCollectionModel.Row> rows, int rowHeight, int height, int overscan,
            int offset) {
        return new GuiCollectionWindow(rows, rowHeight, height, overscan, offset);
    }

    /**
     * Retain the first visible key and its within-row offset through model
     * reorder/resize.
     */
    public GuiCollectionWindow reconcile(List<GuiCollectionModel.Row> rows, int nextRowHeight, int height,
            int overscan) {
        if (rows == null || rows.size() > GuiCollectionModel.MAX_RECORDS) {
            throw new IllegalArgumentException("Collection window requires at most 4096 rows");
        }
        int index = Math.min(anchorIndex, Math.max(0, rows.size() - 1));
        if (anchor != null) {
            for (int candidate = 0; candidate < rows.size(); candidate++) {
                if (rows.get(candidate).record.key.equals(anchor)) {
                    index = candidate;
                    break;
                }
            }
        }
        int requested = nextRowHeight >= 1 && nextRowHeight <= 1024
                ? index * nextRowHeight + Math.min(anchorInset, nextRowHeight - 1)
                : 0;
        return create(rows, nextRowHeight, height, overscan, requested);
    }

    public int revealOffset(int index) {
        if (index < 0 || index >= contentHeight / rowHeight) {
            throw new IllegalArgumentException("Collection reveal index is outside the current model");
        }
        int top = index * rowHeight;
        if (top < offset || viewportHeight < rowHeight) {
            return top;
        }
        return top + rowHeight > offset + viewportHeight ? top + rowHeight - viewportHeight : offset;
    }

    public int realized() {
        return lastExclusive - first;
    }

    public int rowTop(int index) {
        if (index < first || index >= lastExclusive) {
            throw new IllegalArgumentException("Collection row has not been realized in this viewport");
        }
        return index * rowHeight - offset;
    }
}
