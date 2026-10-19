package betamoon.gui.presentation;

/** Axis-aligned translation/scale geometry in logical GUI pixels, shared by draw and input. */
public final class GuiGeometry {
    public final double x;
    public final double y;
    public final double scaleX;
    public final double scaleY;
    public final double width;
    public final double height;
    public final double opacity;
    public final int tint;
    public final boolean visible;
    public final Bounds clip;

    public GuiGeometry(double x, double y, double scaleX, double scaleY, double width, double height,
            double opacity, int tint, boolean visible, Bounds clip) {
        this.x = x;
        this.y = y;
        this.scaleX = scaleX;
        this.scaleY = scaleY;
        this.width = width;
        this.height = height;
        this.opacity = opacity;
        this.tint = tint;
        this.visible = visible;
        this.clip = clip;
    }

    public Bounds bounds() {
        return new Bounds(x, y, width * scaleX, height * scaleY);
    }

    public boolean available() {
        return visible && opacity > 0 && scaleX > 0 && scaleY > 0 && width > 0 && height > 0
                && !bounds().intersect(clip).empty();
    }

    public boolean contains(double px, double py) {
        return available() && bounds().contains(px, py) && clip.contains(px, py);
    }

    public double localX(double px) {
        return scaleX == 0 ? 0 : (px - x) / scaleX;
    }

    public double localY(double py) {
        return scaleY == 0 ? 0 : (py - y) / scaleY;
    }

    public static int multiply(int a, int b) {
        int r = ((a >> 16 & 255) * (b >> 16 & 255) + 127) / 255;
        int g = ((a >> 8 & 255) * (b >> 8 & 255) + 127) / 255;
        int blue = ((a & 255) * (b & 255) + 127) / 255;
        return r << 16 | g << 8 | blue;
    }

    public static final class Bounds {
        public final double x;
        public final double y;
        public final double width;
        public final double height;

        public Bounds(double x, double y, double width, double height) {
            this.x = x;
            this.y = y;
            this.width = Math.max(0, width);
            this.height = Math.max(0, height);
        }

        public boolean contains(double px, double py) {
            return px >= x && px < x + width && py >= y && py < y + height;
        }

        public boolean empty() {
            return width <= 0 || height <= 0;
        }

        public Bounds intersect(Bounds other) {
            double left = Math.max(x, other.x);
            double top = Math.max(y, other.y);
            return new Bounds(left, top, Math.min(x + width, other.x + other.width) - left,
                    Math.min(y + height, other.y + other.height) - top);
        }
    }
}
