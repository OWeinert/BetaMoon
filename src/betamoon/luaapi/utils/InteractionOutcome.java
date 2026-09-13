package betamoon.luaapi.utils;

import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;

/** Closed outcome of an item or block interaction callback. */
public enum InteractionOutcome {
    DENY("deny", -1), PASS("pass", 0), HANDLED("handled", 1);

    private final LuaValue luaValue;
    private final int nativeCode;

    InteractionOutcome(String luaName, int nativeCode) {
        luaValue = LuaValue.valueOf(luaName);
        this.nativeCode = nativeCode;
    }

    public LuaValue toLuaValue() {
        return luaValue;
    }

    public int toNativeCode() {
        return nativeCode;
    }

    public static InteractionOutcome fromLua(LuaValue value, String path) {
        if (value.isnil() || value.raweq(PASS.luaValue)) {
            return PASS;
        }
        if (value.raweq(HANDLED.luaValue)) {
            return HANDLED;
        }
        if (value.raweq(DENY.luaValue)) {
            return DENY;
        }
        throw new LuaError(path + ": return pass, handled, deny, or nil");
    }
}
