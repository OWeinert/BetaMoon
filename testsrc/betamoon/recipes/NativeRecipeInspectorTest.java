package betamoon.recipes;

import betamoon.query.RecipeQueryUtils;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.src.IRecipe;
import net.minecraft.src.InventoryCrafting;
import net.minecraft.src.Item;
import net.minecraft.src.ItemStack;
import net.minecraft.src.ShapedRecipes;
import net.minecraft.src.ShapelessRecipes;

/** Direct checks for the shared native recipe inspection contract. */
public final class NativeRecipeInspectorTest {
    private NativeRecipeInspectorTest() {
    }

    public static void run() {
        ItemStack[] grid = {new ItemStack(1, 1, 0), null, new ItemStack(3, 1, -1), new ItemStack(4, 1, 0)};
        ShapedRecipes shaped = new ShapedRecipes(2, 2, grid, new ItemStack(5, 2, 0));
        require(NativeRecipeInspector.kind(shaped) == NativeRecipeKind.SHAPED, "Shaped recipe kind changed");
        require(Arrays.equals(NativeRecipeInspector.shapedDimensions(shaped, grid.length), new int[]{2, 2}),
                "Shaped recipe dimensions were not discovered");
        require(NativeRecipeInspector.shapedInputs(shaped) == grid, "Shaped input identity changed");
        require(NativeRecipeInspector.collectInputs(shaped).size() == 3, "Shaped null cells were not excluded");

        List<Object> rawInputs = new ArrayList<>();
        rawInputs.add(new ItemStack(1, 1, 0));
        rawInputs.add(Item.stick);
        ShapelessRecipes shapeless = new ShapelessRecipes(new ItemStack(4, 1, 0), rawInputs);
        require(NativeRecipeInspector.kind(shapeless) == NativeRecipeKind.SHAPELESS, "Shapeless recipe kind changed");
        List<ItemStack> normalized = NativeRecipeInspector.collectInputs(shapeless);
        require(normalized.size() == 2 && normalized.get(1).itemID == Item.stick.shiftedIndex,
                "Shapeless ingredient normalization changed");

        IRecipe unknown = new UnknownRecipe(new ItemStack(2, 1, 0));
        require(NativeRecipeInspector.kind(unknown) == NativeRecipeKind.UNKNOWN, "Unknown recipe kind changed");
        require(NativeRecipeInspector.collectInputs(unknown) == null, "Unknown recipe gained inferred inputs");
        require(NativeRecipeInspector.identity(unknown) == unknown, "Crafting recipe identity changed");

        Map<Integer, ItemStack> furnace = new LinkedHashMap<>();
        furnace.put(Integer.valueOf(1), new ItemStack(2, 1, 0));
        Map.Entry<Integer, ItemStack> entry = furnace.entrySet().iterator().next();
        SmeltingRecipe smelting = new SmeltingRecipe(furnace, entry);
        SmeltingRecipe same = new SmeltingRecipe(furnace, entry);
        require(NativeRecipeInspector.kind(smelting) == NativeRecipeKind.SMELTING, "Smelting kind changed");
        require(NativeRecipeInspector.identity(smelting).equals(Integer.valueOf(1)), "Smelting identity changed");
        require(NativeRecipeInspector.representsSameRegistration(smelting, same),
                "Equivalent smelting wrappers no longer share registration identity");

        ItemStack wildcard = new ItemStack(1, 1, -1);
        require(RecipeModificationHandler.matchesInput(shaped, wildcard),
                "Modification queries lost wildcard-damage matching");
        require(!RecipeQueryUtils.matchesInput(shaped, wildcard),
                "Legacy exact query matching unexpectedly adopted wildcard semantics");
    }

    private static final class UnknownRecipe implements IRecipe {
        private final ItemStack output;

        private UnknownRecipe(ItemStack output) {
            this.output = output;
        }

        public boolean matches(InventoryCrafting inventory) {
            return false;
        }

        public ItemStack getCraftingResult(InventoryCrafting inventory) {
            return output.copy();
        }

        public int getRecipeSize() {
            return 0;
        }

        public ItemStack getRecipeOutput() {
            return output;
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
