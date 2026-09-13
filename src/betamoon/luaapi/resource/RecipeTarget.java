package betamoon.luaapi.resource;

import org.luaj.vm2.LuaValue;

/** Common override dispatch for native and custom recipe references. */
@FunctionalInterface
public interface RecipeTarget {
    LuaValue override(LuaValue definition);
}
