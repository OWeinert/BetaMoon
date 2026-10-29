package betamoon.gui.graphics;

/** Immutable logical clip with one outward-rounded framebuffer conversion. */
public final class GraphicsClip {
    public final double left;
    public final double top;
    public final double right;
    public final double bottom;

    public GraphicsClip(double left, double top, double right, double bottom) {
        if (!finite(left) || !finite(top) || !finite(right) || !finite(bottom)) {
            throw new IllegalArgumentException("Graphics clip coordinates must be finite and bounded");
        }
        this.left = left;
        this.top = top;
        this.right = Math.max(left, right);
        this.bottom = Math.max(top, bottom);
    }

    public boolean isEmpty() {
        return left == right || top == bottom;
    }

    public boolean contains(double x, double y) {
        return x >= left && x < right && y >= top && y < bottom;
    }

    public GraphicsClip intersect(GraphicsClip other) {
        return new GraphicsClip(Math.max(left, other.left), Math.max(top, other.top), Math.min(right, other.right),
                Math.min(bottom, other.bottom));
    }

    public GraphicsClip transform(GraphicsTransform transform) {
        return new GraphicsClip(transform.mapX(left), transform.mapY(top), transform.mapX(right),
                transform.mapY(bottom));
    }

    public Pixels pixels(int logicalWidth, int logicalHeight, int framebufferWidth, int framebufferHeight) {
        if (logicalWidth <= 0 || logicalHeight <= 0 || framebufferWidth <= 0 || framebufferHeight <= 0) {
            return new Pixels(0, 0, 0, 0);
        }
        double xScale = (double) framebufferWidth / logicalWidth;
        double yScale = (double) framebufferHeight / logicalHeight;
        int x = floor(left * xScale, framebufferWidth);
        int y = floor((logicalHeight - bottom) * yScale, framebufferHeight);
        if (isEmpty()) {
            return new Pixels(x, y, 0, 0);
        }
        int endX = ceil(right * xScale, framebufferWidth);
        int endY = ceil((logicalHeight - top) * yScale, framebufferHeight);
        return new Pixels(x, y, Math.max(0, endX - x), Math.max(0, endY - y));
    }

    private static int floor(double value, int maximum) {
        return (int) Math.max(0, Math.min(maximum, Math.floor(value)));
    }

    private static int ceil(double value, int maximum) {
        return (int) Math.max(0, Math.min(maximum, Math.ceil(value)));
    }

    private static boolean finite(double value) {
        return Double.isFinite(value) && Math.abs(value) <= 1e9;
    }

    public static final class Pixels {
        public final int x;
        public final int y;
        public final int width;
        public final int height;

        private Pixels(int x, int y, int width, int height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }
    }
}
