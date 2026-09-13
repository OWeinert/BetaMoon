package betamoon.tileentity;

/** Direction and clipping geometry for a container progress image. */
public enum ProgressDirection {
    LEFT_TO_RIGHT("left_to_right", false, false), RIGHT_TO_LEFT("right_to_left", false,
            true), TOP_TO_BOTTOM("top_to_bottom", true, false), BOTTOM_TO_TOP("bottom_to_top", true, true);

    private final String luaName;
    private final boolean vertical;
    private final boolean reverse;

    ProgressDirection(String luaName, boolean vertical, boolean reverse) {
        this.luaName = luaName;
        this.vertical = vertical;
        this.reverse = reverse;
    }

    public Slice slice(int width, int height, int value, int maximum, int minimumPixels) {
        int fullLength = vertical ? height : width;
        int safeMaximum = Math.max(1, maximum);
        int amount = value * fullLength / safeMaximum;
        if (value > 0) {
            amount = Math.max(minimumPixels, amount);
        }
        amount = Math.min(fullLength, amount);

        int offset = reverse ? fullLength - amount : 0;
        if (vertical) {
            return new Slice(0, offset, width, amount);
        }
        return new Slice(offset, 0, amount, height);
    }

    public static ProgressDirection fromLua(String value) {
        for (ProgressDirection direction : values()) {
            if (direction.luaName.equals(value)) {
                return direction;
            }
        }
        return null;
    }

    /** Source and destination offset plus the visible rectangle size. */
    public static final class Slice {
        public final int x;
        public final int y;
        public final int width;
        public final int height;

        private Slice(int x, int y, int width, int height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }

        public boolean isEmpty() {
            return width <= 0 || height <= 0;
        }
    }
}
