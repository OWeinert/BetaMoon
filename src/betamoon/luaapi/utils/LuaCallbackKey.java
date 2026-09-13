package betamoon.luaapi.utils;

/** Stable Lua name owned by a domain-specific callback enum. */
@FunctionalInterface
public interface LuaCallbackKey {
    String luaName();
}
