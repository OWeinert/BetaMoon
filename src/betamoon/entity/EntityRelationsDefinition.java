package betamoon.entity;

import betamoon.assets.AssetKey;
import org.luaj.vm2.LuaValue;

import static betamoon.luaapi.utils.LuaDeclarationValues.bool;
import static betamoon.luaapi.utils.LuaDeclarationValues.error;
import static betamoon.luaapi.utils.LuaDeclarationValues.fields;
import static betamoon.luaapi.utils.LuaDeclarationValues.string;

/** Optional saved owner and team identity for a prop or living entity. */
public final class EntityRelationsDefinition {
    public final boolean ownership;
    public final String team;

    public EntityRelationsDefinition(LuaValue value) {
        fields(value, "entity.relations", "ownership", "team");
        ownership = bool(value.get("ownership"), "entity.relations.ownership", false);
        if (value.get("team").isnil()) {
            team = "";
        } else {
            String parsed = string(value.get("team"), "entity.relations.team");
            try {
                team = AssetKey.parse(parsed).toString();
            } catch (IllegalArgumentException exception) {
                throw error("entity.relations.team", exception.getMessage());
            }
        }
        if (!ownership && team.isEmpty()) {
            throw error("entity.relations", "requires ownership = true or a team");
        }
    }
}
