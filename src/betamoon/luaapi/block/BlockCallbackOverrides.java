package betamoon.luaapi.block;

import betamoon.luaapi.item.ItemInteractionRouting;
import betamoon.luaapi.utils.InteractionOutcome;
import betamoon.luaapi.resource.OverrideManager;
import betamoon.luaapi.utils.LuaOverrideCallback;
import betamoon.luaapi.utils.LuaOverrideCallback.Result;
import betamoon.luaapi.utils.LuaOverrideDefinition;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.WeakHashMap;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import net.minecraft.src.Block;
import net.minecraft.src.Entity;
import net.minecraft.src.EntityLiving;
import net.minecraft.src.EntityPlayer;
import net.minecraft.src.EntityItem;
import net.minecraft.src.ItemStack;
import net.minecraft.src.World;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.LuaTable;

/**
 * Effective block callback layers, separate from declarations and Lua context
 * implementation.
 */
public final class BlockCallbackOverrides {
    private static final Map<String, LuaOverrideCallback> CALLBACKS = new ConcurrentHashMap<String, LuaOverrideCallback>();
    private static final Map<World, Map<String, Integer>> INPUTS = new WeakHashMap<World, Map<String, Integer>>();

    private BlockCallbackOverrides() {
    }

    public static boolean supports(String name) {
        return BlockCallback.fromLuaName(name) != null;
    }

    public static LuaOverrideCallback get(int id, BlockCallback callbackId) {
        LuaOverrideCallback callback = CALLBACKS.get(key(id, callbackId));
        return callback != null && callback.isEnabled() ? callback : null;
    }

    public static OverrideManager.PropertyAdapter<Block, LuaOverrideDefinition> adapter(final String name) {
        final BlockCallback callbackId = BlockCallback.fromLuaName(name);
        return new OverrideManager.PropertyAdapter<Block, LuaOverrideDefinition>() {
            public LuaOverrideDefinition read(Block target) {
                LuaOverrideCallback callback = CALLBACKS.get(key(((Block) target).blockID, callbackId));
                return callback == null ? null : callback.definition;
            }

            public void write(Block target, LuaOverrideDefinition value) {
                String key = key(((Block) target).blockID, callbackId);
                if (value == null) {
                    CALLBACKS.remove(key);
                } else {
                    CALLBACKS.put(key, new LuaOverrideCallback((LuaOverrideDefinition) value));
                }
            }
        };
    }

    public static LuaValue invoke(int id, BlockCallback callbackId, World world,
            Supplier<LuaBlockActionContext> contextFactory, Supplier<LuaValue> original, Result contract) {
        LuaOverrideCallback callback = get(id, callbackId);
        if (callback == null || world.multiplayerWorld) {
            return original.get();
        }
        try (LuaBlockActionContext context = contextFactory.get()) {
            return callback.invoke(context, original, contract);
        }
    }

    private static String key(int id, BlockCallback callback) {
        return id + ":" + callback.name();
    }

    private static LuaBlockActionContext context(Block block, World world, int x, int y, int z, EntityPlayer player,
            Entity entity, int face, int metadata, boolean mutable) {
        LuaBlockActionContext context = new LuaBlockActionContext(world, x, y, z, player,
                player == null ? null : player.getCurrentEquippedItem(), face, mutable, block.blockID, metadata);
        if (entity != null) {
            context.set("entity", context.entityAccess(entity));
        }
        return context;
    }

    public static void tick(Block block, World world, int x, int y, int z, Random random) {
        LuaOverrideCallback callback = get(block.blockID, BlockCallback.TICK);
        if (callback == null || world.multiplayerWorld) {
            block.updateTick(world, x, y, z, random);
            return;
        }
        BlockTickRegistry.invokeTickOverride(callback, block, world, x, y, z, random);
    }

    public static void added(Block block, World world, int x, int y, int z) {
        invoke(block.blockID, BlockCallback.ADDED, world, () -> {
            LuaBlockActionContext context = context(block, world, x, y, z, null, null, -1,
                    world.getBlockMetadata(x, y, z), true);
            return context;
        }, () -> {
            block.onBlockAdded(world, x, y, z);
            return LuaValue.NIL;
        }, Result.EVENT);
    }

