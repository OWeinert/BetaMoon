package betamoon.luaapi.item;

import betamoon.luaapi.resource.OverrideManager;
import forge.ForgeHooks;
import forge.IUseItemFirst;
import betamoon.luaapi.utils.LuaOverrideCallback;
import betamoon.luaapi.utils.InteractionOutcome;
import betamoon.luaapi.utils.LuaOverrideCallback.Result;
import betamoon.luaapi.utils.LuaOverrideDefinition;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import net.minecraft.src.Block;
import net.minecraft.src.Entity;
import net.minecraft.src.EntityLiving;
import net.minecraft.src.EntityPlayer;
import net.minecraft.src.Item;
import net.minecraft.src.ItemStack;
import net.minecraft.src.MathHelper;
import net.minecraft.src.World;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

/**
 * Item-specific override dispatch, retaining live stacks and Minecraft's return
 * values.
 */
public final class ItemCallbackOverrides {
    private static final Map<String, LuaOverrideCallback> CALLBACKS = new ConcurrentHashMap<String, LuaOverrideCallback>();
    private static final ThreadLocal<Boolean> HARVEST_BASE = new ThreadLocal<Boolean>();
    private static final ThreadLocal<Boolean> USE_FIRST_DONE = new ThreadLocal<Boolean>();

    private ItemCallbackOverrides() {
    }

    public static boolean supports(String name) {
        return ItemCallback.fromLuaName(name) != null;
    }

    public static LuaOverrideCallback get(int id, ItemCallback callbackId) {
        if (callbackId == ItemCallback.CAN_HARVEST && Boolean.TRUE.equals(HARVEST_BASE.get())) {
            return null;
        }
        LuaOverrideCallback callback = CALLBACKS.get(key(id, callbackId));
        return callback != null && callback.isEnabled() ? callback : null;
    }

    public static OverrideManager.PropertyAdapter<Item, LuaOverrideDefinition> adapter(final String name) {
        final ItemCallback callbackId = ItemCallback.fromLuaName(name);
        return new OverrideManager.PropertyAdapter<Item, LuaOverrideDefinition>() {
            public LuaOverrideDefinition read(Item target) {
                LuaOverrideCallback callback = CALLBACKS.get(key(((Item) target).shiftedIndex, callbackId));
                return callback == null ? null : callback.definition;
            }

            public void write(Item target, LuaOverrideDefinition value) {
                String key = key(((Item) target).shiftedIndex, callbackId);
                if (value == null) {
                    CALLBACKS.remove(key);
                } else {
                    CALLBACKS.put(key, new LuaOverrideCallback((LuaOverrideDefinition) value));
                }
            }
        };
    }

    private static LuaValue invoke(Item item, ItemCallback callbackId, World world,
            Supplier<LuaItemActionContext> factory, Supplier<LuaValue> original, Result contract) {
        LuaOverrideCallback callback = get(item.shiftedIndex, callbackId);
        if (callback == null || world.multiplayerWorld) {
            return original.get();
        }
        try (LuaItemActionContext context = factory.get()) {
            return callback.invoke(context, original, contract);
        }
    }

    private static String key(int id, ItemCallback callback) {
        return id + ":" + callback.name();
    }

    private static LuaItemActionContext context(ItemStack stack, World world, Entity entity, Entity target) {
        EntityPlayer player = entity instanceof EntityPlayer ? (EntityPlayer) entity : null;
        LuaItemActionContext context = new LuaItemActionContext(world, MathHelper.floor_double(entity.posX),
                MathHelper.floor_double(entity.posY), MathHelper.floor_double(entity.posZ), player, stack, -1, true);
        if (target != null) {
            LuaTable targetValue = new LuaTable();
            targetValue.set("kind", "entity");
            targetValue.set("entity", context.entityAccess(target));
            context.set("target", targetValue);
            context.set("entity", context.entityAccess(target));
        }
        return context;
    }

    private static LuaValue interaction(boolean handled) {
        return (handled ? InteractionOutcome.HANDLED : InteractionOutcome.PASS).toLuaValue();
    }

