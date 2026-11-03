package betamoon.gui.menu;

import betamoon.gui.framework.GuiGeometry.Rect;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Native reservations leave nonoverlapping contribution rails; overflow belongs
 * to shared scrolling.
 */
public final class GuiMenuLayout {
    public final Rect actions;
    public final Rect footer;
    public final Rect recovery;
    public final Rect content;

    public GuiMenuLayout(int width, int height, List<Rect> reserved, int recoveryHeight) {
        int left = width / 2 - 100;
        int right = width / 2 + 100;
        for (Rect rect : reserved) {
            if (rect.getLeft() > 8 && rect.getLeft() <= width / 2 && rect.getRight() > width / 2) {
                left = Math.min(left, rect.getLeft());
            }
            if (rect.getRight() < width - 8 && rect.getLeft() < width / 2 && rect.getRight() >= width / 2) {
                right = Math.max(right, rect.getRight());
            }
        }
        int leftWidth = Math.max(0, Math.min(200, left - 16));
        int rightWidth = Math.max(0, Math.min(200, width - right - 16));
        int chromeWidth = Math.min(Math.max(24, leftWidth), Math.max(0, width - 16));
        int chromeHeight = Math.min(recoveryHeight, Math.max(0, height - 16));
        recovery = Rect.fromPositionAndSize(8, Math.max(8, height - chromeHeight - 8), chromeWidth, chromeHeight);
        List<Rect> exclusions = new ArrayList<>(reserved);
        if (chromeHeight > 0) {
            exclusions.add(recovery);
        }
        actions = free(Rect.fromPositionAndSize(8, 8, leftWidth, Math.max(0, height - 16)), exclusions, false);
        footer = free(
                Rect.fromPositionAndSize(Math.max(8, width - rightWidth - 8), 8, rightWidth, Math.max(0, height - 16)),
                exclusions, true);
        content = Rect.fromPositionAndSize(8, 8, Math.max(0, width - 16),
                Math.max(0, height - 16 - (chromeHeight > 0 ? chromeHeight + 8 : 0)));
    }

    public Rect region(String name) {
        switch (name) {
            case "actions":
                return actions;
            case "footer":
                return footer;
            case "recovery":
                return recovery;
            case "content":
                return content;
            default:
                throw new IllegalArgumentException("Unknown native menu region: " + name);
        }
    }

    private static Rect free(Rect column, List<Rect> reserved, boolean bottom) {
        if (column.getWidth() < 24) {
            return Rect.EMPTY;
        }
        List<Rect> blocked = new ArrayList<>();
        for (Rect rect : reserved) {
            Rect clipped = rect.intersect(column);
            if (!clipped.isEmpty()) {
                blocked.add(clipped);
            }
        }
        blocked.sort(Comparator.comparingInt(Rect::getTop));
        int cursor = column.getTop();
        Rect chosen = Rect.EMPTY;
        for (int index = 0; index <= blocked.size(); index++) {
            int next = index == blocked.size() ? column.getBottom() : blocked.get(index).getTop();
            Rect candidate = new Rect(column.getLeft(), cursor, column.getRight(), Math.max(cursor, next));
            if (candidate.getHeight() >= 20
                    && (chosen.isEmpty() || bottom || candidate.getHeight() > chosen.getHeight())) {
                chosen = candidate;
            }
            if (index < blocked.size()) {
                cursor = Math.max(cursor, blocked.get(index).getBottom());
            }
        }
        return chosen;
    }
}
