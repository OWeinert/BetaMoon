package betamoon.instrumentation.hooks.item;

import betamoon.luaapi.item.ItemCallbackRegistry;
import betamoon.luaapi.item.ItemCallbackOverrides;
import betamoon.luaapi.item.ItemCallback;
import betamoon.luaapi.item.ItemDefinition;
import net.minecraft.src.Block;
import net.minecraft.src.EntityPlayer;
import net.minecraft.src.ItemStack;

/** Runtime callbacks loaded only after the game classes are available. */
public final class ItemHarvestCallbacks {
    private ItemHarvestCallbacks() {
    }

    public static int permission(Block block, EntityPlayer player, int metadata) {
        int override = ItemCallbackOverrides.harvestPermission(block, player, metadata);
        if (override != 0) {
            return override;
        }
        ItemStack stack = player.getCurrentEquippedItem();
        ItemDefinition definition = stack == null ? null : ItemCallbackRegistry.get(stack.itemID);
        if (definition == null
                || (definition.tool.classes.isEmpty() && !definition.callbacks.has(ItemCallback.CAN_HARVEST))) {
            return 0;
        }
        Boolean declared = definition.tool.harvest(block, metadata);
        if (declared == null && !definition.callbacks.has(ItemCallback.CAN_HARVEST)) {
            return 0;
        }
        boolean fallback = declared == null ? block.blockMaterial.getIsHarvestable() : declared.booleanValue();
        return ItemCallbackRegistry.canHarvest(stack.itemID, block, metadata, fallback) ? 1 : -1;
    }

    public static boolean result(boolean original, int decision) {
        return decision == 0 ? original : decision > 0;
    }
}
