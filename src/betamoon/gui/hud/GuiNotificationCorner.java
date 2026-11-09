package betamoon.gui.hud;

/**
 * Logical notification stack anchors; stack fitting belongs to the Java host.
 */
public enum GuiNotificationCorner {
    TOP_LEFT("top_left", false, false), TOP_RIGHT("top_right", true, false), BOTTOM_LEFT("bottom_left", false,
            true), BOTTOM_RIGHT("bottom_right", true, true);

    public final String name;
    public final boolean right;
    public final boolean bottom;

    GuiNotificationCorner(String name, boolean right, boolean bottom) {
        this.name = name;
        this.right = right;
        this.bottom = bottom;
    }

    public static GuiNotificationCorner named(String name) {
        for (GuiNotificationCorner corner : values()) {
            if (corner.name.equals(name)) {
                return corner;
            }
        }
        throw new IllegalArgumentException("Unknown notification corner: " + name);
    }
}
