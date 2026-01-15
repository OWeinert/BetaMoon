package betamoon.gui.api;

/**
 * Common layout helpers for GUI positioning.
 */
public final class GuiLayout {
    private GuiLayout() {
    }

    public static int centerX(int screenWidth, int width) {
        return screenWidth / 2 - width / 2;
    }

    public static int centerY(int screenHeight, int height) {
        return screenHeight / 2 - height / 2;
    }

    public static int alignRight(int screenWidth, int width, int padding) {
        return screenWidth - padding - width;
    }

    public static int alignBottom(int screenHeight, int height, int padding) {
        return screenHeight - padding - height;
    }
}