    public static void removed(Block block, World world, int x, int y, int z) {
        Map<String, Integer> inputs = INPUTS.get(world);
        if (inputs != null) {
            inputs.remove(block.blockID + ":" + x + ":" + y + ":" + z);
        }
        invoke(block.blockID, BlockCallback.REMOVED, world, () -> {
            LuaBlockActionContext context = context(block, world, x, y, z, null, null, -1,
                    world.getBlockMetadata(x, y, z), true);
            return context;
        }, () -> {
            block.onBlockRemoval(world, x, y, z);
            return LuaValue.NIL;
        }, Result.EVENT);
    }

    public static void exploded(Block block, World world, int x, int y, int z) {
        invoke(block.blockID, BlockCallback.EXPLODED, world, () -> {
            LuaBlockActionContext context = context(block, world, x, y, z, null, null, -1,
                    world.getBlockMetadata(x, y, z), true);
            return context;
        }, () -> {
            block.onBlockDestroyedByExplosion(world, x, y, z);
            return LuaValue.NIL;
        }, Result.EVENT);
    }

    public static void click(Block block, World world, int x, int y, int z, EntityPlayer player) {
        invoke(block.blockID, BlockCallback.CLICK, world, () -> {
            LuaBlockActionContext context = context(block, world, x, y, z, player, null, -1,
                    world.getBlockMetadata(x, y, z), true);
            return context;
        }, () -> {
            block.onBlockClicked(world, x, y, z, player);
            return LuaValue.NIL;
        }, Result.EVENT);
    }

    public static void placed(Block block, World world, int x, int y, int z, EntityLiving entity) {
        invoke(block.blockID, BlockCallback.PLACED, world, () -> {
            LuaBlockActionContext context = context(block, world, x, y, z,
                    entity instanceof EntityPlayer ? (EntityPlayer) entity : null, entity, -1,
                    world.getBlockMetadata(x, y, z), true);
            return context;
        }, () -> {
            block.onBlockPlacedBy(world, x, y, z, entity);
            return LuaValue.NIL;
        }, Result.EVENT);
    }

    public static void walk(Block block, World world, int x, int y, int z, Entity entity) {
        invoke(block.blockID, BlockCallback.ENTITY_WALK, world, () -> {
            LuaBlockActionContext context = context(block, world, x, y, z,
                    entity instanceof EntityPlayer ? (EntityPlayer) entity : null, entity, -1,
                    world.getBlockMetadata(x, y, z), true);
            return context;
        }, () -> {
            block.onEntityWalking(world, x, y, z, entity);
            return LuaValue.NIL;
        }, Result.EVENT);
    }

    public static void collide(Block block, World world, int x, int y, int z, Entity entity) {
        invoke(block.blockID, BlockCallback.ENTITY_COLLIDE, world, () -> {
            LuaBlockActionContext context = context(block, world, x, y, z,
                    entity instanceof EntityPlayer ? (EntityPlayer) entity : null, entity, -1,
                    world.getBlockMetadata(x, y, z), true);
            return context;
        }, () -> {
            block.onEntityCollidedWithBlock(world, x, y, z, entity);
            return LuaValue.NIL;
        }, Result.EVENT);
    }

    public static void neighbor(Block block, World world, int x, int y, int z, int neighborId) {
        invoke(block.blockID, BlockCallback.NEIGHBOR_CHANGED, world, () -> {
            LuaBlockActionContext context = context(block, world, x, y, z, null, null, -1,
                    world.getBlockMetadata(x, y, z), true);
            context.set("neighborId", neighborId);
            return context;
        }, () -> {
            block.onNeighborBlockChange(world, x, y, z, neighborId);
            return LuaValue.NIL;
        }, Result.EVENT);
        if (BlockCallbackRegistry.get(block.blockID) == null) {
            inputChanged(block, world, x, y, z, neighborId);
        }
    }