    private static boolean handled(LuaValue result) {
        InteractionOutcome outcome = InteractionOutcome.fromLua(result, "interaction callback");
        if (outcome == InteractionOutcome.DENY) {
            ItemInteractionRouting.deny();
        }
        return outcome != InteractionOutcome.PASS;
    }

    public static boolean useFirst(Item item, ItemStack stack, EntityPlayer player, World world, int x, int y, int z,
            int face) {
        return handled(invoke(item, ItemCallback.USE_FIRST, world,
                () -> new LuaItemActionContext(world, x, y, z, player, stack, face, true),
                () -> interaction(item instanceof IUseItemFirst
                        && ((IUseItemFirst) item).onItemUseFirst(stack, player, world, x, y, z, face)),
                Result.INTERACTION));
    }

    public static int beginUseFirst(ItemStack stack, EntityPlayer player, World world, int x, int y, int z, int face) {
        USE_FIRST_DONE.remove();
        if (stack == null || get(stack.itemID, ItemCallback.USE_FIRST) == null || world.multiplayerWorld) {
            return 0;
        }
        boolean handled = useFirst(stack.getItem(), stack, player, world, x, y, z, face);
        USE_FIRST_DONE.set(Boolean.TRUE);
        return handled ? 1 : 0;
    }

    public static boolean useFirstInterface(IUseItemFirst item, ItemStack stack, EntityPlayer player, World world,
            int x, int y, int z, int face) {
        return !Boolean.TRUE.equals(USE_FIRST_DONE.get()) && item.onItemUseFirst(stack, player, world, x, y, z, face);
    }

    public static void finishUseFirst() {
        USE_FIRST_DONE.remove();
    }

    public static boolean useOnBlock(Item item, ItemStack stack, EntityPlayer player, World world, int x, int y, int z,
            int face) {
        return handled(invoke(item, ItemCallback.USE_ON_BLOCK, world,
                () -> new LuaItemActionContext(world, x, y, z, player, stack, face, true),
                () -> interaction(item.onItemUse(stack, player, world, x, y, z, face)), Result.INTERACTION));
    }

    public static ItemStack use(Item item, ItemStack stack, World world, EntityPlayer player) {
        ItemStack[] result = {stack};
        handled(invoke(item, ItemCallback.USE, world, () -> context(stack, world, player, null), () -> {
            result[0] = item.onItemRightClick(stack, world, player);
            return LuaValue.valueOf("handled");
        }, Result.INTERACTION));
        return result[0];
    }

    public static boolean hit(Item item, ItemStack stack, EntityLiving target, EntityLiving attacker) {
        return invoke(item, ItemCallback.HIT_ENTITY, attacker.worldObj,
                () -> context(stack, attacker.worldObj, attacker, target),
                () -> LuaValue.valueOf(item.hitEntity(stack, target, attacker)), Result.EVENT).toboolean();
    }

    public static boolean destroyed(Item item, ItemStack stack, int blockId, int x, int y, int z, EntityLiving entity) {
        return invoke(item, ItemCallback.BLOCK_DESTROYED, entity.worldObj, () -> {
            LuaItemActionContext context = new LuaItemActionContext(entity.worldObj, x, y, z,
                    entity instanceof EntityPlayer ? (EntityPlayer) entity : null, stack, -1, true);
            context.set("blockId", blockId);
            return context;
        }, () -> LuaValue.valueOf(item.onBlockDestroyed(stack, blockId, x, y, z, entity)), Result.EVENT).toboolean();
    }

    public static void crafted(Item item, ItemStack stack, World world, EntityPlayer player) {
        invoke(item, ItemCallback.CRAFTED, world, () -> context(stack, world, player, null), () -> {
            item.onCreated(stack, world, player);
            return LuaValue.NIL;
        }, Result.EVENT);
    }

