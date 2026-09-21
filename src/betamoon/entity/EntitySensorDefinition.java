package betamoon.entity;

import org.luaj.vm2.LuaValue;

import static betamoon.luaapi.utils.LuaDeclarationValues.error;
import static betamoon.luaapi.utils.LuaDeclarationValues.fields;
import static betamoon.luaapi.utils.LuaDeclarationValues.integer;
import static betamoon.luaapi.utils.LuaDeclarationValues.required;
import static betamoon.luaapi.utils.LuaDeclarationValues.string;

/** A named server-side overlap sensor, independent of rendered bones and native collision. */
public final class EntitySensorDefinition {
    public enum Filter {
        ALL,
        PLAYERS,
        LIVING
    }

    public final String name;
    public final EntityShapeDefinition shape;
    public final Filter filter;
    public final int intervalTicks;
    public final LuaValue onEnter;
    public final LuaValue onStay;
    public final LuaValue onLeave;

    public EntitySensorDefinition(String name, LuaValue value) {
        this.name = name;
        String path = "entity.sensors." + name;
        fields(value, path, "shape", "filter", "intervalTicks", "onEnter", "onStay", "onLeave");
        shape = new EntityShapeDefinition(required(value, "shape"), path + ".shape");
        String selected = value.get("filter").isnil() ? "all" : string(value.get("filter"), path + ".filter");
        switch (selected) {
            case "all":
                filter = Filter.ALL;
                break;
            case "players":
                filter = Filter.PLAYERS;
                break;
            case "living":
                filter = Filter.LIVING;
                break;
            default:
                throw error(path + ".filter", "expected all, players, or living");
        }
        intervalTicks = value.get("intervalTicks").isnil() ? 5
                : integer(value.get("intervalTicks"), path + ".intervalTicks", 1, 1200);
        onEnter = callback(value, "onEnter", path);
        onStay = callback(value, "onStay", path);
        onLeave = callback(value, "onLeave", path);
        if (onEnter.isnil() && onStay.isnil() && onLeave.isnil()) {
            throw error(path, "at least one sensor callback is required");
        }
    }

    private static LuaValue callback(LuaValue value, String name, String path) {
        LuaValue result = value.get(name);
        if (!result.isnil() && !result.isfunction()) {
            throw error(path + "." + name, "expected a function");
        }
        return result;
    }
}
