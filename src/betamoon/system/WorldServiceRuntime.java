package betamoon.system;

import betamoon.interop.runtime.NativeEventAccess;
import betamoon.luaapi.interop.NativeCallbackTable;

import betamoon.data.DataStore;
import betamoon.dimension.DimensionRuntime;
import betamoon.luaapi.utils.LuaCallbackScope;
import betamoon.luaapi.world.LuaWorldActionAccess;
import betamoon.luamodloader.LuaScriptErrors;
import betamoon.luamodloader.ScriptExecution;
import betamoon.networking.LogicalNetworkRuntime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.src.ChunkCoordinates;
import net.minecraft.src.World;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

/**
 * Owns one persistent runtime instance per declared service and authoritative
 * world.
 */
public final class WorldServiceRuntime {
    private static final String STORAGE_KEY = "betamoon_systems";
    private static final Map<World, RuntimeWorld> WORLDS = new WeakHashMap<>();

    private WorldServiceRuntime() {
    }

    public static synchronized void load(World world) {
        if (world == null || world.multiplayerWorld || WORLDS.containsKey(world)
                || DimensionRuntime.isPreparing(world)) {
            return;
        }
        BetaMoonWorldData storage = storedData(world);
        RuntimeWorld runtime = new RuntimeWorld(world, storage);
        WORLDS.put(world, runtime);
        runtime.load();
    }

    public static synchronized void tick(World world) {
        if (world == null || world.multiplayerWorld) {
            return;
        }
        load(world);
        RuntimeWorld runtime = WORLDS.get(world);
        if (runtime != null) {
            runtime.tick();
            LogicalNetworkRuntime.tick(world);
        }
    }

    public static synchronized void unload(World world) {
        LogicalNetworkRuntime.unload(world);
        RuntimeWorld runtime = WORLDS.remove(world);
        if (runtime != null) {
            runtime.unload();
        }
    }

    /**
     * Runs unload persistence while the source is still active, before a checked
     * transfer save.
     */
    public static synchronized void prepareToLeave(World world) {
        LogicalNetworkRuntime.unload(world);
        RuntimeWorld runtime = WORLDS.get(world);
        if (runtime != null) {
            runtime.unload();
        }
    }

    public static synchronized void resumeCancelledLeave(World world) {
        RuntimeWorld runtime = WORLDS.get(world);
        if (runtime != null && runtime.preparedToLeave) {
            runtime.preparedToLeave = false;
            runtime.load();
        }
    }

    public static synchronized LuaValue snapshot(World world, WorldServiceDefinition definition) {
        load(world);
        RuntimeWorld runtime = WORLDS.get(world);
        Instance instance = runtime == null ? null : runtime.instances.get(definition.key.toString());
        return instance == null ? LuaValue.NIL : instance.data.snapshot();
    }

    public static synchronized BetaMoonWorldData storage(World world) {
        if (world != null && !world.multiplayerWorld && DimensionRuntime.isPreparing(world)) {
            return storedData(world);
        }
        load(world);
        RuntimeWorld runtime = WORLDS.get(world);
        return runtime == null ? null : runtime.storage;
    }

    private static BetaMoonWorldData storedData(World world) {
        BetaMoonWorldData storage = (BetaMoonWorldData) world.loadItemData(BetaMoonWorldData.class, STORAGE_KEY);
        if (storage == null) {
            storage = new BetaMoonWorldData(STORAGE_KEY);
            world.setItemData(STORAGE_KEY, storage);
        }
        return storage;
    }

    private static LuaTable context(World world, Instance instance, LuaCallbackScope scope) {
        ChunkCoordinates spawn = world.getSpawnPoint();
        NativeEventAccess.configure(scope, world, null);
        LuaTable result = new NativeCallbackTable(scope);
        result.set("key", instance.definition.key.toString());
        result.set("world", LuaWorldActionAccess.create(scope, world, spawn.x, spawn.y, spawn.z));
        result.set("data", data(instance, scope));
        return result;
    }

    private static LuaTable data(final Instance instance, final LuaCallbackScope scope) {
        final LuaTable data = new LuaTable();
        data.set("get", new VarArgFunction() {
            public Varargs invoke(Varargs arguments) {
                scope.requireActive();
                return instance.data.get(arguments.arg(arguments.arg1() == data ? 2 : 1).checkjstring());
            }
        });
        data.set("set", new VarArgFunction() {
            public Varargs invoke(Varargs arguments) {
                scope.requireMutable();
                int offset = arguments.arg1() == data ? 1 : 0;
                instance.data.set(arguments.arg(1 + offset).checkjstring(), arguments.arg(2 + offset), true);
                return NIL;
            }
        });
        data.set("snapshot", new VarArgFunction() {
            public Varargs invoke(Varargs arguments) {
                scope.requireActive();
                return instance.data.snapshot();
            }
        });
        return data;
    }

    private static void invoke(World world, Instance instance, LuaValue callback, String name) {
        if (callback.isnil() || instance.disabled) {
            return;
        }
        try (LuaCallbackScope scope = new LuaCallbackScope(true)) {
            ScriptExecution.invoke(instance.definition.owner, "systems." + name, callback,
                    context(world, instance, scope));
        } catch (Throwable error) {
            instance.disabled = true;
            String detail = error.getMessage() == null ? error.toString() : error.getMessage();
            String message = "world service " + instance.definition.key + " " + name + " was disabled after an error: "
                    + detail;
            LuaScriptErrors.add(instance.definition.owner, message, LuaScriptErrors.Outcome.CALLBACK_DISABLED, error);
        }
    }

    private static final class RuntimeWorld {
        private final World world;
        private long simulationTicks;
        private boolean preparedToLeave;
        private final BetaMoonWorldData storage;
        private final Map<String, Instance> instances = new LinkedHashMap<>();

        private RuntimeWorld(World world, BetaMoonWorldData storage) {
            this.world = world;
            this.storage = storage;
            for (WorldServiceDefinition definition : WorldServiceRegistry.all()) {
                final String key = definition.key.toString();
                DataStore store = new DataStore(definition.data, new DataStore.ChangeListener() {
                    public void changed(String field) {
                        storage.markDirty();
                    }
                });
                if (storage.services().hasKey(key)) {
                    store.load(storage.services().getCompoundTag(key));
                }
                storage.services().setCompoundTag(key, store.raw());
                instances.put(key, new Instance(definition, store));
            }
        }

        private void load() {
            for (Instance instance : instances.values()) {
                invoke(world, instance, instance.definition.onLoad, "onLoad");
            }
        }

        private void tick() {
            if (preparedToLeave) {
                return;
            }
            long tick = ++simulationTicks;
            for (Instance instance : instances.values()) {
                if (instance.definition.tickInterval > 0 && tick % instance.definition.tickInterval == 0) {
                    invoke(world, instance, instance.definition.onTick, "onTick");
                }
            }
        }

        private void unload() {
            if (preparedToLeave) {
                return;
            }
            preparedToLeave = true;
            for (Instance instance : instances.values()) {
                invoke(world, instance, instance.definition.onUnload, "onUnload");
                storage.services().setCompoundTag(instance.definition.key.toString(), instance.data.raw());
            }
            storage.markDirty();
        }
    }

    private static final class Instance {
        private final WorldServiceDefinition definition;
        private final DataStore data;
        private boolean disabled;
        private Instance(WorldServiceDefinition definition, DataStore data) {
            this.definition = definition;
            this.data = data;
        }
    }
}
