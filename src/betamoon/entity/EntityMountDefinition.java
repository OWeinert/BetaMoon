package betamoon.entity;

import org.luaj.vm2.LuaValue;

import static betamoon.luaapi.utils.LuaDeclarationValues.bool;
import static betamoon.luaapi.utils.LuaDeclarationValues.error;
import static betamoon.luaapi.utils.LuaDeclarationValues.fields;
import static betamoon.luaapi.utils.LuaDeclarationValues.number;

/** Single-seat native riding capability. */
public final class EntityMountDefinition {
    public final double passengerOffset;
    public final boolean allowPlayers;
    public final boolean allowEntities;

    public EntityMountDefinition(LuaValue value, float height) {
        fields(value, "entity.mount", "passengerOffset", "allowPlayers", "allowEntities");
        passengerOffset = value.get("passengerOffset").isnil()
                ? height * 0.75 : number(value.get("passengerOffset"), "entity.mount.passengerOffset");
        if (passengerOffset < -16 || passengerOffset > 16) {
            throw error("entity.mount.passengerOffset", "expected -16..16 blocks");
        }
        allowPlayers = bool(value.get("allowPlayers"), "entity.mount.allowPlayers", true);
        allowEntities = bool(value.get("allowEntities"), "entity.mount.allowEntities", false);
        if (!allowPlayers && !allowEntities) {
            throw error("entity.mount", "must allow players, entities, or both");
        }
    }
}
