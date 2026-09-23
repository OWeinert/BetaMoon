package betamoon.luaapi.system;

import betamoon.assets.AssetKey;
import betamoon.data.DataSchema;
import betamoon.luaapi.utils.LuaDeclarationValues;
import betamoon.luamodloader.LuaScriptRegistry;
import betamoon.system.WorldServiceDefinition;
import betamoon.system.WorldServiceReference;
import betamoon.system.WorldServiceRegistry;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

/** Lua registration and lookup API for persistent per-world services. */
public final class SystemsApi {
    private SystemsApi() {
    }

    public static void attach(LuaTable root) {
        final LuaTable systems = new LuaTable();
        systems.set("add", new VarArgFunction() {
            public Varargs invoke(Varargs arguments) {
                return SystemsApi.add(argument(arguments, systems));
            }
        });
        systems.set("get", lookup(systems, false));
        systems.set("getRequired", lookup(systems, true));
        root.set("systems", systems);
    }

    public static WorldServiceDefinition definition(LuaValue value, String path) {
        if (value instanceof WorldServiceReference) {
            return ((WorldServiceReference) value).definition();
        }
        WorldServiceDefinition definition = WorldServiceRegistry.find(key(value, path));
        if (definition == null) {
            throw new LuaError(path + ": world service is not registered: " + value.tojstring());
        }
        return definition;
    }

    private static WorldServiceReference add(LuaValue declaration) {
        LuaDeclarationValues.fields(declaration, "system",
                "key", "data", "tick", "onLoad", "onTick", "onUnload");
        AssetKey key = key(LuaDeclarationValues.required(declaration, "key"), "system.key");
        if (!key.getPath().startsWith("system/")) {
            throw new LuaError("system.key must use namespace:system/name.");
        }
        String owner = LuaScriptRegistry.getCurrentScriptFile();
        if (owner == null) {
            throw new LuaError("systems:add is available only while a Lua mod is loading.");
        }
        int interval = 0;
        LuaValue tick = declaration.get("tick");
        if (!tick.isnil()) {
            LuaDeclarationValues.fields(tick, "system.tick", "interval");
            interval = LuaDeclarationValues.integer(
                    LuaDeclarationValues.required(tick, "interval"), "system.tick.interval", 1, 1200);
        }
        LuaValue onLoad = callback(declaration.get("onLoad"), "system.onLoad");
        LuaValue onTick = callback(declaration.get("onTick"), "system.onTick");
        LuaValue onUnload = callback(declaration.get("onUnload"), "system.onUnload");
        if (!onTick.isnil() && interval == 0) {
            throw new LuaError("system.tick is required when onTick is present.");
        }
        DataSchema data = DataSchema.parse(declaration.get("data"), "system.data");
        if (data.containsEntityReference()) {
            throw new LuaError("system.data: entity_reference is currently supported only by entity data.");
        }
        WorldServiceDefinition definition = new WorldServiceDefinition(
                key, owner, data, interval, onLoad, onTick, onUnload);
        try {
            WorldServiceRegistry.register(definition);
        } catch (IllegalArgumentException error) {
            throw new LuaError("systems:add: " + error.getMessage());
        }
        return new WorldServiceReference(definition);
    }

    private static VarArgFunction lookup(final LuaTable registry, final boolean required) {
        return new VarArgFunction() {
            public Varargs invoke(Varargs arguments) {
                LuaValue value = argument(arguments, registry);
                WorldServiceDefinition definition = WorldServiceRegistry.find(key(value, "systems"));
                if (definition == null) {
                    if (required) {
                        throw new LuaError("World service is not registered: " + value.tojstring());
                    }
                    return NIL;
                }
                return new WorldServiceReference(definition);
            }
        };
    }

    private static AssetKey key(LuaValue value, String path) {
        try {
            return AssetKey.parse(value.checkjstring());
        } catch (IllegalArgumentException error) {
            throw new LuaError(path + ": " + error.getMessage());
        }
    }

    private static LuaValue callback(LuaValue value, String path) {
        if (!value.isnil() && !value.isfunction()) {
            throw new LuaError(path + " must be a function.");
        }
        return value;
    }

    private static LuaValue argument(Varargs arguments, LuaValue receiver) {
        return arguments.arg(arguments.arg1() == receiver ? 2 : 1);
    }
}
