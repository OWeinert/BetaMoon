package betamoon.gui.hud;

import betamoon.gui.framework.GuiGeometry.Rect;
import java.util.ArrayList;
import java.util.List;

/**
 * Viewport-constrained corner stacks preserve native recovery/achievement/chat
 * regions.
 */
final class GuiNotificationLayout implements GuiNotificationQueue.Placement {
    private final int width;
    private final int height;
    private final List<Rect> reserved;

    GuiNotificationLayout(int width, int height, List<Rect> reserved) {
        this.width = width;
        this.height = height;
        this.reserved = new ArrayList<>(reserved);
    }

    public Rect place(GuiNotification notification, List<Rect> occupied) {
        int panelWidth = Math.min(notification.definition.width, (width - 20) / 2);
        int panelHeight = notification.definition.height;
        if (panelWidth < 40 || panelHeight > height - 8) {
            return null;
        }
        GuiNotificationCorner corner = notification.options().corner;
        int x = corner.right ? width - panelWidth - 4 : 4;
        int y = corner.bottom ? height - panelHeight - 4 : 4;
        List<Rect> blocked = new ArrayList<>(reserved);
        blocked.addAll(occupied);
        for (int attempt = 0; attempt <= blocked.size(); attempt++) {
            if (y < 4 || y + panelHeight > height - 4) {
                return null;
            }
            Rect candidate = Rect.fromPositionAndSize(x, y, panelWidth, panelHeight);
            int next = y;
            for (Rect region : blocked) {
                if (!candidate.intersect(region).isEmpty()) {
                    next = corner.bottom
                            ? Math.min(next, region.getTop() - panelHeight - 4)
                            : Math.max(next, region.getBottom() + 4);
                }
            }
            if (next == y) {
                return candidate;
            }
            y = next;
        }
        return null;
    }
}
