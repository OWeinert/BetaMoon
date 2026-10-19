package betamoon.luaapi.utils;

import betamoon.dimension.DimensionSelector;
import java.util.Set;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;

/** Lua structural adapter shared by dimension-aware declaration APIs. */
public final class LuaDimensionSelectors {
    private LuaDimensionSelectors() {
    }

    public static Set<String> parse(LuaValue value, String path) {
        try {
            return DimensionSelector.parse(value.isnil() ? null : LuaDataSnapshot.value(value, path, 30000000), path);
        } catch (IllegalArgumentException exception) {
            throw new LuaError(exception.getMessage());
        }
    }
}
