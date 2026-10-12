package betamoon.client.control.input;

/** Native or named input families that an active context may capture. */
public enum InputFamily {
    NAMED_ACTIONS("actions"), MOVEMENT("movement"), LOOK("look"), WORLD_ACTIONS("world_actions"), INVENTORY(
            "inventory"), GUI("gui");

    private final String luaName;

    InputFamily(String luaName) {
        this.luaName = luaName;
    }

    public String luaName() {
        return luaName;
    }

    public static InputFamily parse(String value) {
        if (value != null) {
            String normalized = value.trim().toLowerCase();
            for (InputFamily family : values()) {
                if (family.luaName.equals(normalized)) {
                    return family;
                }
            }
        }
        throw new IllegalArgumentException("Unknown input family: " + value);
    }
}
