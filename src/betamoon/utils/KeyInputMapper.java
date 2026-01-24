package betamoon.utils;

public final class KeyInputMapper {
    public enum Layout {
        QWERTY,
        QWERTZ,
        AZERTY
    }

    private static KeyLayoutMapper current = new QwertyKeyLayout();

    private KeyInputMapper() {
    }

    public static char map(int keyCode, boolean shift, boolean alt, boolean ctrl) {
        return current.map(keyCode, shift, alt, ctrl);
    }

    public static void setLayout(Layout layout) {
        if (layout == null) {
            return;
        }
        switch (layout) {
            case QWERTY:
                current = new QwertyKeyLayout();
                break;
            case QWERTZ:
                current = new QwertzKeyLayout();
                break;
            case AZERTY:
                current = new AzertyKeyLayout();
                break;
            default:
                current = new QwertyKeyLayout();
                break;
        }
    }
}
