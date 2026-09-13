package betamoon.luaapi.block;

import betamoon.luaapi.item.ItemInteractionRouting;
import betamoon.luaapi.utils.InteractionOutcome;
import betamoon.luaapi.utils.LuaOverrideCallback;
import betamoon.luamodloader.ScriptResourceTracker;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.src.Block;
import net.minecraft.src.Entity;
import net.minecraft.src.EntityPlayer;
import net.minecraft.src.ItemStack;
import net.minecraft.src.World;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import static betamoon.luaapi.utils.LuaDeclarationValues.error;

/** Registers block declarations and routes block lifecycle callbacks. */
public final class BlockCallbackRegistry {
    private static final Map<Integer, BlockDefinition> DEFINITIONS = new HashMap<Integer, BlockDefinition>();
    private static final Map<Integer, String> SAVED_SCHEMAS = new HashMap<Integer, String>();
    private static final Map<World, Map<String, Integer>> INPUTS = new WeakHashMap<World, Map<String, Integer>>();
    private static final Map<World, Set<String>> ACTIVE_NEIGHBORS = new WeakHashMap<World, Set<String>>();
    private static final Map<World, Set<String>> DEFERRED_NEIGHBORS = new WeakHashMap<World, Set<String>>();

    private BlockCallbackRegistry() {
    }

    public static BlockDefinition get(int id) {
        return DEFINITIONS.get(Integer.valueOf(id));
    }

    public static void validateIdentity(int id, BlockDefinition definition) {
        String old = SAVED_SCHEMAS.get(id);
        if (old != null && !old.equals(definition.state.signature)) {
            throw error("state", "schema changed; restart Minecraft (existing worlds require compatible metadata)");
        }
    }

    public static void install(final int id, final BlockDefinition def) {
        validateIdentity(id, def);
        SAVED_SCHEMAS.put(id, def.state.signature);
        BlockFireRegistration.apply(id, def.fireSpread, def.fireBurn);
        DEFINITIONS.put(id, def);
        Block.blocksList[id].slipperiness = def.shapes.slipperiness;
        Block.opaqueCubeLookup[id] = Block.blocksList[id].isOpaqueCube();
        ScriptResourceTracker.track(new ScriptResourceTracker.Cleanup() {
            public void run() {
                if (DEFINITIONS.get(id) == def) {
                    DEFINITIONS.remove(id);
                    BlockFireRegistration.apply(id, 0, 0);
                    Block.opaqueCubeLookup[id] = Block.blocksList[id].isOpaqueCube();
                }
            }
        });
    }

    public static void event(int id, BlockCallback callback, World world, int x, int y, int z, EntityPlayer player,
            Entity entity) {
        event(id, callback, world, x, y, z, player, entity, world.getBlockMetadata(x, y, z), -1);
    }

    public static void event(int id, BlockCallback callback, World world, int x, int y, int z, EntityPlayer player,
            Entity entity, int metadata, int face) {
        BlockDefinition def = get(id);
        if (def == null || !def.callbacks.has(callback) || world.multiplayerWorld) {
            return;
        }
        try (LuaBlockActionContext ctx = new LuaBlockActionContext(world, x, y, z, player,
                player == null ? null : player.getCurrentEquippedItem(), face, true, id, metadata)) {
            if (entity != null) {
                ctx.set("entity", ctx.entityAccess(entity));
            }
            def.callbacks.call(callback, ctx, LuaValue.NIL);
        }
    }

    public static InteractionOutcome activate(int id, World world, int x, int y, int z, EntityPlayer player) {
        BlockDefinition def = get(id);
        if (def == null || !def.callbacks.has(BlockCallback.ACTIVATE)) {
            return InteractionOutcome.PASS;
        }
        if (world.multiplayerWorld) {
            return InteractionOutcome.HANDLED;
        }
        try (LuaBlockActionContext ctx = new LuaBlockActionContext(world, x, y, z, player,
                player.getCurrentEquippedItem(), -1, true)) {
            InteractionOutcome result = def.callbacks.interaction(BlockCallback.ACTIVATE, ctx);
            if (result == InteractionOutcome.DENY) {
                ItemInteractionRouting.deny();
            }
            return result;
        }
    }

    /**
     * A nil result keeps declared/vanilla drops; an empty list deliberately drops
     * nothing.
     */
    public static List<ItemStack> drops(int id, World world, int x, int y, int z, int metadata, float chance) {
        BlockDefinition definition = get(id);
        if (definition == null || !definition.callbacks.has(BlockCallback.GET_DROPS)) {
            return null;
        }
        try (LuaBlockActionContext context = new LuaBlockActionContext(world, x, y, z, null, null, -1, false, id,
                metadata)) {
            LuaValue result = definition.callbacks.call(BlockCallback.GET_DROPS, context, LuaValue.NIL);
            if (result.isnil()) {
                return null;
            }
            BlockDropDefinition drops = new BlockDropDefinition(result);
            drops.validateRegistered();
            return drops.sample(world.rand, chance);
        } catch (RuntimeException error) {
            definition.callbacks.fail(BlockCallback.GET_DROPS, error);
            return null;
        }
    }

