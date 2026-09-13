package betamoon.utils;

public final class KeyInputMapper {
    public enum Layout {
        QWERTY(new QwertyKeyLayout()), QWERTZ(new QwertzKeyLayout()), AZERTY(new AzertyKeyLayout());

        private final KeyLayoutMapper mapper;

        Layout(KeyLayoutMapper mapper) {
            this.mapper = mapper;
        }
    }

    private static KeyLayoutMapper current = Layout.QWERTY.mapper;

    private KeyInputMapper() {
    }

    public static char map(int keyCode, boolean shift, boolean alt, boolean ctrl) {
        return current.map(keyCode, shift, alt, ctrl);
    }

    public static void setLayout(Layout layout) {
        if (layout == null) {
            return;
        }
        current = layout.mapper;
    }
}
