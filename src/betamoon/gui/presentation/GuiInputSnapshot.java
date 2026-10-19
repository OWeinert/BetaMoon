package betamoon.gui.presentation;

import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

/** Copies only the public primitive input fields, without retaining callback contexts or handles. */
public final class GuiInputSnapshot {
    private static final String[] FIELDS = {"source", "phase", "button", "wheel", "x", "y", "deltaX", "deltaY",
            "key", "character", "shift", "control", "conflicted"};

    private GuiInputSnapshot() {
    }

    public static LuaTable copy(LuaTable input) {
        LuaTable copy = new LuaTable();
        for (String field : FIELDS) {
            LuaValue value = input.get(field);
            if (value.isboolean() || value.type() == LuaValue.TNUMBER || value.type() == LuaValue.TSTRING) {
                copy.set(field, value);
            }
        }
        return copy;
    }
}
