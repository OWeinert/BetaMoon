package betamoon.luaapi.entity;

import betamoon.entity.motion.EntitySimulationThread;
import betamoon.entity.motion.EntityTransformRequest;
import betamoon.entity.motion.EntityTransforms;
import betamoon.entity.motion.MotionVector;
import betamoon.entity.motion.NativeEntityFields;
import betamoon.luaapi.utils.LuaCallbackScope;
import betamoon.luaapi.utils.LuaDeclarationValues;
import net.minecraft.src.AxisAlignedBB;
import net.minecraft.src.Entity;
import net.minecraft.src.EntityPlayer;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

/** Parsing and Lua snapshots for the shared entity transform actions. */
final class LuaEntityTransforms {
    private LuaEntityTransforms() {
    }

    static void attach(final LuaTable api, final LuaCallbackScope scope, final Entity entity) {
        api.set("getTransform", query(scope, () -> transform(entity)));
        api.set("getBounds", query(scope, () -> bounds(entity.boundingBox)));
        api.set("getMovementState", query(scope, () -> movement(entity)));
        api.set("getSupport", new VarArgFunction() {
            public Varargs invoke(Varargs args) {
                read(scope);
                String reason = EntityTransforms.supportAvailable(entity);
                return reason == null ? support(entity) : failure(reason);
            }
        });
        api.set("isMounted", query(scope, () -> LuaValue.valueOf(entity.ridingEntity != null)));
        api.set("isSleeping", query(scope, () -> LuaValue.valueOf(entity instanceof EntityPlayer
                && ((EntityPlayer) entity).isPlayerSleeping())));
        api.set("setTransform", new VarArgFunction() {
            public Varargs invoke(Varargs args) {
                write(scope);
                EntityTransformRequest request = request(argument(args, api, 1));
                try {
                    return outcome(EntityTransforms.transform(entity, request));
                } catch (IllegalArgumentException error) {
                    throw LuaDeclarationValues.error("setTransform", error.getMessage());
                }
            }
        });
        api.set("addVelocity", new VarArgFunction() {
            public Varargs invoke(Varargs args) {
                write(scope);
                MotionVector delta = vector(args, api, "addVelocity");
                try {
                    return outcome(EntityTransforms.impulse(entity, delta));
                } catch (IllegalArgumentException error) {
                    throw LuaDeclarationValues.error("addVelocity", error.getMessage());
                }
            }
        });
        api.set("moveBy", new VarArgFunction() {
            public Varargs invoke(Varargs args) {
                write(scope);
                MotionVector delta = vector(args, api, "moveBy");
                double x = entity.posX;
                double y = entity.posY;
                double z = entity.posZ;
                try {
                    String reason = EntityTransforms.move(entity, delta);
                    if (reason != null) {
                        return outcome(reason);
                    }
                    LuaTable result = collision(entity);
                    result.set("requestedDelta", vector(delta.x, delta.y, delta.z));
                    result.set("appliedDelta", vector(entity.posX - x, entity.posY - y, entity.posZ - z));
                    return LuaValue.varargsOf(LuaValue.TRUE, result);
                } catch (IllegalArgumentException error) {
                    throw LuaDeclarationValues.error("moveBy", error.getMessage());
                }
            }
        });
    }

    private static VarArgFunction query(final LuaCallbackScope scope, final Snapshot snapshot) {
        return new VarArgFunction() {
            public Varargs invoke(Varargs args) {
                read(scope);
                return snapshot.get();
            }
        };
    }

    private static void read(LuaCallbackScope scope) {
        scope.requireActive();
        try {
            EntitySimulationThread.requireCurrent();
        } catch (IllegalStateException error) {
            throw LuaDeclarationValues.error("entity", error.getMessage());
        }
    }

    private static void write(LuaCallbackScope scope) {
        scope.requireMutable();
        read(scope);
    }

    static LuaTable transform(Entity entity) {
        LuaTable result = new LuaTable();
        result.set("position", vector(entity.posX, entity.posY, entity.posZ));
        LuaTable rotation = new LuaTable();
        rotation.set("yaw", entity.rotationYaw);
        rotation.set("pitch", entity.rotationPitch);
        result.set("rotation", rotation);
        return result;
    }

    static LuaTable movement(Entity entity) {
        LuaTable result = collision(entity);
        result.set("position", vector(entity.posX, entity.posY, entity.posZ));
        result.set("velocity", vector(entity.motionX, entity.motionY, entity.motionZ));
        result.set("fallDistance", NativeEntityFields.fallDistance(entity));
        result.set("inWater", LuaValue.valueOf(entity.isInWater()));
        result.set("burning", LuaValue.valueOf(entity.isBurning()));
        result.set("mounted", LuaValue.valueOf(entity.ridingEntity != null));
        result.set("sleeping", LuaValue.valueOf(entity instanceof EntityPlayer
                && ((EntityPlayer) entity).isPlayerSleeping()));
        String reason = EntityTransforms.supportAvailable(entity);
        if (reason == null) {
            result.set("support", support(entity));
        } else {
            result.set("supportReason", reason);
        }
        return result;
    }

