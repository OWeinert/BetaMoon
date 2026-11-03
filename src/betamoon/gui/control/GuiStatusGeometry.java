package betamoon.gui.control;

/** Bounded Java-only status fractions and animation phase. */
public final class GuiStatusGeometry {
    private GuiStatusGeometry() {
    }

    public static double phase(long milliseconds) {
        return Math.floorMod(milliseconds, 1200L) / 1200D;
    }

    public static double segment(double fraction, int index, int count) {
        if (!Double.isFinite(fraction) || count < 1 || count > 256 || index < 0 || index >= count) {
            throw new IllegalArgumentException("Invalid status segment geometry");
        }
        return Math.max(0, Math.min(1, fraction * count - index));
    }
}
