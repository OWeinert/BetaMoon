package betamoon.luaapi.particle;

import betamoon.client.particles.ParticleSprite;
import betamoon.luaapi.utils.LuaCallbackScope;
import betamoon.luaapi.utils.LuaPositionArguments;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

/**
 * Scoped presentation setters; no world mutations or saved entity handle
 * escape.
 */
public final class LuaParticleAccess {
    private LuaParticleAccess() {
    }

    public static LuaTable create(LuaCallbackScope scope, ParticleSprite particle) {
        LuaTable api = new LuaTable();
        getter(api, scope, "getAge", particle::age);
        getter(api, scope, "getLifetime", particle::lifetime);
        getter(api, scope, "getRotation", () -> particle.rotation);
        getter(api, scope, "getSize", () -> particle.size);
        getter(api, scope, "getAlpha", () -> particle.color[3]);
        setter(api, scope, "setRotation", -1000000, 1000000, value -> particle.rotation = value);
        setter(api, scope, "setSize", 0, 16, value -> particle.size = value);
        setter(api, scope, "setAlpha", 0, 1, value -> particle.color[3] = value);
        setter(api, scope, "setGravity", -1, 1, value -> particle.gravity = value);
        setter(api, scope, "setDrag", 0, 1, value -> particle.drag = value);
        api.set("getPosition", new VarArgFunction() {
            public Varargs invoke(Varargs args) {
                scope.requireActive();
                return vector(particle.posX, particle.posY, particle.posZ);
            }
        });
        api.set("getVelocity", new VarArgFunction() {
            public Varargs invoke(Varargs args) {
                scope.requireActive();
                return vector(particle.motionX, particle.motionY, particle.motionZ);
            }
        });
        api.set("getColor", new VarArgFunction() {
            public Varargs invoke(Varargs args) {
                scope.requireActive();
                LuaTable color = new LuaTable();
                String[] channels = {"r", "g", "b", "a"};
                for (int i = 0; i < channels.length; i++) {
                    color.set(channels[i], particle.color[i]);
                }
                return color;
            }
        });
        api.set("setColor", new VarArgFunction() {
            public Varargs invoke(Varargs args) {
                scope.requireMutable();
                if (argument(args, api, 1).isnil()) {
                    throw new LuaError("particle.setColor: color is required");
                }
                double[] color = ParticleDeclarations.color(argument(args, api, 1), "particle.setColor");
                System.arraycopy(color, 0, particle.color, 0, 4);
                return NONE;
            }
        });
        api.set("setVelocity", new VarArgFunction() {
            public Varargs invoke(Varargs args) {
                scope.requireMutable();
                LuaPositionArguments.Position position = LuaPositionArguments.vector(args, api, 1,
                        "particle.setVelocity");
                double x = position.x;
                double y = position.y;
                double z = position.z;
                if (Math.abs(x) > 16 || Math.abs(y) > 16 || Math.abs(z) > 16) {
                    throw new LuaError("particle.setVelocity: components must be within -16..16");
                }
                particle.velocity(x, y, z);
                return NONE;
            }
        });
        api.set("remove", new VarArgFunction() {
            public Varargs invoke(Varargs args) {
                scope.requireMutable();
                particle.setEntityDead();
                return NONE;
            }
        });
        return api;
    }

    private static void getter(LuaTable api, LuaCallbackScope scope, String name, DoubleSupplier value) {
        api.set(name, new VarArgFunction() {
            public Varargs invoke(Varargs args) {
                scope.requireActive();
                return valueOf(value.getAsDouble());
            }
        });
    }

    private static void setter(LuaTable api, LuaCallbackScope scope, String name, double min, double max,
            DoubleConsumer consumer) {
        api.set(name, new VarArgFunction() {
            public Varargs invoke(Varargs args) {
                scope.requireMutable();
                LuaValue value = argument(args, api, 1);
                if (value.isnil()) {
                    throw new LuaError("particle." + name + ": value is required");
                }
                consumer.accept(ParticleDeclarations.bounded(value, 0, "particle." + name, min, max));
                return NONE;
            }
        });
    }

    private static LuaTable vector(double x, double y, double z) {
        LuaTable result = new LuaTable();
        result.set("x", x);
        result.set("y", y);
        result.set("z", z);
        return result;
    }

    public static LuaValue argument(Varargs args, LuaValue api, int index) {
        return args.arg(index + (args.arg1() == api ? 1 : 0));
    }
}
