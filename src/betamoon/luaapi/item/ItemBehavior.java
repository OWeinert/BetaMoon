package betamoon.luaapi.item;

import betamoon.luaapi.utils.InteractionOutcome;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import net.minecraft.src.Entity;
import net.minecraft.src.EntityLiving;
import net.minecraft.src.EntityPlayer;
import net.minecraft.src.ItemStack;
import net.minecraft.src.MathHelper;
import net.minecraft.src.World;

/**
 * Shared item-wrapper policies. Superclass operations remain lazy and are
 * supplied by the wrapper that owns the appropriate Minecraft superclass.
 */
public final class ItemBehavior {
    private ItemBehavior() {
    }

    public static int icon(int itemId, int metadata, IntSupplier base) {
        ItemDefinition definition = ItemCallbackRegistry.get(itemId);
        return definition == null ? base.getAsInt() : definition.visual.icon(metadata, base.getAsInt());
    }

    public static int color(int itemId, int metadata, IntSupplier base) {
        ItemDefinition definition = ItemCallbackRegistry.get(itemId);
        return definition == null ? base.getAsInt() : definition.visual.color(metadata, base.getAsInt());
    }

    public static ItemStack use(ItemStack stack, World world, EntityPlayer player, Supplier<ItemStack> base) {
        if (ItemUseHandler.suppressDefaultUse(stack, world, player)) {
            return stack;
        }
        int oldCount = stack.stackSize;
        InteractionOutcome result = ItemCallbackRegistry.interact(ItemCallback.USE, stack, player, world,
                MathHelper.floor_double(player.posX), MathHelper.floor_double(player.posY),
                MathHelper.floor_double(player.posZ), -1, null);
        if (result == InteractionOutcome.DENY) {
            return stack;
        }
        ItemStack after = result == InteractionOutcome.HANDLED ? stack : base.get();
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
