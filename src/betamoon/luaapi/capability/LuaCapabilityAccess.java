package betamoon.luaapi.capability;

import betamoon.BetaMoonCommon;
import betamoon.assets.AssetKey;
import betamoon.capability.CapabilityAttachmentDefinition;
import betamoon.capability.CapabilityDefinition;
import betamoon.capability.CapabilityInstance;
import betamoon.capability.CapabilityOperationDefinition;
import betamoon.data.DataRecords;
import betamoon.luaapi.tileentity.LuaTileDataAccess;
import betamoon.luaapi.utils.LuaCallbackScope;
import betamoon.luaapi.world.LuaWorldActionAccess;
import betamoon.luamodloader.LuaScriptErrors;
import betamoon.tileentity.LuaTileEntity;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.src.TileEntity;
import net.minecraft.src.World;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

/** Scoped local data access and stale-safe external operation proxies for tile capabilities. */
public final class LuaCapabilityAccess {
    private static final int MAX_CALL_DEPTH = 16;
    private static final int MAX_CALLS_PER_TICK = 4096;
    private static final ThreadLocal<Integer> DEPTH = new ThreadLocal<>();
    private static final Map<World, Budget> BUDGETS = new WeakHashMap<>();

    private LuaCapabilityAccess() {
    }

    public static void installWorld(final LuaTable worldApi, final LuaCallbackScope scope, final World world) {
        worldApi.set("getCapability", new VarArgFunction() {
            public Varargs invoke(Varargs arguments) {
                scope.requireActive();
                int offset = arguments.arg1() == worldApi ? 1 : 0;
                LuaValue first = arguments.arg(1 + offset);
                int x;
                int y;
                int z;
                int capabilityIndex;
                if (first.istable()) {
                    x = coordinate(first.get("x"), "getCapability.position.x");
                    y = integer(first.get("y"), 0, 127, "getCapability.position.y");
                    z = coordinate(first.get("z"), "getCapability.position.z");
                    capabilityIndex = 2 + offset;
                } else {
                    x = coordinate(first, "getCapability.x");
                    y = integer(arguments.arg(2 + offset), 0, 127, "getCapability.y");
                    z = coordinate(arguments.arg(3 + offset), "getCapability.z");
                    capabilityIndex = 4 + offset;
                }
                CapabilityDefinition definition = CapabilitiesApi.definition(
                        arguments.arg(capabilityIndex), "getCapability.capability");
                String face = face(arguments.arg(capabilityIndex + 1));
                return external(scope, world, x, y, z, definition, face, false);
            }
        });
    }

    public static LuaTable local(final LuaCallbackScope scope, final LuaTileEntity tile) {
        final LuaTable registry = new LuaTable();
        registry.set("get", localLookup(registry, scope, tile, false));
        registry.set("getRequired", localLookup(registry, scope, tile, true));
        return registry;
    }

    public static LuaTable networkProxy(LuaCallbackScope scope, CapabilityInstance instance) {
        LuaTileEntity tile = instance.tile;
        return proxy(scope, tile.worldObj, tile.xCoord, tile.yCoord, tile.zCoord,
                tile, instance, null, false);
    }

    private static VarArgFunction localLookup(final LuaTable registry, final LuaCallbackScope scope,
            final LuaTileEntity tile, final boolean required) {
        return new VarArgFunction() {
            public Varargs invoke(Varargs arguments) {
                scope.requireActive();
                CapabilityDefinition definition = CapabilitiesApi.definition(
                        arguments.arg(arguments.arg1() == registry ? 2 : 1), "capabilities");
                CapabilityInstance instance = tile.getCapability(definition.key);
                if (instance == null) {
                    if (required) {
                        throw new LuaError("Tile does not implement capability " + definition.key + ".");
                    }
                    return NIL;
                }
                return proxy(scope, tile.worldObj, tile.xCoord, tile.yCoord, tile.zCoord,
                        tile, instance, null, true);
            }
        };
    }

