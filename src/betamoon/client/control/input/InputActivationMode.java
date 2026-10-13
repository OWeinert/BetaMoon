package betamoon.client.control.input;

/** Player-selectable logical activation for a named input action. */
public enum InputActivationMode {
    HOLD("hold"), TOGGLE("toggle");

    private final String luaName;

    InputActivationMode(String luaName) {
        this.luaName = luaName;
    }

    public String luaName() {
        return luaName;
    }

    public static InputActivationMode parse(String value) {
        for (InputActivationMode mode : values()) {
            if (mode.luaName.equals(value)) {
                return mode;
            }
        }
        throw new IllegalArgumentException("Unknown input activation mode: " + value);
    }
}