    public static void neighbor(World world, int x, int y, int z, int id, int neighborId) {
        if (world.multiplayerWorld) {
            return;
        }
        String position = x + ":" + y + ":" + z;
        Set<String> active = positions(ACTIVE_NEIGHBORS, world);
        if (!active.add(position)) {
            if (positions(DEFERRED_NEIGHBORS, world).add(position)) {
                world.scheduleBlockUpdate(x, y, z, id, 1);
            }
            return;
        }
        try {
            dispatchNeighbor(world, x, y, z, id, neighborId);
        } finally {
            active.remove(position);
        }
    }

    public static void recheckNeighbors(World world, int x, int y, int z, int id) {
        Set<String> deferred = DEFERRED_NEIGHBORS.get(world);
        if (deferred != null && deferred.remove(x + ":" + y + ":" + z)) {
            neighbor(world, x, y, z, id, id);
        }
    }

    private static Set<String> positions(Map<World, Set<String>> registry, World world) {
        Set<String> positions = registry.get(world);
        if (positions == null) {
            positions = new HashSet<String>();
            registry.put(world, positions);
        }
        return positions;
    }

    private static void dispatchNeighbor(World world, int x, int y, int z, int id, int neighborId) {
        BlockDefinition definition = get(id);
        if (definition == null || world.multiplayerWorld) {
            return;
        }
        if (definition.placement.dropUnsupported) {
            int face = definition.state.has("attachedFace")
                    ? BlockFaces.side(definition.state.get(world.getBlockMetadata(x, y, z), "attachedFace").tojstring(),
                            -1)
                    : 1;
            BlockFace attachedFace = BlockFace.fromNative(face);
            if (!world.isBlockSolidOnSide(x - attachedFace.xOffset, y - attachedFace.yOffset, z - attachedFace.zOffset,
                    face)) {
                Block.blocksList[id].dropBlockAsItem(world, x, y, z, world.getBlockMetadata(x, y, z));
                world.setBlockWithNotify(x, y, z, 0);
                return;
            }
        }
        LuaOverrideCallback inputOverride = BlockCallbackOverrides.get(id, BlockCallback.INPUT_CHANGED);
        if (inputOverride == null && !definition.callbacks.has(BlockCallback.INPUT_CHANGED)
                && !definition.callbacks.has(BlockCallback.NEIGHBOR_CHANGED)) {
            return;
        }
        try (LuaBlockActionContext ctx = new LuaBlockActionContext(world, x, y, z, null, null, -1, true)) {
            ctx.set("neighborId", neighborId);
            definition.callbacks.call(BlockCallback.NEIGHBOR_CHANGED, ctx, LuaValue.NIL);
            if (inputOverride == null && !definition.callbacks.has(BlockCallback.INPUT_CHANGED)
                    || world.getBlockId(x, y, z) != id) {
                return;
            }
            Map<String, Integer> states = INPUTS.get(world);
            if (states == null) {
                // Eviction produces an initial observation next time; no live Lua objects
                // or correctness-critical machine state are held in this cache.
                states = new LinkedHashMap<String, Integer>(128, 0.75F, true) {
                    protected boolean removeEldestEntry(Map.Entry<String, Integer> eldest) {
                        return size() > 8192;
                    }
                };
                INPUTS.put(world, states);
            }
            int mask = 0;
            for (BlockFace face : BlockFace.values()) {
                if (world.isBlockIndirectlyProvidingPowerTo(x + face.xOffset, y + face.yOffset, z + face.zOffset,
                        face.nativeSide)) {
                    mask |= 1 << face.nativeSide;
                }
            }
            String key = x + ":" + y + ":" + z;
            Integer old = states.put(key, mask);
            if (old == null || old.intValue() != mask) {
                LuaTable previous = new LuaTable();
                LuaTable current = new LuaTable();
                for (BlockFace face : BlockFace.values()) {
                    previous.set(face.luaName, LuaValue.valueOf(old != null && (old & (1 << face.nativeSide)) != 0));
                    current.set(face.luaName, LuaValue.valueOf((mask & (1 << face.nativeSide)) != 0));
                }
                ctx.set("previous", previous);
                ctx.set("current", current);
                ctx.set("initial", LuaValue.valueOf(old == null));
                if (inputOverride == null) {
                    definition.callbacks.call(BlockCallback.INPUT_CHANGED, ctx, LuaValue.NIL);
                } else {
                    inputOverride.invoke(ctx,
                            () -> definition.callbacks.call(BlockCallback.INPUT_CHANGED, ctx, LuaValue.NIL),
                            LuaOverrideCallback.Result.EVENT);
                }
            }
        }
    }

    public static void removed(World world, int x, int y, int z) {
        Set<String> deferred = DEFERRED_NEIGHBORS.get(world);
        if (deferred != null) {
            deferred.remove(x + ":" + y + ":" + z);
        }
        Map<String, Integer> states = INPUTS.get(world);
        if (states != null) {
            states.remove(x + ":" + y + ":" + z);
        }
    }

    public static boolean canPlace(int id, BlockDefinition definition, World world, int x, int y, int z, int side) {
        if (!definition.placement.canPlace(world, x, y, z, side)) {
            return false;
        }
        if (!definition.callbacks.has(BlockCallback.CAN_PLACE)) {
            return true;
        }
        try (LuaBlockActionContext context = new LuaBlockActionContext(world, x, y, z, null, null, side, false, id,
                definition.state.defaults)) {
            return definition.callbacks.query(BlockCallback.CAN_PLACE, context, false);
        }
    }
}
