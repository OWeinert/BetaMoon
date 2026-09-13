package betamoon.gui.framework;

/** Immutable geometry values shared by layout, rendering, and input handling. */
public final class GuiGeometry {
    private GuiGeometry() {
    }

    public static final class Rect {
        public static final Rect EMPTY = new Rect(0, 0, 0, 0);

        private final int left;
        private final int top;
        private final int right;
        private final int bottom;

        public Rect(int left, int top, int right, int bottom) {
            this.left = left;
            this.top = top;
            this.right = Math.max(left, right);
            this.bottom = Math.max(top, bottom);
        }

        public static Rect fromPositionAndSize(int x, int y, int width, int height) {
            return new Rect(x, y, x + Math.max(0, width), y + Math.max(0, height));
        }

        public int getLeft() {
            return left;
        }

        public int getTop() {
            return top;
        }

        public int getRight() {
            return right;
        }

        public int getBottom() {
            return bottom;
        }

        public int getWidth() {
            return right - left;
        }

        public int getHeight() {
            return bottom - top;
        }

        public boolean isEmpty() {
            return right <= left || bottom <= top;
        }

        /** Uses half-open edges so adjacent elements never share a hit pixel. */
        public boolean contains(int x, int y) {
            return x >= left && x < right && y >= top && y < bottom;
        }

        public Rect inset(Insets insets) {
            if (insets == null) {
                return this;
            }
            return new Rect(left + insets.getLeft(), top + insets.getTop(),
                    Math.max(left + insets.getLeft(), right - insets.getRight()),
                    Math.max(top + insets.getTop(), bottom - insets.getBottom()));
        }

        public Rect intersect(Rect other) {
            if (other == null) {
                return EMPTY;
            }
            int intersectionLeft = Math.max(left, other.left);
            int intersectionTop = Math.max(top, other.top);
            int intersectionRight = Math.min(right, other.right);
            int intersectionBottom = Math.min(bottom, other.bottom);
            if (intersectionRight <= intersectionLeft || intersectionBottom <= intersectionTop) {
                return new Rect(intersectionLeft, intersectionTop, intersectionLeft, intersectionTop);
            }
            return new Rect(intersectionLeft, intersectionTop, intersectionRight, intersectionBottom);
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Rect)) {
                return false;
            }
            Rect rect = (Rect) other;
            return left == rect.left && top == rect.top && right == rect.right && bottom == rect.bottom;
        }

        @Override
        public int hashCode() {
            int result = left;
            result = 31 * result + top;
            result = 31 * result + right;
            result = 31 * result + bottom;
            return result;
        }

        @Override
        public String toString() {
            return "Rect{" + left + ", " + top + ", " + right + ", " + bottom + '}';
        }
    }

    public static final class Size {
        public static final Size ZERO = new Size(0, 0);

        private final int width;
        private final int height;

        public Size(int width, int height) {
            this.width = Math.max(0, width);
            this.height = Math.max(0, height);
        }

        public int getWidth() {
            return width;
        }

        public int getHeight() {
            return height;
        }
    }

    public static final class Constraints {
        private final int minWidth;
        private final int minHeight;
        private final int maxWidth;
        private final int maxHeight;

        public Constraints(int minWidth, int minHeight, int maxWidth, int maxHeight) {
            this.minWidth = Math.max(0, minWidth);
            this.minHeight = Math.max(0, minHeight);
            this.maxWidth = Math.max(this.minWidth, maxWidth);
            this.maxHeight = Math.max(this.minHeight, maxHeight);
        }

        public static Constraints tight(int width, int height) {
            return new Constraints(width, height, width, height);
        }

        public static Constraints upTo(int width, int height) {
            return new Constraints(0, 0, Math.max(0, width), Math.max(0, height));
        }

        public int getMinWidth() {
            return minWidth;
        }

        public int getMinHeight() {
            return minHeight;
        }

        public int getMaxWidth() {
            return maxWidth;
        }

        public int getMaxHeight() {
            return maxHeight;
        }

        public Size constrain(Size size) {
            if (size == null) {
                size = Size.ZERO;
            }
            return new Size(clamp(size.getWidth(), minWidth, maxWidth),
                    clamp(size.getHeight(), minHeight, maxHeight));
        }

        private static int clamp(int value, int minimum, int maximum) {
            return Math.max(minimum, Math.min(maximum, value));
        }
    }

    public static final class Insets {
        public static final Insets NONE = new Insets(0, 0, 0, 0);

        private final int left;
        private final int top;
        private final int right;
        private final int bottom;

        public Insets(int all) {
            this(all, all, all, all);
        }

        public Insets(int horizontal, int vertical) {
            this(horizontal, vertical, horizontal, vertical);
        }

        public Insets(int left, int top, int right, int bottom) {
            this.left = Math.max(0, left);
            this.top = Math.max(0, top);
            this.right = Math.max(0, right);
            this.bottom = Math.max(0, bottom);
        }

        public int getLeft() {
            return left;
        }

        public int getTop() {
            return top;
        }

        public int getRight() {
            return right;
        }

        public int getBottom() {
            return bottom;
        }

        public int getHorizontal() {
            return left + right;
        }

        public int getVertical() {
            return top + bottom;
        }
    }
}
