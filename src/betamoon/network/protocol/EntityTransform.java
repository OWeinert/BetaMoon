package betamoon.network.protocol;

/** Complete authoritative transform used when an entity begins tracking. */
public final class EntityTransform {
    public final double x;
    public final double y;
    public final double z;
    public final float yaw;
    public final float pitch;
    public final double velocityX;
    public final double velocityY;
    public final double velocityZ;

    public EntityTransform(double x, double y, double z, float yaw, float pitch,
            double velocityX, double velocityY, double velocityZ) {
        finite(x, "x");
        finite(y, "y");
        finite(z, "z");
        finite(yaw, "yaw");
        finite(pitch, "pitch");
        finite(velocityX, "velocityX");
        finite(velocityY, "velocityY");
        finite(velocityZ, "velocityZ");
        bounded(x, ProtocolLimits.MAX_COORDINATE, "x");
        bounded(y, ProtocolLimits.MAX_COORDINATE, "y");
        bounded(z, ProtocolLimits.MAX_COORDINATE, "z");
        bounded(velocityX, ProtocolLimits.MAX_VELOCITY, "velocityX");
        bounded(velocityY, ProtocolLimits.MAX_VELOCITY, "velocityY");
        bounded(velocityZ, ProtocolLimits.MAX_VELOCITY, "velocityZ");
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
        this.velocityX = velocityX;
        this.velocityY = velocityY;
        this.velocityZ = velocityZ;
    }

    private static void finite(double value, String name) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            throw new IllegalArgumentException(name + " must be finite");
        }
    }

    private static void bounded(double value, double bound, String name) {
        if (Math.abs(value) > bound) {
            throw new IllegalArgumentException(name + " exceeds the network bound");
        }
    }
}
