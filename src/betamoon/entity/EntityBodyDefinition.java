package betamoon.entity;

import org.luaj.vm2.LuaValue;

import static betamoon.luaapi.utils.LuaDeclarationValues.bool;
import static betamoon.luaapi.utils.LuaDeclarationValues.fields;

/** Native body remains the movement/collision body; ray targeting is optional. */
public final class EntityBodyDefinition {
    public final boolean targetable;

    public EntityBodyDefinition(LuaValue value, EntityKind kind) {
        fields(value, "entity.body", "targetable");
        targetable = bool(value.get("targetable"), "entity.body.targetable", kind != EntityKind.PICKUP);
    }
}
