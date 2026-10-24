package betamoon.luaapi.entity;

import betamoon.entity.modifier.EntityModifierCallbacks;
import betamoon.entity.modifier.EntityModifierContext;
import betamoon.entity.motion.EntityMotionCommand;
import betamoon.entity.motion.EntityMotionResult;
import betamoon.entity.motion.EntityMotionState;
import betamoon.entity.motion.EntityTransforms;
import betamoon.entity.motion.MotionVector;
import betamoon.luaapi.utils.LuaCallbackScope;
import betamoon.luaapi.utils.LuaDeclarationValues;
import betamoon.luaapi.utils.LuaDetachedData;
import betamoon.luaapi.world.LuaWorldActionAccess;
import betamoon.luamodloader.ScriptExecution;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.src.EntityPlayer;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

/**
 * Read-only invocation and result parsing; no native mutation is performed by a
 * Lua callback.
 */
final class LuaModifierCallbacks {
    private LuaModifierCallbacks() {
    }

    static EntityModifierCallbacks read(LuaValue table, String owner, EntityModifierCallbacks previous) {
        return new EntityModifierCallbacks(() -> new LuaTable(),
                callback(table, "onAttach", previous.onAttach, value -> new Attachment(owner, value)),
                callback(table, "onDetach", previous.onDetach, value -> new Detachment(owner, value)),
                callback(table, "onMotion", previous.onMotion, value -> new Motion(owner, value)),
                callback(table, "onForces", previous.onForces, value -> new Forces(owner, value)),
                callback(table, "onAfterMotion", previous.onAfterMotion, value -> new Observation(owner, value)));
    }

    static EntityModifierCallbacks clear(EntityModifierCallbacks previous, String path) {
        if (!path.equals("onAttach") && !path.equals("onDetach") && !path.equals("onMotion") && !path.equals("onForces")
                && !path.equals("onAfterMotion")) {
            throw LuaDeclarationValues.error("modifier.clear", "unknown rule/callback path: " + path);
        }
        return new EntityModifierCallbacks(previous.stateFactory, path.equals("onAttach") ? null : previous.onAttach,
                path.equals("onDetach") ? null : previous.onDetach, path.equals("onMotion") ? null : previous.onMotion,
                path.equals("onForces") ? null : previous.onForces,
                path.equals("onAfterMotion") ? null : previous.onAfterMotion);
    }

    private static <T> T callback(LuaValue table, String path, T previous, Function<LuaValue, T> factory) {
        LuaValue value = table.get(path);
        if (value.isnil()) {
            return previous;
        }
        if (!value.isfunction()) {
            throw LuaDeclarationValues.error("modifier." + path, "expected a function");
        }
        return previous instanceof OwnedCallback && ((OwnedCallback) previous).function == value
                ? previous
                : factory.apply(value);
    }

    private abstract static class OwnedCallback {
        private final String owner;
        private final LuaValue function;

        private OwnedCallback(String owner, LuaValue function) {
            this.owner = owner;
            this.function = function;
        }

        final LuaValue invoke(String phase, EntityModifierContext context, LuaValue event) {
            try (LuaCallbackScope scope = new LuaCallbackScope(false)) {
                LuaTable ctx = new LuaTable();
                ctx.set("tick", LuaValue.valueOf(context.tick));
                ctx.set("dt", LuaValue.valueOf(0.05));
                ctx.set("generation", LuaValue.valueOf(context.generation));
                ctx.set("state", context.state instanceof LuaValue ? (LuaValue) context.state : LuaValue.NIL);
                ctx.set("ownsMotion", LuaValue.valueOf(context.ownsMotion));
                ctx.set("movement", LuaDetachedData.copy(movement(context.movement)));
                ctx.set("input", LuaDetachedData.copy(context.input));
                ctx.set("nativeIntent", LuaDetachedData.copy(context.nativeIntent));
                if (context.entity != null) {
                    ctx.set("entity",
                            LuaEntityActionAccess.create(scope,
                                    context.entity instanceof EntityPlayer ? (EntityPlayer) context.entity : null,
                                    context.entity));
                    ctx.set("world",
                            LuaWorldActionAccess.create(scope, context.world, (int) Math.floor(context.entity.posX),
                                    (int) Math.floor(context.entity.posY), (int) Math.floor(context.entity.posZ)));
                }
                return ScriptExecution.invoke(owner, "modifier." + phase, function, LuaValue.varargsOf(ctx, event))
                        .arg1();
            }
        }
    }

    private static final class Attachment extends OwnedCallback implements EntityModifierCallbacks.Attachment {
        private Attachment(String owner, LuaValue function) {
            super(owner, function);
        }

        public void call(EntityModifierContext context) {
            invoke("onAttach", context, LuaValue.NIL);
        }
    }

    private static final class Detachment extends OwnedCallback implements EntityModifierCallbacks.Detachment {
        private Detachment(String owner, LuaValue function) {
            super(owner, function);
        }

        public void call(EntityModifierContext context, String reason) {
            LuaTable event = new LuaTable();
            event.set("reason", reason);
            invoke("onDetach", context, event);
        }
    }

