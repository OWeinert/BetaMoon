package betamoon.entity.motion;

/** Fully parsed relocation/orientation request; null components retain current state. */
public final class EntityTransformRequest {
    public final Double x;
    public final Double y;
    public final Double z;
    public final Float yaw;
    public final Float pitch;
    public final boolean resetVelocity;
    public final boolean resetFallDistance;
    public final boolean snapInterpolation;

    public EntityTransformRequest(Double x, Double y, Double z, Float yaw, Float pitch,
            boolean resetVelocity, boolean resetFallDistance, boolean snapInterpolation) {
        if (x == null && y == null && z == null && yaw == null && pitch == null) {
            throw new IllegalArgumentException("Transform requires at least one position or rotation component");
        }
        this.x = finite(x);
        this.y = finite(y);
        this.z = finite(z);
        this.yaw = angle(yaw);
        this.pitch = angle(pitch);
        this.resetVelocity = resetVelocity;
        this.resetFallDistance = resetFallDistance;
        this.snapInterpolation = snapInterpolation;
    }

    public boolean changesPosition() {
        return x != null || y != null || z != null;
    }

    private static Double finite(Double value) {
        if (value != null && !Double.isFinite(value)) {
            throw new IllegalArgumentException("Transform components must be finite");
        }
        return value;
    }

    private static Float angle(Float value) {
        if (value != null && !Float.isFinite(value)) {
            throw new IllegalArgumentException("Transform angles exceed the supported finite range");
        }
        return value;
    }
}
