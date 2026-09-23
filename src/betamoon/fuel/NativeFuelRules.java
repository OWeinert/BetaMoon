package betamoon.fuel;

import net.minecraft.src.Block;
import net.minecraft.src.Item;
import net.minecraft.src.ItemStack;
import net.minecraft.src.Material;
import net.minecraft.src.ModLoader;

/** The effective Beta 1.7.3 furnace rules, including ModLoader's fallback. */
public final class NativeFuelRules {
    private NativeFuelRules() {
    }

    public static int getBurnTime(ItemStack stack) {
        if (stack == null || stack.getItem() == null) {
            return 0;
        }
        int itemId = stack.getItem().shiftedIndex;
        if (itemId < Block.blocksList.length && Block.blocksList[itemId] != null
                && Block.blocksList[itemId].blockMaterial == Material.wood) {
            return 300;
        }
        if (itemId == Item.stick.shiftedIndex) {
            return 100;
        }
        if (itemId == Item.coal.shiftedIndex) {
            return 1600;
        }
        if (itemId == Item.bucketLava.shiftedIndex) {
            return 20000;
        }
        if (itemId == Block.sapling.blockID) {
            return 100;
        }
        return ModLoader.AddAllFuel(itemId);
    }
}
