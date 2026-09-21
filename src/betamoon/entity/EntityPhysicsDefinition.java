package betamoon.entity;

import org.luaj.vm2.LuaValue;
import static betamoon.luaapi.utils.LuaDeclarationValues.bool;
import static betamoon.luaapi.utils.LuaDeclarationValues.fields;
import static betamoon.luaapi.utils.LuaDeclarationValues.number;
import static betamoon.luaapi.utils.LuaDeclarationValues.string;

/** Motion mode for every entity kind, with optional prop-specific simulation parameters. */
public final class EntityPhysicsDefinition {
    public enum Mode {
        STATIC,
        DYNAMIC
    }

    public final Mode mode;
    public final double gravity;
    public final double drag;
    public final double groundFriction;
    public final double bounce;
    public final boolean pushable;

    public EntityPhysicsDefinition(LuaValue value, EntityKind kind) {
        fields(value, "entity.physics", "mode", "gravity", "drag", "groundFriction", "bounce", "pushable");
        String selected = value.get("mode").isnil() ? kind == EntityKind.PROP ? "static" : "dynamic"
                : string(value.get("mode"), "entity.physics.mode");
        if ("static".equals(selected)) {
            mode = Mode.STATIC;
        } else if ("dynamic".equals(selected)) {
            mode = Mode.DYNAMIC;
        } else {
            throw new IllegalArgumentException("entity.physics.mode must be static or dynamic");
        }
        if (kind != EntityKind.PROP && (!value.get("gravity").isnil() || !value.get("drag").isnil()
                || !value.get("groundFriction").isnil() || !value.get("bounce").isnil()
                || !value.get("pushable").isnil())) {
            throw new IllegalArgumentException("entity.physics: gravity, drag, groundFriction, bounce and pushable "
                    + "are prop-only settings");
        }
        gravity = bounded(value.get("gravity"), 0.04, 0, 1, "gravity");
        drag = bounded(value.get("drag"), 0.98, 0, 1, "drag");
        groundFriction = bounded(value.get("groundFriction"), 0.6, 0, 1, "groundFriction");
        bounce = bounded(value.get("bounce"), 0, 0, 1, "bounce");
        pushable = bool(value.get("pushable"), "entity.physics.pushable", false);
    }

    private static double bounded(LuaValue value, double fallback, double min, double max, String name) {
        if (value.isnil()) {
            return fallback;
        }
        double parsed = number(value, "entity.physics." + name);
        if (parsed < min || parsed > max) {
            throw new IllegalArgumentException("entity.physics." + name + " must be " + min + ".." + max);
        }
        return parsed;
    }
}
