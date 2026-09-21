package betamoon.entity;

import org.luaj.vm2.LuaValue;

import static betamoon.luaapi.utils.LuaDeclarationValues.bool;
import static betamoon.luaapi.utils.LuaDeclarationValues.error;
import static betamoon.luaapi.utils.LuaDeclarationValues.fields;
import static betamoon.luaapi.utils.LuaDeclarationValues.number;

/** Type-level rendering defaults; per-instance overrides remain transient. */
public final class EntityRenderDefinition {
    public final boolean visible;
    public final float shadowRadius;
    public final boolean fireOverlay;
    public final boolean pickupBobbing;
    public final boolean pickupSpin;

    public EntityRenderDefinition(LuaValue value, EntityKind kind) {
        fields(value, "entity.render", "visible", "shadowRadius", "fireOverlay", "pickupBobbing", "pickupSpin");
        visible = bool(value.get("visible"), "entity.render.visible", true);
        shadowRadius = bounded(value.get("shadowRadius"), 0, 16,
                kind == EntityKind.PICKUP ? 0.15 : 0, "entity.render.shadowRadius");
        fireOverlay = bool(value.get("fireOverlay"), "entity.render.fireOverlay", true);
        if (kind != EntityKind.PICKUP
                && (!value.get("pickupBobbing").isnil() || !value.get("pickupSpin").isnil())) {
            throw error("entity.render", "pickupBobbing and pickupSpin require kind = 'pickup'");
        }
        pickupBobbing = bool(value.get("pickupBobbing"), "entity.render.pickupBobbing", true);
        pickupSpin = bool(value.get("pickupSpin"), "entity.render.pickupSpin", true);
    }

    private static float bounded(LuaValue value, double min, double max, double fallback, String path) {
        double parsed = value.isnil() ? fallback : number(value, path);
        if (parsed < min || parsed > max) {
            throw error(path, "expected " + min + ".." + max);
        }
        return (float) parsed;
    }
}
