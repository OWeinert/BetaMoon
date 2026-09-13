package betamoon.luaapi.item;

import static betamoon.luaapi.utils.LuaDeclarationValues.error;

/**
 * Controls whether an inventory callback runs for every stack or only the
 * selected one.
 */
public enum InventoryTickMode {
    ALWAYS("always"), SELECTED("selected");

    private final String luaName;

    InventoryTickMode(String luaName) {
        this.luaName = luaName;
    }

    public boolean accepts(boolean selected) {
        return this == ALWAYS || selected;
    }

    public static InventoryTickMode parse(String name) {
        for (InventoryTickMode mode : values()) {
            if (mode.luaName.equals(name)) {
                return mode;
            }
        }
        throw error("onInventoryTick.when", "expected always or selected");
    }
}