    private static LuaTable bounds(AxisAlignedBB area) {
        LuaTable result = new LuaTable();
        result.set("min", vector(area.minX, area.minY, area.minZ));
        result.set("max", vector(area.maxX, area.maxY, area.maxZ));
        result.set("width", area.maxX - area.minX);
        result.set("height", area.maxY - area.minY);
        result.set("depth", area.maxZ - area.minZ);
        return result;
    }

    private static LuaTable support(Entity entity) {
        LuaTable result = new LuaTable();
        result.set("supported", LuaValue.valueOf(EntityTransforms.supported(entity)));
        result.set("probeDistance", EntityTransforms.SUPPORT_PROBE);
        return result;
    }

    static LuaTable collision(Entity entity) {
        LuaTable result = new LuaTable();
        result.set("onGround", LuaValue.valueOf(entity.onGround));
        result.set("collidedHorizontally", LuaValue.valueOf(entity.isCollidedHorizontally));
        result.set("collidedVertically", LuaValue.valueOf(entity.isCollidedVertically));
        return result;
    }

    static LuaTable vector(double x, double y, double z) {
        LuaTable result = new LuaTable();
        result.set("x", x);
        result.set("y", y);
        result.set("z", z);
        return result;
    }

    private static EntityTransformRequest request(LuaValue value) {
        String path = "setTransform";
        LuaDeclarationValues.fields(value, path, "position", "rotation", "resetVelocity", "resetFallDistance",
                "interpolation");
        LuaValue position = value.get("position");
        LuaValue rotation = value.get("rotation");
        if (!position.isnil()) {
            LuaDeclarationValues.fields(position, path + ".position", "x", "y", "z");
        }
        if (!rotation.isnil()) {
            LuaDeclarationValues.fields(rotation, path + ".rotation", "yaw", "pitch");
        }
        String interpolation = value.get("interpolation").isnil() ? "snap"
                : LuaDeclarationValues.string(value.get("interpolation"), path + ".interpolation");
        if (!"snap".equals(interpolation) && !"preserve".equals(interpolation)) {
            throw LuaDeclarationValues.error(path + ".interpolation", "expected snap or preserve");
        }
        try {
            return new EntityTransformRequest(component(position, "x", path + ".position"),
                    component(position, "y", path + ".position"), component(position, "z", path + ".position"),
                    angle(rotation, "yaw", path + ".rotation"), angle(rotation, "pitch", path + ".rotation"),
                    LuaDeclarationValues.bool(value.get("resetVelocity"), path + ".resetVelocity", false),
                    LuaDeclarationValues.bool(value.get("resetFallDistance"), path + ".resetFallDistance", false),
                    "snap".equals(interpolation));
        } catch (IllegalArgumentException error) {
            throw LuaDeclarationValues.error(path, error.getMessage());
        }
    }

    private static Double component(LuaValue table, String key, String path) {
        if (table.isnil() || table.get(key).isnil()) {
            return null;
        }
        return LuaDeclarationValues.number(table.get(key), path + "." + key);
    }

    private static Float angle(LuaValue table, String key, String path) {
        Double number = component(table, key, path);
        return number == null ? null : Float.valueOf(number.floatValue());
    }

    private static MotionVector vector(Varargs args, LuaTable receiver, String path) {
        MotionVector result = new MotionVector(
                LuaDeclarationValues.number(argument(args, receiver, 1), path + ".x"),
                LuaDeclarationValues.number(argument(args, receiver, 2), path + ".y"),
                LuaDeclarationValues.number(argument(args, receiver, 3), path + ".z"));
        if (!result.within(EntityTransforms.MAX_MOTION)) {
            throw LuaDeclarationValues.error(path, "components must be within -16..16");
        }
        return result;
    }

    private static LuaValue argument(Varargs args, LuaTable receiver, int index) {
        return args.arg(index + (args.arg1() == receiver ? 1 : 0));
    }

    private static Varargs outcome(String reason) {
        return reason == null ? LuaValue.TRUE : LuaValue.varargsOf(LuaValue.FALSE, LuaValue.valueOf(reason));
    }

    private static Varargs failure(String reason) {
        return LuaValue.varargsOf(LuaValue.NIL, LuaValue.valueOf(reason));
    }

    private interface Snapshot {
        LuaValue get();
    }
}
