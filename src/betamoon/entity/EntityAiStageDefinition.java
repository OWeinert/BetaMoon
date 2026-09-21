package betamoon.entity;

import org.luaj.vm2.LuaValue;

import static betamoon.luaapi.utils.LuaDeclarationValues.fields;
import static betamoon.luaapi.utils.LuaDeclarationValues.integer;
import static betamoon.luaapi.utils.LuaDeclarationValues.string;

/** One independently owned decision in a composed living AI. */
public final class EntityAiStageDefinition {
    public enum Mode {
        NATIVE,
        MANUAL
    }

    public final Mode mode;
    public final LuaValue routine;
    public final int intervalTicks;

    public EntityAiStageDefinition(LuaValue value, String name) {
        fields(value, "entity.living.ai.stages." + name, "mode", "routine", "intervalTicks");
        String selected = value.get("mode").isnil() ? "native"
                : string(value.get("mode"), "entity.living.ai.stages." + name + ".mode");
        if (!"native".equals(selected) && !"manual".equals(selected)) {
            throw new IllegalArgumentException("entity.living.ai.stages." + name + ".mode must be native or manual");
        }
        mode = "manual".equals(selected) ? Mode.MANUAL : Mode.NATIVE;
        routine = value.get("routine");
        if (mode == Mode.MANUAL && !routine.isfunction()) {
            throw new IllegalArgumentException("entity.living.ai.stages." + name
                    + ".routine is required for manual mode");
        }
        if (mode == Mode.NATIVE && !routine.isnil()) {
            throw new IllegalArgumentException("entity.living.ai.stages." + name
                    + ".routine requires manual mode");
        }
        if (mode == Mode.NATIVE && !value.get("intervalTicks").isnil()) {
            throw new IllegalArgumentException("entity.living.ai.stages." + name
                    + ".intervalTicks requires manual mode");
        }
        intervalTicks = value.get("intervalTicks").isnil() ? 1
                : integer(value.get("intervalTicks"), "entity.living.ai.stages." + name + ".intervalTicks",
                        1, 1200);
    }
}
