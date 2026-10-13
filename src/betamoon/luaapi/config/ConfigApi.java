package betamoon.luaapi.config;

import betamoon.BetaMoonCommon;
import betamoon.io.IoUtils;
import betamoon.luamodloader.LuaScriptErrors;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;
import org.luaj.vm2.lib.ZeroArgFunction;

/** Callable Lua config namespace backed by the instance's shared config folder. */
public final class ConfigApi {
    private ConfigApi() {
    }

    public static void attach(LuaTable module, File configRoot, String owner) {
        LuaTable namespace = new LuaTable();
        LuaTable metatable = new LuaTable();
        metatable.set("__call", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                if (args.narg() != 3 || args.arg1() != namespace) {
                    throw new LuaError("Use betamoon.config(key, defaults)");
                }
                String name = args.arg(2).checkjstring();
                LuaTable defaultsTable = args.arg(3).checktable();
                Map<String, Object> defaults = ScriptConfigValues.snapshot(defaultsTable, null);
                ScriptConfigKey key = ScriptConfigKey.parse(name);
                File root = configRoot == null ? defaultRoot() : configRoot;
                ScriptConfigStore store = new ScriptConfigStore(root.toPath(), key, defaults);
                Map<String, Object> initial;
                try {
                    initial = store.open();
                } catch (IOException error) {
                    reportLoadError(owner, "Could not load config '" + key + "': " + error.getMessage()
                            + ". Using defaults without replacing the file.");
                    initial = store.defaults();
                }
                return new ScriptConfig(store, initial);
            }
        });
        namespace.setmetatable(metatable);
        module.set("config", namespace);
    }

    private static File defaultRoot() {
        File instanceRoot = IoUtils.resolveMinecraftDirFromCodeSource(BetaMoonCommon.class);
        if (instanceRoot == null) {
            throw new LuaError("Cannot locate the Minecraft instance config folder");
        }
        return new File(new File(instanceRoot, "config"), "betamoon");
    }

    private static void reportLoadError(String owner, String message) {
        if (owner == null) {
            BetaMoonCommon.LOGGER.severe(message);
        } else {
            LuaScriptErrors.add(owner, message);
        }
    }

    private static final class ScriptConfig extends LuaTable {
        private ScriptConfig(ScriptConfigStore store, Map<String, Object> initial) {
            ScriptConfigValues.update(this, ScriptConfigValues.toLua(initial).checktable());
            LuaTable methods = new LuaTable();
            methods.set("save", new ZeroArgFunction() {
                @Override
                public LuaValue call() {
                    try {
                        store.save(ScriptConfig.this);
                        return TRUE;
                    } catch (IOException error) {
                        throw new LuaError("Could not save config '" + store.fileName() + "': " + error.getMessage());
                    }
                }
            });
            methods.set("reload", new ZeroArgFunction() {
                @Override
                public LuaValue call() {
                    try {
                        store.reload(ScriptConfig.this);
                        return TRUE;
                    } catch (IOException error) {
                        throw new LuaError("Could not reload config '" + store.fileName() + "': " + error.getMessage());
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
