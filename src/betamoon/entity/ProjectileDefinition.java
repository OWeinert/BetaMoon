package betamoon.entity;

import org.luaj.vm2.LuaValue;
import static betamoon.luaapi.utils.LuaDeclarationValues.fields;
import static betamoon.luaapi.utils.LuaDeclarationValues.integer;
import static betamoon.luaapi.utils.LuaDeclarationValues.number;
import static betamoon.luaapi.utils.LuaDeclarationValues.bool;

/** Immutable projectile simulation policy, independent of its model. */
public final class ProjectileDefinition {
    public final double speed;
    public final double gravity;
    public final double drag;
    public final int lifetimeTicks;
    public final int damage;
    public final int ownerGraceTicks;
    public final boolean alignToVelocity;

    public ProjectileDefinition(LuaValue value) {
        fields(value, "entity.projectile", "speed", "gravity", "drag", "lifetimeTicks", "damage",
                "ownerGraceTicks", "alignToVelocity");
        speed = bounded(value.get("speed"), 1.5, 0.001, 16, "entity.projectile.speed");
        gravity = bounded(value.get("gravity"), 0.03, 0, 1, "entity.projectile.gravity");
        drag = bounded(value.get("drag"), 0.99, 0, 1, "entity.projectile.drag");
        lifetimeTicks = value.get("lifetimeTicks").isnil() ? 80
                : integer(value.get("lifetimeTicks"), "entity.projectile.lifetimeTicks", 1, 1000000);
        damage = value.get("damage").isnil() ? 0
                : integer(value.get("damage"), "entity.projectile.damage", 0, 32767);
        ownerGraceTicks = value.get("ownerGraceTicks").isnil() ? 5
                : integer(value.get("ownerGraceTicks"), "entity.projectile.ownerGraceTicks", 0, 1000);
        alignToVelocity = bool(value.get("alignToVelocity"), "entity.projectile.alignToVelocity", true);
    }

    private static double bounded(LuaValue value, double fallback, double min, double max, String field) {
        if (value.isnil()) {
            return fallback;
        }
        double parsed = number(value, field);
        if (parsed < min || parsed > max) {
            throw new IllegalArgumentException(field + " must be between " + min + " and " + max);
        }
        return parsed;
    }
}
