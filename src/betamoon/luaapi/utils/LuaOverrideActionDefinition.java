package betamoon.luaapi.utils;

import org.luaj.vm2.LuaValue;

/** Common callback metadata used by layered override dispatch. */
public interface LuaOverrideActionDefinition {
    String getName();

    String getOwner();

    LuaValue getAction();
}