    private static LuaValue external(LuaCallbackScope scope, World world, int x, int y, int z,
            CapabilityDefinition definition, String face, boolean required) {
        if (!world.blockExists(x, y, z)) {
            return LuaValue.NIL;
        }
        TileEntity value = world.getBlockTileEntity(x, y, z);
        if (!(value instanceof LuaTileEntity)) {
            return LuaValue.NIL;
        }
        LuaTileEntity tile = (LuaTileEntity) value;
        CapabilityInstance instance = tile.getCapability(definition.key);
        if (instance == null || Boolean.FALSE.equals(instance.attachment.port(face, tile))) {
            if (required) {
                throw new LuaError("Capability is unavailable at the target tile.");
            }
            return LuaValue.NIL;
        }
        return proxy(scope, world, x, y, z, tile, instance, face, false);
    }

    private static LuaTable proxy(LuaCallbackScope scope, World world, int x, int y, int z,
            LuaTileEntity tile, CapabilityInstance instance, String face, boolean local) {
        LuaTable proxy = new LuaTable();
        proxy.set("key", instance.attachment.capability.key.toString());
        proxy.set("position", position(x, y, z));
        if (face != null) {
            proxy.set("face", face);
            Object port = instance.attachment.port(face, tile);
            proxy.set("port", port instanceof String ? LuaValue.valueOf((String) port) : LuaValue.FALSE);
        }
        proxy.set("config", DataRecords.write(instance.attachment.capability.config, instance.attachment.config));
        if (local) {
            proxy.set("data", data(scope, instance, true));
        }
        proxy.set("call", new Call(proxy, scope, world, x, y, z, tile, instance, face));
        return proxy;
    }

