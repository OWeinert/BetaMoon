package betamoon.utils;

public enum McColors {
    BLACK(0x000000),
    DARK_BLUE(0x0000AA),
    DARK_GREEN(0x00AA00),
    DARK_AQUA(0x00AAAA),
    DARK_RED(0xAA0000),
    DARK_PURPLE(0xAA00AA),
    GOLD(0xFFAA00),
    GRAY(0xAAAAAA),
    DARK_GRAY(0x555555),
    BLUE(0x5555FF),
    GREEN(0x55FF55),
    AQUA(0x55FFFF),
    RED(0xFF5555),
    LIGHT_PURPLE(0xFF55FF),
    YELLOW(0xFFFF55),
    WHITE(0xFFFFFF);

    private final int rgb;

    McColors(int rgb) {
        this.rgb = rgb;
    }

    public int getRgb() {
        return rgb;
    }

    public int getArgb(int alpha) {
        return (alpha & 0xFF) << 24 | rgb;
    }
}
