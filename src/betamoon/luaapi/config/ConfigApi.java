package betamoon.luaapi.config;

import betamoon.BetaMoonCommon;
import betamoon.io.BetaMoonDirectories;
import betamoon.io.IoUtils;
import betamoon.luamodloader.LuaScriptErrors;
import betamoon.luamodloader.ScriptResourceTracker;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;
import org.luaj.vm2.lib.ZeroArgFunction;

/**
 * Callable Lua config namespace backed by the instance's BetaMoon content
 * folder.
 */
public final class ConfigApi {
    private ConfigApi() {
    }

    public static void attach(LuaTable module, File configRoot, String owner) {
        LuaTable namespace = new LuaTable();
        LuaTable metatable = new LuaTable();
        metatable.set("__call", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                if ((args.narg() != 3 && args.narg() != 4) || args.arg1() != namespace) {
                    throw new LuaError("Use betamoon.config(key, defaults[, options])");
                }
                String name = args.arg(2).checkjstring();
                LuaTable defaultsTable = args.arg(3).checktable();
                LuaValue options = args.narg() == 4 ? args.arg(4) : LuaValue.NIL;
                Map<String, Object> defaults = ScriptConfigValues.snapshot(defaultsTable,
                        ScriptConfigMetadata.defaultHints(defaultsTable, options));
                ScriptConfigKey key = ScriptConfigKey.parse(name);
                File root = configRoot == null ? defaultRoot() : configRoot;
                String source = owner == null ? "<unowned>" : owner;
                ScriptConfigMetadata metadata = ScriptConfigMetadata.parse(key.toString(), defaults, options);
                ScriptConfigStore store = new ScriptConfigStore(root.toPath(), key, defaults, metadata.fields);
                ScriptConfigRegistry.checkAvailable(source, key.toString(), store);
                Map<String, Object> initial;
                boolean malformed = false;
                try {
                    initial = store.open();
                } catch (IOException error) {
                    reportLoadError(owner, "Could not load config '" + key + "': " + error.getMessage()
                            + ". Using defaults without replacing the file.");
                    initial = store.defaults();
                    malformed = true;
                }
                ScriptConfig live = new ScriptConfig();
                ScriptConfigValues.update(live, ScriptConfigValues.toLua(initial).checktable());
                ScriptConfigRegistration registration = new ScriptConfigRegistration(source,
                        ScriptResourceTracker.ownerGeneration(source), key.toString(), store, metadata, live, initial,
                        malformed);
                live.install(registration);
                ScriptConfigRegistry.register(registration, store);
                return live;
            }
        });
        namespace.setmetatable(metatable);
        module.set("config", namespace);
    }

    private static File defaultRoot() {
        File instanceRoot = IoUtils.resolveMinecraftDirFromCodeSource(BetaMoonCommon.class);
        if (instanceRoot == null) {
            throw new LuaError("Cannot locate the Minecraft instance folder");
        }
        return BetaMoonDirectories.scriptConfigDirectoryForInstance(instanceRoot);
    }

    private static void reportLoadError(String owner, String message) {
        if (owner == null) {
            BetaMoonCommon.LOGGER.severe(message);
        } else {
            LuaScriptErrors.add(owner, message);
        }
    }

    private static final class ScriptConfig extends LuaTable {
        private void install(ScriptConfigRegistration registration) {
            LuaTable methods = new LuaTable();
            methods.set("save", new ZeroArgFunction() {
                @Override
                public LuaValue call() {
                    try {
                        registration.saveLua();
                        return TRUE;
                    } catch (IOException error) {
                        throw new LuaError("Could not save config '" + registration.key() + "': " + error.getMessage());
                    }
                }
            });
            methods.set("reload", new ZeroArgFunction() {
                @Override
                public LuaValue call() {
                    try {
                        registration.reloadLua();
                        return TRUE;
                    } catch (IOException error) {
                        throw new LuaError(
                                "Could not reload config '" + registration.key() + "': " + error.getMessage());
                    }
                }
            });
            LuaTable metatable = new LuaTable();
            metatable.set("__index", methods);
            metatable.set("__metatable", LuaValue.valueOf("BetaMoon config methods"));
            setmetatable(metatable);
        }

        @Override
        public void set(LuaValue key, LuaValue value) {
            if (key.isstring() && ("save".equals(key.tojstring()) || "reload".equals(key.tojstring()))) {
                throw new LuaError("Config field '" + key.tojstring() + "' is reserved for a config method");
            }
            super.set(key, value);
        }
    }
}
