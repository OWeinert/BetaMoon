package betamoon.assets.model;

/**
 * Immutable unit quaternion; procedural Euler rotations compose local X, Y,
 * then Z.
 */
public final class ModelRotation {
    public static final ModelRotation IDENTITY = new ModelRotation(0, 0, 0, 1);
    private final double x;
    private final double y;
    private final double z;
    private final double w;

    private ModelRotation(double x, double y, double z, double w) {
        double length = Math.sqrt(x * x + y * y + z * z + w * w);
        this.x = x / length;
        this.y = y / length;
        this.z = z / length;
        this.w = w / length;
    }

    public static ModelRotation euler(ModelVector degrees) {
        double x = Math.toRadians(degrees.x) / 2;
        double y = Math.toRadians(degrees.y) / 2;
        double z = Math.toRadians(degrees.z) / 2;
        return new ModelRotation(Math.sin(x), 0, 0, Math.cos(x)).then(new ModelRotation(0, Math.sin(y), 0, Math.cos(y)))
                .then(new ModelRotation(0, 0, Math.sin(z), Math.cos(z)));
    }

    /** Blockbench uses ZYX Euler order for imported Bedrock bones and cubes. */
    public static ModelRotation bedrock(ModelVector degrees) {
        double x = Math.toRadians(-degrees.x) / 2;
        double y = Math.toRadians(-degrees.y) / 2;
        double z = Math.toRadians(degrees.z) / 2;
        return new ModelRotation(0, 0, Math.sin(z), Math.cos(z)).then(new ModelRotation(0, Math.sin(y), 0, Math.cos(y)))
                .then(new ModelRotation(Math.sin(x), 0, 0, Math.cos(x)));
    }

    public ModelRotation then(ModelRotation other) {
        return new ModelRotation(w * other.x + x * other.w + y * other.z - z * other.y,
                w * other.y - x * other.z + y * other.w + z * other.x,
                w * other.z + x * other.y - y * other.x + z * other.w,
                w * other.w - x * other.x - y * other.y - z * other.z);
    }

    public ModelRotation inverse() {
        return new ModelRotation(-x, -y, -z, w);
    }

    public ModelRotation mix(ModelRotation other, double weight) {
        double dot = x * other.x + y * other.y + z * other.z + w * other.w;
        double sign = dot < 0 ? -1 : 1;
        dot = Math.min(1, Math.abs(dot));
        double a = 1 - weight;
        double b = weight;
        if (dot < 0.9995) {
            double angle = Math.acos(dot);
            a = Math.sin((1 - weight) * angle) / Math.sin(angle);
            b = Math.sin(weight * angle) / Math.sin(angle);
        }
        return new ModelRotation(x * a + other.x * b * sign, y * a + other.y * b * sign, z * a + other.z * b * sign,
                w * a + other.w * b * sign);
    }

    public ModelVector transform(ModelVector point) {
        double tx = 2 * (y * point.z - z * point.y);
        double ty = 2 * (z * point.x - x * point.z);
        double tz = 2 * (x * point.y - y * point.x);
        return new ModelVector(point.x + w * tx + y * tz - z * ty, point.y + w * ty + z * tx - x * tz,
                point.z + w * tz + x * ty - y * tx);
    }

    public ModelVector toEuler() {
        double sineY = Math.max(-1, Math.min(1, 2 * (x * z + w * y)));
        double angleX;
        double angleZ;
        if (Math.abs(sineY) < 0.9999999) {
            angleX = Math.atan2(2 * (w * x - y * z), 1 - 2 * (x * x + y * y));
            angleZ = Math.atan2(2 * (w * z - x * y), 1 - 2 * (y * y + z * z));
        } else {
            angleX = Math.atan2(2 * (y * z + w * x), 1 - 2 * (x * x + z * z));
            angleZ = 0;
        }
        return new ModelVector(Math.toDegrees(angleX), Math.toDegrees(Math.asin(sineY)), Math.toDegrees(angleZ));
    }
}
