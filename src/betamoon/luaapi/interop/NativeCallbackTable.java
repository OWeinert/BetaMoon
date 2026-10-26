package betamoon.luaapi.interop;

import betamoon.luaapi.utils.LuaCallbackScope;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

/**
 * Context tables retain only their existing scope; closing it clears the native
 * entry.
 */
public class NativeCallbackTable extends LuaTable implements NativeCallbackContext {
    private final LuaCallbackScope nativeScope;

    public NativeCallbackTable(LuaCallbackScope scope) {
        this.nativeScope = scope;
    }

    public static LuaValue wrap(LuaCallbackScope scope, LuaValue value) {
        if (!value.istable()) {
            return value;
        }
        NativeCallbackTable result = new NativeCallbackTable(scope);
        LuaTable source = value.checktable();
        for (LuaValue key : source.keys()) {
            result.rawset(key, source.rawget(key));
        }
        return result;
    }

    @Override
    public LuaCallbackScope nativeCallbackScope() {
        return nativeScope;
    }
}
