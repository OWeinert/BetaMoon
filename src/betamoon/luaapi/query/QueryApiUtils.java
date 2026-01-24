package betamoon.luaapi.query;

import betamoon.luaapi.LuaApiUtils;
import betamoon.query.QueryFailure;
import org.luaj.vm2.LuaValue;

final class QueryApiUtils {
    private QueryApiUtils() {
    }

    static LuaValue pushWarning(QueryFailure warning) {
        LuaApiUtils.warn("Query API", warning.getMessage() + ".\n\n" + warning.getTree());
        return LuaValue.NIL;
    }

    static LuaValue pushNil(String message) {
        LuaApiUtils.warn("Query API", message + " (returning nil)");
        return LuaValue.NIL;
    }

    static int readDamageFromHandle(LuaValue handle) {
        if (handle.istable()) {
            LuaValue getter = handle.get("getDamage");
            if (!getter.isnil()) {
                return getter.call(handle).checkint();
            }
        }
        return 0;
    }
}
