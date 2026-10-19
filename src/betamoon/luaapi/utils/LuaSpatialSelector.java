package betamoon.luaapi.utils;

import betamoon.worldgen.spatial.SpatialRegistry;
import betamoon.worldgen.spatial.SpatialSelector;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;

/** Structural Lua input adapter shared by position-aware declaration APIs. */
public final class LuaSpatialSelector {
    private LuaSpatialSelector() {
    }

    public static SpatialSelector read(LuaValue value, String path) {
        if (value.isnil()) {
            return null;
        }
        try {
            return new SpatialSelector(LuaDataSnapshot.object(value, path), path, SpatialRegistry.pendingSnapshot());
        } catch (IllegalArgumentException exception) {
            throw new LuaError(exception.getMessage());
        }
    }
}
