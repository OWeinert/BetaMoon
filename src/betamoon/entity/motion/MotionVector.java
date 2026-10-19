package betamoon.entity.motion;

/** Immutable finite world-space motion/displacement value. */
public final class MotionVector {
    public static final MotionVector ZERO = new MotionVector(0, 0, 0);
    public final double x;
    public final double y;
    public final double z;

    public MotionVector(double x, double y, double z) {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            throw new IllegalArgumentException("Vector components must be finite");
        }
        this.x = x == 0 ? 0 : x;
        this.y = y == 0 ? 0 : y;
        this.z = z == 0 ? 0 : z;
    }

    public boolean within(double limit) {
        return Math.abs(x) <= limit && Math.abs(y) <= limit && Math.abs(z) <= limit;
    }
}
