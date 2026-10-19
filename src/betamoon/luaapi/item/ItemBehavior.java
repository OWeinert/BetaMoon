package betamoon.luaapi.item;

import betamoon.luaapi.utils.InteractionOutcome;
import betamoon.multiblock.MultiblockItemUse;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import net.minecraft.src.Entity;
import net.minecraft.src.EntityLiving;
import net.minecraft.src.EntityPlayer;
import net.minecraft.src.Block;
import net.minecraft.src.ItemStack;
import net.minecraft.src.MathHelper;
import net.minecraft.src.World;

/**
 * Shared native item policies. Superclass operations remain lazy and are
 * supplied by the implementation that owns the appropriate Minecraft
 * superclass.
 */
public final class ItemBehavior {
    private ItemBehavior() {
    }

    public static boolean useFirst(ItemStack stack, EntityPlayer player, World world, int x, int y, int z, int side) {
        return MultiblockItemUse.block(world, player, stack, x, y, z, side, false,
                () -> ItemCallbackRegistry.interact(ItemCallback.USE_FIRST, stack, player, world, x, y, z, side,
                        null) != InteractionOutcome.PASS);
    }

    public static boolean useOnBlock(ItemStack stack, EntityPlayer player, World world, int x, int y, int z, int side,
            BooleanSupplier base) {
        return MultiblockItemUse.block(world, player, stack, x, y, z, side, true, () -> {
            InteractionOutcome result = ItemCallbackRegistry.interact(ItemCallback.USE_ON_BLOCK, stack, player, world,
                    x, y, z, side, null);
            return MultiblockItemUse.handled() || result != InteractionOutcome.PASS || base.getAsBoolean();
        });
    }

    public static boolean canHarvest(int itemId, Block block, boolean base) {
        return ItemCallbackRegistry.canHarvest(itemId, block, 0, base);
    }

    public static float miningSpeed(ItemStack stack, Block block, int metadata, float base) {
        return ItemCallbackRegistry.miningSpeed(stack, block, metadata, base);
    }

    public static int icon(int itemId, int metadata, IntSupplier base) {
        ItemDefinition definition = ItemCallbackRegistry.get(itemId);
        return definition == null ? base.getAsInt() : definition.visual.icon(metadata, base.getAsInt());
    }

    public static int color(int itemId, int metadata, IntSupplier base) {
        ItemDefinition definition = ItemCallbackRegistry.get(itemId);
        return definition == null ? base.getAsInt() : definition.visual.color(metadata, base.getAsInt());
    }

    public static String name(int itemId, int metadata, String base) {
        ItemDefinition definition = ItemCallbackRegistry.get(itemId);
        return definition == null ? base : definition.names.key(base, metadata);
    }

    public static ItemStack use(ItemStack stack, World world, EntityPlayer player, Supplier<ItemStack> base) {
        return MultiblockItemUse.air(world, stack, () -> useInContext(stack, world, player, base));
    }

    private static ItemStack useInContext(ItemStack stack, World world, EntityPlayer player, Supplier<ItemStack> base) {
        if (ItemUseHandler.suppressDefaultUse(stack, world, player)) {
            return stack;
        }
        int oldCount = stack.stackSize;
        InteractionOutcome result = ItemCallbackRegistry.interact(ItemCallback.USE, stack, player, world,
                MathHelper.floor_double(player.posX), MathHelper.floor_double(player.posY),
                MathHelper.floor_double(player.posZ), -1, null);
        if (result == InteractionOutcome.DENY || MultiblockItemUse.handled()) {
            return stack;
        }
        ItemStack after = MultiblockItemUse.handled() || result == InteractionOutcome.HANDLED ? stack : base.get();
        return ItemUseHandler.finishUse(stack, after, oldCount, world, player);
    }

    public static void inventoryTick(ItemStack stack, World world, Entity entity, int slot, boolean selected) {
        ItemCallbackRegistry.event(ItemCallback.INVENTORY_TICK, stack, world, entity,
                MathHelper.floor_double(entity.posX), MathHelper.floor_double(entity.posY),
                MathHelper.floor_double(entity.posZ), selected, slot);
    }

    public static void crafted(ItemStack stack, World world, EntityPlayer player) {
        ItemCallbackRegistry.event(ItemCallback.CRAFTED, stack, world, player, MathHelper.floor_double(player.posX),
                MathHelper.floor_double(player.posY), MathHelper.floor_double(player.posZ), false, -1);
    }

    public static boolean hit(int itemId, ItemStack stack, EntityLiving target, EntityLiving attacker,
            BooleanSupplier base) {
        ItemDefinition def = ItemCallbackRegistry.get(itemId);
        boolean result;
        if (def != null && def.tool.hitCost >= 0) {
            stack.damageItem(def.tool.hitCost, attacker);
            result = true;
        } else {
            result = base.getAsBoolean();
        }
        ItemCallbackRegistry.hit(stack, target, attacker);
        return result;
    }

    public static boolean destroyedBlock(int itemId, ItemStack stack, int x, int y, int z, EntityLiving entity,
            BooleanSupplier base) {
        ItemDefinition def = ItemCallbackRegistry.get(itemId);
        boolean result;
        if (def != null && def.tool.mineCost >= 0) {
            stack.damageItem(def.tool.mineCost, entity);
            result = true;
        } else {
            result = base.getAsBoolean();
        }
        ItemCallbackRegistry.event(ItemCallback.BLOCK_DESTROYED, stack, entity.worldObj, entity, x, y, z, false, -1);
        return result;
    }
}
