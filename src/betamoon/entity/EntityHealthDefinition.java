package betamoon.entity;

import org.luaj.vm2.LuaValue;

import static betamoon.luaapi.utils.LuaDeclarationValues.fields;
import static betamoon.luaapi.utils.LuaDeclarationValues.integer;
import static betamoon.luaapi.utils.LuaDeclarationValues.required;

/** Opt-in saved health for entities without Minecraft's living health implementation. */
public final class EntityHealthDefinition {
    public final int max;

    public EntityHealthDefinition(LuaValue value) {
        fields(value, "entity.health", "max");
        max = integer(required(value, "max"), "entity.health.max", 1, 32767);
    }
}