    private static LuaTable data(final LuaCallbackScope scope, final CapabilityInstance instance,
            final boolean mutable) {
        final LuaTable data = new LuaTable();
        data.set("get", new VarArgFunction() {
            public Varargs invoke(Varargs arguments) {
                scope.requireActive();
                return instance.data.get(arguments.arg(arguments.arg1() == data ? 2 : 1).checkjstring());
            }
        });
        data.set("set", new VarArgFunction() {
            public Varargs invoke(Varargs arguments) {
                scope.requireActive();
                int offset = arguments.arg1() == data ? 1 : 0;
                instance.data.set(arguments.arg(1 + offset).checkjstring(), arguments.arg(2 + offset),
                        mutable && scope.isMutable());
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

    private static LuaTable context(LuaCallbackScope scope, CapabilityInstance instance, String face,
            boolean mutable) {
        LuaTileEntity tile = instance.tile;
        LuaTable context = new LuaTable();
        context.set("x", tile.xCoord);
        context.set("y", tile.yCoord);
        context.set("z", tile.zCoord);
        context.set("position", position(tile.xCoord, tile.yCoord, tile.zCoord));
        context.set("world", LuaWorldActionAccess.create(scope, tile.worldObj, tile.xCoord, tile.yCoord, tile.zCoord));
        LuaTable capability = new LuaTable();
        capability.set("key", instance.attachment.capability.key.toString());
        capability.set("data", data(scope, instance, mutable));
        capability.set("config", DataRecords.write(
                instance.attachment.capability.config, instance.attachment.config));
        if (face != null) {
            capability.set("face", face);
            Object port = instance.attachment.port(face, tile);
            capability.set("port", port instanceof String ? LuaValue.valueOf((String) port) : LuaValue.FALSE);
        }
        context.set("capability", capability);
        LuaTable entity = new LuaTable();
        entity.set("data", LuaTileDataAccess.create(scope, tile));
        entity.set("capabilities", local(scope, tile));
        context.set("entity", entity);
        return context;
    }

    private static LuaValue invoke(LuaCallbackScope scope, CapabilityInstance instance, String face,
            String operationName, LuaValue requestValue) {
        CapabilityOperationDefinition operation = instance.attachment.capability.operations.get(operationName);
        if (operation == null) {
            throw new LuaError("Unknown capability operation: " + operationName);
        }
        if (instance.isDisabled(operationName)) {
            throw new LuaError("Capability operation is disabled after an earlier callback error: " + operationName);
        }
        if (operation.mode == CapabilityOperationDefinition.Mode.ACTION) {
            scope.requireMutable();
        }
        consume(instance.tile.worldObj);
        int depth = DEPTH.get() == null ? 0 : DEPTH.get().intValue();
        if (depth >= MAX_CALL_DEPTH) {
            throw new LuaError("Capability call depth exceeds " + MAX_CALL_DEPTH + ".");
        }
        Map<String, Object> request = DataRecords.read(
                operation.request, requestValue, "capability request", true);
        Map<String, Object> checkpoint = instance.data.checkpoint();
        DEPTH.set(Integer.valueOf(depth + 1));
        boolean mutable = operation.mode == CapabilityOperationDefinition.Mode.ACTION && scope.isMutable();
        try (LuaCallbackScope operationScope = new LuaCallbackScope(mutable)) {
            LuaValue response = instance.attachment.operations.get(operationName).call(
                    context(operationScope, instance, face, mutable),
                    DataRecords.write(operation.request, request));
            Map<String, Object> parsed = DataRecords.read(
                    operation.response, response, "capability response", true);
            return DataRecords.write(operation.response, parsed);
        } catch (Throwable error) {
            instance.data.restore(checkpoint);
            instance.disable(operationName);
            String detail = error.getMessage() == null ? error.toString() : error.getMessage();
            String message = "capability " + instance.attachment.capability.key + " operation '"
                    + operationName + "' was disabled after an error: " + detail;
            LuaScriptErrors.add(instance.attachment.capability.owner, message);
            BetaMoonCommon.LOGGER.warning(instance.attachment.capability.owner + ": " + message);
            throw error instanceof LuaError ? (LuaError) error : new LuaError(message);
        } finally {
            if (depth == 0) {
                DEPTH.remove();
            } else {
                DEPTH.set(Integer.valueOf(depth));
            }
        }
    }

    private static synchronized void consume(World world) {
        Budget budget = BUDGETS.get(world);
        long tick = world.getWorldTime();
        if (budget == null || budget.tick != tick) {
            budget = new Budget(tick);
            BUDGETS.put(world, budget);
        }
        if (++budget.calls > MAX_CALLS_PER_TICK) {
            throw new LuaError("Capability call budget exceeded for this world tick.");
        }
    }

    private static String face(LuaValue value) {
        if (value.isnil()) {
            return null;
        }
        String face = value.checkjstring().toLowerCase();
        if (!("north".equals(face) || "south".equals(face) || "east".equals(face) || "west".equals(face)
                || "up".equals(face) || "down".equals(face))) {
            throw new LuaError("Capability face must be north, south, east, west, up, or down.");
        }
        return face;
    }

    private static int coordinate(LuaValue value, String path) {
        return integer(value, -30000000, 30000000, path);
    }

    private static int integer(LuaValue value, int minimum, int maximum, String path) {
        int result = value.checkint();
        if (result < minimum || result > maximum) {
            throw new LuaError(path + " must be between " + minimum + " and " + maximum + ".");
        }
        return result;
    }

    private static LuaTable position(int x, int y, int z) {
        LuaTable result = new LuaTable();
        result.set("x", x);
        result.set("y", y);
        result.set("z", z);
        return result;
    }

    private static final class Call extends VarArgFunction {
        private final LuaTable receiver;
        private final LuaCallbackScope scope;
        private final World world;
        private final int x;
        private final int y;
        private final int z;
        private final LuaTileEntity expected;
        private final CapabilityInstance expectedInstance;
        private final String face;

        private Call(LuaTable receiver, LuaCallbackScope scope, World world, int x, int y, int z,
                LuaTileEntity expected, CapabilityInstance expectedInstance, String face) {
            this.receiver = receiver;
            this.scope = scope;
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
            this.expected = expected;
            this.expectedInstance = expectedInstance;
            this.face = face;
        }

        public Varargs invoke(Varargs arguments) {
            scope.requireActive();
            int offset = arguments.arg1() == receiver ? 1 : 0;
            if (!world.blockExists(x, y, z) || world.getBlockTileEntity(x, y, z) != expected
                    || expected.func_31006_g()
                    || expected.getCapability(expectedInstance.attachment.capability.key) != expectedInstance
                    || Boolean.FALSE.equals(expectedInstance.attachment.port(face, expected))) {
                throw new LuaError("Capability handle is stale or no longer available.");
            }
            String operation = arguments.arg(1 + offset).checkjstring();
            return LuaCapabilityAccess.invoke(scope, expectedInstance, face, operation, arguments.arg(2 + offset));
        }
    }

    private static final class Budget {
        private final long tick;
        private int calls;
        private Budget(long tick) {
            this.tick = tick;
        }
    }
}
