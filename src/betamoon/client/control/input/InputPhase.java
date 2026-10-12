package betamoon.client.control.input;

/** One bounded transition delivered to a named action callback. */
public enum InputPhase {
    PRESSED("pressed"), RELEASED("released");

    private final String luaName;

    InputPhase(String luaName) {
        this.luaName = luaName;
    }

    public String luaName() {
        return luaName;
    }
}
