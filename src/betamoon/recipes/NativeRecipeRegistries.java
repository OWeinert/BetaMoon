package betamoon.recipes;

import java.util.List;
import java.util.Map;
import net.minecraft.src.CraftingManager;
import net.minecraft.src.FurnaceRecipes;
import net.minecraft.src.IRecipe;
import net.minecraft.src.ItemStack;

/**
 * Typed access to the live registries exposed as raw collections by Minecraft.
 */
public final class NativeRecipeRegistries {
    private NativeRecipeRegistries() {
    }

    /**
     * Returns the engine's mutable crafting list, retaining registration order and
     * identity.
     */
    @SuppressWarnings("unchecked")
    public static List<IRecipe> crafting() {
        return CraftingManager.getInstance().getRecipeList();
    }

    /** Returns the engine's mutable input-ID to output-stack smelting map. */
    @SuppressWarnings("unchecked")
    public static Map<Integer, ItemStack> smelting() {
        return FurnaceRecipes.smelting().getSmeltingList();
    }
}
