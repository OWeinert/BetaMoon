package betamoon.gui.hud;

import betamoon.gui.framework.GuiGeometry.Rect;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Bounded free-area selection around native chrome and visible notifications.
 */
public final class GuiHudLayout {
    public static Rect safeArea(int width, int height, List<Rect> reservations) {
        if (width < 0 || height < 0 || width > 16384 || height > 16384 || reservations.size() > 16) {
            throw new IllegalArgumentException("HUD safe area requires bounded viewport and at most 16 reservations");
        }
        Rect viewport = Rect.fromPositionAndSize(4, 4, Math.max(0, width - 8), Math.max(0, height - 8));
        List<Rect> free = new ArrayList<>(Collections.singleton(viewport));
        for (Rect reservation : reservations) {
            List<Rect> next = new ArrayList<>();
            for (Rect candidate : free) {
                Rect blocked = candidate.intersect(reservation);
                if (blocked.isEmpty()) {
                    add(next, candidate);
                    continue;
                }
                add(next, new Rect(candidate.getLeft(), candidate.getTop(), blocked.getLeft(), candidate.getBottom()));
                add(next,
                        new Rect(blocked.getRight(), candidate.getTop(), candidate.getRight(), candidate.getBottom()));
                add(next, new Rect(candidate.getLeft(), candidate.getTop(), candidate.getRight(), blocked.getTop()));
                add(next, new Rect(candidate.getLeft(), blocked.getBottom(), candidate.getRight(),
                        candidate.getBottom()));
            }
            free = next;
        }
        Rect best = Rect.EMPTY;
        for (Rect candidate : free) {
            long area = (long) candidate.getWidth() * candidate.getHeight();
            long previous = (long) best.getWidth() * best.getHeight();
            if (area > previous || area == previous && (candidate.getTop() < best.getTop()
                    || candidate.getTop() == best.getTop() && candidate.getLeft() < best.getLeft())) {
                best = candidate;
            }
        }
        return best;
    }

    private static void add(List<Rect> candidates, Rect value) {
        if (value.isEmpty()) {
            return;
        }
        for (Rect existing : candidates) {
            if (contains(existing, value)) {
                return;
            }
        }
        candidates.removeIf(existing -> contains(value, existing));
        candidates.add(value);
    }

    private static boolean contains(Rect outer, Rect inner) {
        return outer.getLeft() <= inner.getLeft() && outer.getTop() <= inner.getTop()
                && outer.getRight() >= inner.getRight() && outer.getBottom() >= inner.getBottom();
    }

    private GuiHudLayout() {
    }
}