    private static void inputChanged(Block block, World world, int x, int y, int z, int neighborId) {
        LuaOverrideCallback callback = get(block.blockID, BlockCallback.INPUT_CHANGED);
        if (callback == null || world.multiplayerWorld || world.getBlockId(x, y, z) != block.blockID) {
            return;
        }
        Map<String, Integer> inputs = INPUTS.get(world);
        if (inputs == null) {
            inputs = new LinkedHashMap<String, Integer>(128, 0.75F, true) {
                protected boolean removeEldestEntry(Map.Entry<String, Integer> entry) {
                    return size() > 8192;
                }
            };
            INPUTS.put(world, inputs);
        }
        int mask = 0;
        for (BlockFace face : BlockFace.values()) {
            if (world.isBlockIndirectlyProvidingPowerTo(x + face.xOffset, y + face.yOffset, z + face.zOffset,
                    face.nativeSide)) {
                mask |= 1 << face.nativeSide;
            }
        }
        Integer previous = inputs.put(block.blockID + ":" + x + ":" + y + ":" + z, mask);
        if (previous != null && previous == mask) {
            return;
        }
        try (LuaBlockActionContext context = context(block, world, x, y, z, null, null, -1,
                world.getBlockMetadata(x, y, z), true)) {
            context.set("neighborId", neighborId);
            context.set("initial", LuaValue.valueOf(previous == null));
            context.set("previous", inputFaces(previous == null ? 0 : previous));
            context.set("current", inputFaces(mask));
            // Vanilla has a neighbor method, but no separate input-change callback to
            // invoke again.
            callback.invoke(context, () -> LuaValue.NIL, Result.EVENT);
        }
    }

    private static LuaTable inputFaces(int mask) {
        LuaTable faces = new LuaTable();
        for (BlockFace face : BlockFace.values()) {
            faces.set(face.luaName, LuaValue.valueOf((mask & (1 << face.nativeSide)) != 0));
        }
        return faces;
    }

    public static boolean activate(Block block, World world, int x, int y, int z, EntityPlayer player) {
        LuaValue result = invoke(block.blockID, BlockCallback.ACTIVATE, world,
                () -> context(block, world, x, y, z, player, null, -1, world.getBlockMetadata(x, y, z), true),
                () -> LuaValue.valueOf(block.blockActivated(world, x, y, z, player) ? "handled" : "pass"),
                Result.INTERACTION);
        InteractionOutcome outcome = InteractionOutcome.fromLua(result, "onActivate");
        if (outcome == InteractionOutcome.DENY) {
            ItemInteractionRouting.deny();
        }
        return outcome != InteractionOutcome.PASS;
    }

    public static boolean canPlace(Block block, World world, int x, int y, int z, int face) {
        return invoke(block.blockID, BlockCallback.CAN_PLACE, world,
                () -> context(block, world, x, y, z, null, null, face, 0, false),
                () -> LuaValue.valueOf(block.canPlaceBlockOnSide(world, x, y, z, face)), Result.BOOLEAN).toboolean();
    }

    public static void drops(Block block, World world, int x, int y, int z, int metadata, float chance) {
        LuaOverrideCallback callback = get(block.blockID, BlockCallback.GET_DROPS);
        if (callback == null || world.multiplayerWorld) {
            block.dropBlockAsItemWithChance(world, x, y, z, metadata, chance);
            return;
        }
        LuaValue result;
        try (LuaBlockActionContext context = context(block, world, x, y, z, null, null, -1, metadata, false)) {
            result = callback.invoke(context, () -> BlockDropOverrideCapture.original(block, world, x, y, z, metadata),
                    value -> {
                        if (!value.isnil()) {
                            new BlockDropDefinition(value).validateRegistered();
                        }
                        return value;
                    });
        }
        for (ItemStack stack : new BlockDropDefinition(result).sample(world.rand, chance)) {
            double ox = world.rand.nextFloat() * 0.7F + 0.15F;
            double oy = world.rand.nextFloat() * 0.7F + 0.15F;
            double oz = world.rand.nextFloat() * 0.7F + 0.15F;
            EntityItem dropped = new EntityItem(world, x + ox, y + oy, z + oz, stack);
            dropped.delayBeforeCanPickup = 10;
            world.entityJoinedWorld(dropped);
        }
    }
}
