package betamoon.gui.graphics;

/**
 * Immutable axis-aligned transform in logical pixels, independent of a UI host.
 */
public final class GraphicsTransform {
    public static final GraphicsTransform IDENTITY = new GraphicsTransform(0, 0, 1, 1);

    public final double x;
    public final double y;
    public final double scaleX;
    public final double scaleY;

    public GraphicsTransform(double x, double y, double scaleX, double scaleY) {
        if (!finite(x) || !finite(y) || !finite(scaleX) || !finite(scaleY) || scaleX <= 0 || scaleY <= 0) {
            throw new IllegalArgumentException("Graphics transforms require finite translation and positive scale");
        }
        this.x = x;
        this.y = y;
        this.scaleX = scaleX;
        this.scaleY = scaleY;
    }

    /** Applies the child first, then this transform. */
    public GraphicsTransform compose(GraphicsTransform child) {
        if (child == IDENTITY) {
            return this;
        }
        if (this == IDENTITY) {
            return child;
        }
        return new GraphicsTransform(mapX(child.x), mapY(child.y), scaleX * child.scaleX, scaleY * child.scaleY);
    }

    public double mapX(double localX) {
        return x + localX * scaleX;
    }

    public double mapY(double localY) {
        return y + localY * scaleY;
    }

    public double localX(double screenX) {
        return (screenX - x) / scaleX;
    }

    public double localY(double screenY) {
        return (screenY - y) / scaleY;
    }

    private static boolean finite(double value) {
        return Double.isFinite(value) && Math.abs(value) <= 1e9;
    }
}