    private static final class Motion extends OwnedCallback implements EntityModifierCallbacks.Motion {
        private Motion(String owner, LuaValue function) {
            super(owner, function);
        }

        public EntityMotionCommand call(EntityModifierContext context) {
            LuaValue result = invoke("onMotion", context, LuaValue.NIL);
            if (result.isnil()) {
                return null;
            }
            LuaDeclarationValues.fields(result, "modifier.onMotion", "mode", "velocity", "rotation", "fallDistance",
                    "rules");
            String mode = LuaDeclarationValues.string(result.get("mode"), "modifier.onMotion.mode");
            if (!mode.equals("native") && !mode.equals("manual")) {
                throw LuaDeclarationValues.error("modifier.onMotion.mode", "expected native or manual");
            }
            LuaValue rotation = result.get("rotation");
            if (!rotation.isnil()) {
                LuaDeclarationValues.fields(rotation, "modifier.onMotion.rotation", "yaw", "pitch");
            }
            return new EntityMotionCommand(
                    mode.equals("native") ? EntityMotionCommand.Mode.NATIVE : EntityMotionCommand.Mode.MANUAL,
                    result.get("velocity").isnil()
                            ? null
                            : vector(result.get("velocity"), "modifier.onMotion.velocity"),
                    angle(rotation, "yaw"), angle(rotation, "pitch"),
                    result.get("fallDistance").isnil()
                            ? null
                            : LuaDeclarationValues.number(result.get("fallDistance"), "modifier.onMotion.fallDistance"),
                    EntityRuleDeclarations.parse(result.get("rules"), "modifier.onMotion.rules"));
        }
    }

    private static final class Forces extends OwnedCallback implements EntityModifierCallbacks.Forces {
        private Forces(String owner, LuaValue function) {
            super(owner, function);
        }

        public MotionVector call(EntityModifierContext context) {
            LuaValue result = invoke("onForces", context, LuaValue.NIL);
            if (result.isnil()) {
                return null;
            }
            LuaDeclarationValues.fields(result, "modifier.onForces", "velocityDelta");
            return vector(result.get("velocityDelta"), "modifier.onForces.velocityDelta");
        }
    }

    private static final class Observation extends OwnedCallback implements EntityModifierCallbacks.Observation {
        private Observation(String owner, LuaValue function) {
            super(owner, function);
        }

        public void call(EntityModifierContext context, EntityMotionResult result) {
            Map<String, Object> fields = new LinkedHashMap<>(movement(result.movement));
            fields.put("sourceKey", result.sourceKey);
            fields.put("sourceOwner", result.sourceOwner);
            fields.put("mode", result.mode);
            fields.put("requestedDelta", vector(result.requestedDelta));
            fields.put("appliedDelta", vector(result.appliedDelta));
            fields.put("velocityDelta", vector(result.velocityDelta));
            fields.put("forceStatus", result.forceStatus);
            fields.put("blockedReason", result.blockedReason);
            if (context.entity != null) {
                String reason = EntityTransforms.supportAvailable(context.entity);
                if (reason == null) {
                    Map<String, Object> support = new LinkedHashMap<>();
                    support.put("supported", EntityTransforms.supported(context.entity));
                    support.put("probeDistance", 0.001);
                    fields.put("support", support);
                } else {
                    fields.put("supportReason", reason);
                }
            }
            invoke("onAfterMotion", context, LuaDetachedData.copy(fields));
        }
    }

    private static Float angle(LuaValue rotation, String key) {
        return rotation.isnil() || rotation.get(key).isnil()
                ? null
                : (float) LuaDeclarationValues.number(rotation.get(key), "modifier.onMotion.rotation." + key);
    }

    private static MotionVector vector(LuaValue value, String path) {
        LuaDeclarationValues.fields(value, path, "x", "y", "z");
        MotionVector result = new MotionVector(LuaDeclarationValues.number(value.get("x"), path + ".x"),
                LuaDeclarationValues.number(value.get("y"), path + ".y"),
                LuaDeclarationValues.number(value.get("z"), path + ".z"));
        if (!result.within(16)) {
            throw LuaDeclarationValues.error(path, "components must be within -16..16");
        }
        return result;
    }

    private static Map<String, Object> movement(EntityMotionState state) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (state == null) {
            return result;
        }
        result.put("position", vector(state.position));
        result.put("velocity", vector(state.velocity));
        Map<String, Object> rotation = new LinkedHashMap<>();
        rotation.put("yaw", state.yaw);
        rotation.put("pitch", state.pitch);
        result.put("rotation", rotation);
        result.put("fallDistance", state.fallDistance);
        result.put("onGround", state.onGround);
        result.put("collidedHorizontally", state.collidedHorizontally);
        result.put("collidedVertically", state.collidedVertically);
        result.put("inWater", state.inWater);
        result.put("inLava", state.inLava);
        result.put("mounted", state.mounted);
        result.put("sleeping", state.sleeping);
        return result;
    }

    private static Map<String, Object> vector(MotionVector vector) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("x", vector.x);
        result.put("y", vector.y);
        result.put("z", vector.z);
        return result;
    }
}
