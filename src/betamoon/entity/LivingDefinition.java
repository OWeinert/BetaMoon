package betamoon.entity;

import java.util.Locale;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import static betamoon.luaapi.utils.LuaDeclarationValues.fields;
import static betamoon.luaapi.utils.LuaDeclarationValues.integer;
import static betamoon.luaapi.utils.LuaDeclarationValues.number;
import static betamoon.luaapi.utils.LuaDeclarationValues.string;
import static betamoon.luaapi.utils.LuaDeclarationValues.bool;

/** Native living statistics and a safe default movement behavior. */
public final class LivingDefinition {
    public enum Ai {
        IDLE,
        WANDER,
        DIRECTED,
        MANUAL
    }

    public enum Aggression {
        NONE,
        RETALIATE,
        PLAYERS
    }

    public final int maxHealth;
    public final float movementSpeed;
    public final Ai ai;
    public final LuaValue routine;
    public final int routineIntervalTicks;
    public final EntityAiStageDefinition targetStage;
    public final EntityAiStageDefinition pathStage;
    public final EntityAiStageDefinition attackStage;
    public final EntityAiStageDefinition movementStage;
    public final Aggression aggression;
    public final int attackDamage;
    public final int attackCooldownTicks;
    public final float targetRange;
    public final boolean despawn;

    public LivingDefinition(LuaValue value) {
        fields(value, "entity.living", "maxHealth", "movementSpeed", "ai", "aggression", "attackDamage",
                "attackCooldownTicks", "targetRange", "despawn");
        maxHealth = value.get("maxHealth").isnil() ? 20
                : integer(value.get("maxHealth"), "entity.living.maxHealth", 1, 32767);
        double speed = value.get("movementSpeed").isnil() ? 0.7
                : number(value.get("movementSpeed"), "entity.living.movementSpeed");
        if (speed < 0 || speed > 4) {
            throw new IllegalArgumentException("entity.living.movementSpeed must be between 0 and 4");
        }
        movementSpeed = (float) speed;
        LuaValue aiValue = value.get("ai");
        String preset;
        LuaValue stages = LuaValue.NIL;
        if (aiValue.istable()) {
            fields(aiValue, "entity.living.ai", "mode", "preset", "routine", "intervalTicks", "stages");
            String mode = aiValue.get("mode").isnil() ? "native"
                    : string(aiValue.get("mode"), "entity.living.ai.mode");
            if ("manual".equals(mode)) {
                if (!aiValue.get("preset").isnil()) {
                    throw new IllegalArgumentException("entity.living.ai.preset requires mode = 'native'");
                }
                preset = "manual";
            } else if ("native".equals(mode)) {
                preset = aiValue.get("preset").isnil() ? "directed"
                        : string(aiValue.get("preset"), "entity.living.ai.preset");
            } else {
                throw new IllegalArgumentException("entity.living.ai.mode must be native or manual");
            }
            routine = aiValue.get("routine");
            if (!routine.isnil() && !routine.isfunction()) {
                throw new IllegalArgumentException("entity.living.ai.routine must be a function");
            }
            routineIntervalTicks = aiValue.get("intervalTicks").isnil() ? 1
                    : integer(aiValue.get("intervalTicks"), "entity.living.ai.intervalTicks", 1, 1200);
            stages = aiValue.get("stages");
            if (!stages.isnil() && "manual".equals(mode)) {
                throw new IllegalArgumentException("entity.living.ai.stages requires mode = 'native'");
            }
        } else {
            preset = aiValue.isnil() ? "idle" : string(aiValue, "entity.living.ai");
            if ("manual".equals(preset)) {
                throw new IllegalArgumentException("entity.living.ai manual mode requires { mode = 'manual' }");
            }
            routine = LuaValue.NIL;
            routineIntervalTicks = 1;
        }
        try {
            ai = Ai.valueOf(preset.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException("entity.living.ai must use idle, wander, directed, or manual mode");
        }
        if (!stages.isnil()) {
            fields(stages, "entity.living.ai.stages", "target", "path", "attack", "movement");
            targetStage = stage(stages, "target");
            pathStage = stage(stages, "path");
            attackStage = stage(stages, "attack");
            movementStage = stage(stages, "movement");
        } else {
            targetStage = pathStage = attackStage = movementStage = null;
        }
        String targetMode = value.get("aggression").isnil()
                ? "none" : string(value.get("aggression"), "entity.living.aggression");
        try {
            aggression = Aggression.valueOf(targetMode.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException("entity.living.aggression must be none, retaliate, or players");
        }
        if (((ai == Ai.IDLE && targetStage == null) || ai == Ai.MANUAL)
                && aggression != Aggression.NONE) {
            throw new IllegalArgumentException("entity.living.aggression requires native wander or directed AI");
        }
        attackDamage = value.get("attackDamage").isnil() ? 2
                : integer(value.get("attackDamage"), "entity.living.attackDamage", 0, 32767);
        attackCooldownTicks = value.get("attackCooldownTicks").isnil() ? 20
                : integer(value.get("attackCooldownTicks"), "entity.living.attackCooldownTicks", 1, 1200);
        double range = value.get("targetRange").isnil() ? 16
                : number(value.get("targetRange"), "entity.living.targetRange");
        if (range <= 0 || range > 32) {
            throw new IllegalArgumentException("entity.living.targetRange must be > 0 and <= 32");
        }
        targetRange = (float) range;
        despawn = bool(value.get("despawn"), "entity.living.despawn", false);
    }

    public boolean composed() {
        return targetStage != null;
    }

    private static EntityAiStageDefinition stage(LuaValue stages, String name) {
        LuaValue value = stages.get(name);
        return new EntityAiStageDefinition(value.isnil() ? new LuaTable() : value, name);
    }
}
