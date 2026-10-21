package betamoon.luaapi.world;

import betamoon.atmosphere.AtmosphereRegistry;
import betamoon.instrumentation.api.RequiredNativeHooks;
import betamoon.luaapi.utils.LuaDataReference;
import betamoon.luaapi.utils.LuaDataSnapshot;
import betamoon.luaapi.utils.LuaPositionArguments;
import betamoon.luamodloader.LuaScriptRegistry;
import betamoon.luamodloader.ScriptResourceTracker;
import betamoon.runtime.RuntimeEnvironment;
import java.util.List;
import java.util.Map;
import net.minecraft.src.World;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

/** Client presentation declarations and callback-scoped world queries. */
public final class AtmospheresApi {
    private AtmospheresApi() {
    }

    public static void attach(LuaTable module) {
        LuaTable registry = new LuaTable();
        registry.set("add", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                try {
                    if (!RuntimeEnvironment.isDedicatedServer()) {
                        RequiredNativeHooks.require("Atmospheres", true, "betamoon:atmosphere:sky",
                                "betamoon:atmosphere:light_tone", "betamoon:fluid_camera:updateFogColor",
                                "betamoon:fluid_camera:setupFog", "betamoon:tick_events:client");
                    }
                    return new Reference(AtmosphereRegistry
                            .add(LuaDataSnapshot.object(argument(args, registry, 1), "atmosphere", 30000000)));
                } catch (IllegalArgumentException | IllegalStateException exception) {
                    throw new LuaError(exception.getMessage());
                }
            }
        });
        registry.set("bind", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                try {
                    AtmosphereRegistry.bind(LuaDataSnapshot.object(argument(args, registry, 1), "atmosphere.bind"));
                    return NONE;
                } catch (IllegalArgumentException | IllegalStateException exception) {
                    throw new LuaError(exception.getMessage());
                }
            }
        });
        for (String operation : new String[]{"get", "getRequired"}) {
            registry.set(operation, new VarArgFunction() {
                @Override
                public Varargs invoke(Varargs args) {
                    String key = argument(args, registry, 1).checkjstring();
                    if (AtmosphereRegistry.get(key) == null) {
                        if (operation.equals("getRequired")) {
                            throw new LuaError("Unknown atmosphere: " + key);
                        }
                        return NIL;
                    }
                    return new Reference(key);
                }
            });
        }
        registry.set("getActive", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                World world = LuaWorldActionAccess.requireWorld(argument(args, registry, 1));
                LuaPositionArguments.Position position = LuaPositionArguments.vector(args, registry, 2,
                        "atmosphere.getActive");
                double x = coordinate(position.x, "atmosphere.x");
                double y = coordinate(position.y, "atmosphere.y");
                double z = coordinate(position.z, "atmosphere.z");
                LuaTable result = new LuaTable();
                AtmosphereRegistry.Presentation presentation = AtmosphereRegistry.queryPresentation(world, x, y, z);
                result.set("profiles", contributions(presentation.profiles()));
                LuaTable channels = new LuaTable();
                String[] names = {"fog", "sky", "lightTone", "ambientSound", "weatherSound"};
                for (int index = 0; index < names.length; index++) {
                    channels.set(names[index], contributions(presentation.channel(index)));
                }
                result.set("channels", channels);
                return result;
            }
        });
        LuaTable client = module.get("client").istable() ? module.get("client").checktable() : new LuaTable();
        LuaTable visuals = client.get("visuals").istable() ? client.get("visuals").checktable() : new LuaTable();
        visuals.set("atmospheres", registry);
        client.set("visuals", visuals);
        module.set("client", client);
    }

    private static LuaTable contributions(List<AtmosphereRegistry.Contribution> values) {
        LuaTable result = new LuaTable();
        for (AtmosphereRegistry.Contribution value : values) {
            LuaTable entry = new LuaTable();
            entry.set("key", value.profile.key);
            entry.set("weight", value.weight);
            entry.set("priority", value.priority);
            result.set(result.length() + 1, entry);
        }
        return result;
    }

    private static double coordinate(double value, String path) {
        if (Math.abs(value) > 30000000) {
            throw new LuaError(path + ": coordinate exceeds world bounds");
        }
        return value;
    }

    private static LuaValue argument(Varargs args, LuaValue receiver, int index) {
        return args.arg(index + (args.arg1() == receiver ? 1 : 0));
    }

    private static final class Reference extends LuaTable implements LuaDataReference {
        private final String key;

        private Reference(String key) {
            this.key = key;
            set("key", key);
            set("getKey", new VarArgFunction() {
                @Override
                public Varargs invoke(Varargs args) {
                    return valueOf(key);
                }
            });
            set("activate", new VarArgFunction() {
                @Override
                public Varargs invoke(Varargs args) {
                    if (RuntimeEnvironment.isDedicatedServer()) {
                        return varargsOf(new LuaValue[]{NIL, valueOf("no_client")});
                    }
                    LuaValue input = argument(args, Reference.this, 1);
                    World world = LuaWorldActionAccess.requireWorld(input.get("world"));
                    LuaTable options = new LuaTable();
                    for (LuaValue field : input.checktable().keys()) {
                        if (!field.tojstring().equals("world")) {
                            options.set(field, input.get(field));
                        }
                    }
                    try {
                        Map<String, Object> values = LuaDataSnapshot.object(options, "atmosphere.activate", 30000000);
                        long id = AtmosphereRegistry.activate(LuaScriptRegistry.getCurrentScriptFile(), key, world,
                                values);
                        ScriptResourceTracker.track(() -> AtmosphereRegistry.close(id));
                        LuaTable handle = new LuaTable();
                        handle.set("close", new VarArgFunction() {
                            @Override
                            public Varargs invoke(Varargs args) {
                                AtmosphereRegistry.close(id);
                                return NONE;
                            }
                        });
                        handle.set("isActive", new VarArgFunction() {
                            @Override
                            public Varargs invoke(Varargs args) {
                                return valueOf(AtmosphereRegistry.isActive(id));
                            }
                        });
                        return handle;
                    } catch (IllegalArgumentException | IllegalStateException exception) {
                        throw new LuaError(exception.getMessage());
                    }
                }
            });
        }

        @Override
        public Object declarationValue() {
            return key;
        }
    }
}
