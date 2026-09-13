package betamoon.tileentity;

/** Calculates positions for the supported container GUI anchors. */
public enum GuiAnchor {
    TOP_LEFT("top_left", Horizontal.LEFT, Vertical.TOP), TOP_CENTER("top_center", Horizontal.CENTER,
            Vertical.TOP), TOP_RIGHT("top_right", Horizontal.RIGHT, Vertical.TOP), CENTER("center", Horizontal.CENTER,
                    Vertical.CENTER), BOTTOM_LEFT("bottom_left", Horizontal.LEFT, Vertical.BOTTOM), BOTTOM_CENTER(
                            "bottom_center", Horizontal.CENTER,
                            Vertical.BOTTOM), BOTTOM_RIGHT("bottom_right", Horizontal.RIGHT, Vertical.BOTTOM);

    private final String luaName;
    private final Horizontal horizontal;
    private final Vertical vertical;

    GuiAnchor(String luaName, Horizontal horizontal, Vertical vertical) {
        this.luaName = luaName;
        this.horizontal = horizontal;
        this.vertical = vertical;
    }

    public int positionX(int offset, int elementWidth, int containerWidth) {
        if (horizontal == Horizontal.CENTER) {
            return (containerWidth - elementWidth) / 2 + offset;
        }
        if (horizontal == Horizontal.RIGHT) {
            return containerWidth - elementWidth + offset;
        }
        return offset;
    }

    public int positionY(int offset, int elementHeight, int containerHeight) {
        if (vertical == Vertical.CENTER) {
            return (containerHeight - elementHeight) / 2 + offset;
        }
        if (vertical == Vertical.BOTTOM) {
            return containerHeight - elementHeight + offset;
        }
        return offset;
    }

    public static GuiAnchor fromLua(String value) {
        for (GuiAnchor anchor : values()) {
            if (anchor.luaName.equals(value)) {
                return anchor;
            }
        }
        return null;
    }

    private enum Horizontal {
        LEFT, CENTER, RIGHT
    }

    private enum Vertical {
        TOP, CENTER, BOTTOM
    }
}
