package betamoon.instrumentation.hooks.item;

import betamoon.luaapi.item.ItemCallbackRegistry;
import betamoon.luaapi.item.ItemCallback;
import betamoon.luaapi.item.ItemCallbackOverrides;
import betamoon.luaapi.item.ItemInteractionRouting;
import net.minecraft.src.Entity;
import net.minecraft.src.EntityPlayer;
import net.minecraft.src.ItemStack;
import net.minecraft.src.MathHelper;
import net.minecraft.src.World;

/** Runtime callbacks loaded only after the game classes are available. */
public final class ItemInteractionCallbacks {
    private ItemInteractionCallbacks() {
    }

    public static int begin(EntityPlayer player, World world, ItemStack stack, int x, int y, int z, int face) {
        ItemInteractionRouting.begin(player, world);
        return ItemCallbackOverrides.beginUseFirst(stack, player, world, x, y, z, face);
    }

    public static boolean complete(boolean result, int override) {
        ItemCallbackOverrides.finishUseFirst();
        return ItemInteractionRouting.complete(override != 0 || result);
    }

    public static int fallback(EntityPlayer player, World world) {
        return ItemInteractionRouting.denyFallback(player, world) ? -1 : 0;
    }

    public static boolean fallbackComplete(boolean result, int decision) {
        return decision == 0 && result;
    }

    public static int entity(EntityPlayer player, Entity target) {
        int override = ItemCallbackOverrides.useOnEntity(player, target);
        if (override != 0) {
            return override;
        }
        ItemStack stack = player.getCurrentEquippedItem();
        if (stack == null) {
            return 0;
        }
        return ItemCallbackRegistry.interact(ItemCallback.USE_ON_ENTITY, stack, player, player.worldObj,
                MathHelper.floor_double(target.posX), MathHelper.floor_double(target.posY),
                MathHelper.floor_double(target.posZ), -1, target).toNativeCode();
    }

    public static void entityComplete() {
    }
}