    public static void inventoryTick(Item item, ItemStack stack, World world, Entity entity, int slot,
            boolean selected) {
        LuaOverrideCallback callback = get(item.shiftedIndex, ItemCallback.INVENTORY_TICK);
        if (callback != null && (world.getWorldTime() % callback.definition.interval != 0
                || callback.definition.selectedOnly && !selected)) {
            item.onUpdate(stack, world, entity, slot, selected);
            return;
        }
        invoke(item, ItemCallback.INVENTORY_TICK, world, () -> {
            LuaItemActionContext context = context(stack, world, entity, null);
            context.set("slot", slot);
            context.set("selected", LuaValue.valueOf(selected));
            context.set("entity", context.entityAccess(entity));
            return context;
        }, () -> {
            item.onUpdate(stack, world, entity, slot, selected);
            return LuaValue.NIL;
        }, Result.EVENT);
    }

    public static int useOnEntity(EntityPlayer player, Entity target) {
        ItemStack stack = player.getCurrentEquippedItem();
        if (stack == null || get(stack.itemID, ItemCallback.USE_ON_ENTITY) == null
                || player.worldObj.multiplayerWorld) {
            return 0;
        }
        invoke(stack.getItem(), ItemCallback.USE_ON_ENTITY, player.worldObj,
                () -> context(stack, player.worldObj, player, target), () -> {
                    InteractionOutcome result = ItemCallbackRegistry.interact(ItemCallback.USE_ON_ENTITY, stack, player,
                            player.worldObj, MathHelper.floor_double(target.posX), MathHelper.floor_double(target.posY),
                            MathHelper.floor_double(target.posZ), -1, target);
                    if (result == InteractionOutcome.PASS) {
                        player.useCurrentItemOnEntity(target);
                    }
                    return interaction(result != InteractionOutcome.DENY);
                }, Result.INTERACTION);
        // The override has already decided whether to execute the complete original
        // interaction.
        return 1;
    }

    private static LuaTable miningContext(int itemId, Block block, int metadata) {
        LuaTable context = new LuaTable();
        LuaTable target = new LuaTable();
        target.set("id", block.blockID);
        target.set("damage", metadata);
        context.set("block", target);
        context.set("itemId", itemId);
        return context;
    }

    public static boolean canHarvest(Item item, Block block) {
        LuaOverrideCallback callback = get(item.shiftedIndex, ItemCallback.CAN_HARVEST);
        Supplier<LuaValue> original = () -> LuaValue.valueOf(item.canHarvestBlock(block));
        return callback == null
                ? original.get().toboolean()
                : callback.invoke(miningContext(item.shiftedIndex, block, 0), original, Result.BOOLEAN).toboolean();
    }

    public static int harvestPermission(Block block, EntityPlayer player, int metadata) {
        ItemStack stack = player.getCurrentEquippedItem();
        LuaOverrideCallback callback = stack == null ? null : get(stack.itemID, ItemCallback.CAN_HARVEST);
        if (callback == null) {
            return 0;
        }
        return callback.invoke(miningContext(stack.itemID, block, metadata), () -> {
            HARVEST_BASE.set(Boolean.TRUE);
            try {
                return LuaValue.valueOf(ForgeHooks.canHarvestBlock(block, player, metadata));
            } finally {
                HARVEST_BASE.remove();
            }
        }, Result.BOOLEAN).toboolean() ? 1 : -1;
    }

    public static float miningSpeed(Item item, ItemStack stack, Block block, int metadata) {
        return miningSpeed(item, stack, block, metadata, () -> item.getStrVsBlock(stack, block, metadata));
    }

    public static float miningSpeed(Item item, ItemStack stack, Block block) {
        return miningSpeed(item, stack, block, 0, () -> item.getStrVsBlock(stack, block));
    }

    private static float miningSpeed(Item item, ItemStack stack, Block block, int metadata, Supplier<Float> original) {
        LuaOverrideCallback callback = get(item.shiftedIndex, ItemCallback.MINING_SPEED);
        return callback == null
                ? original.get()
                : callback.invoke(miningContext(stack.itemID, block, metadata), () -> LuaValue.valueOf(original.get()),
                        Result.NUMBER).tofloat();
    }
}
