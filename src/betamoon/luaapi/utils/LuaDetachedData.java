package betamoon.luaapi.utils;

import java.util.List;
import java.util.Map;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

/**
 * Copies trusted, already bounded diagnostic values into independent Lua
 * tables.
 */
public final class LuaDetachedData {
    private LuaDetachedData() {
    }

    public static LuaValue copy(Object value) {
        if (value == null) {
            return LuaValue.NIL;
        }
        if (value instanceof Boolean) {
            return LuaValue.valueOf((Boolean) value);
        }
        if (value instanceof Number) {
            return LuaValue.valueOf(((Number) value).doubleValue());
        }
        if (value instanceof String) {
            return LuaValue.valueOf((String) value);
        }
        LuaTable result = new LuaTable();
        if (value instanceof List) {
            for (Object entry : (List<?>) value) {
                result.set(result.length() + 1, copy(entry));
            }
        } else if (value instanceof Map) {
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                result.set((String) entry.getKey(), copy(entry.getValue()));
            }
        } else {
            throw new IllegalArgumentException("Unsupported detached diagnostic value");
        }
        return result;
    }
}
