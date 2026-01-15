package betamoon.recipes;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.src.CraftingManager;
import net.minecraft.src.FurnaceRecipes;
import net.minecraft.src.IRecipe;
import net.minecraft.src.Item;
import net.minecraft.src.ItemStack;
import net.minecraft.src.ShapedRecipes;
import net.minecraft.src.ShapelessRecipes;

public final class RecipeModificationHandler {
    private static Map recipeMap;

    /**
     * Builds the cached map for crafting and smelting recipes.
     */
    public static void createRecipeMap() {
        recipeMap = mapRecipes();
    }

    /**
     * Returns the cached recipe map keyed by output name and amount.
     *
     * @return map of output keys to recipes
     */
    public static Map getRecipeMap() {
        return recipeMap;
    }

    /**
     * Returns a recipe entry by its output key.
     *
     * @param key output key string
     * @return recipe instance or null when not found
     */
    public static Object getRecipeByKey(String key) {
        if (key == null) {
            return null;
        }
        return recipeMap.get(key);
    }

    /**
     * Removes a recipe entry by its output key.
     *
     * @param key output key string
     * @return true when the recipe was removed
     */
    public static boolean removeRecipeByKey(String key) {
        if (key == null) {
            return false;
        }
        Object recipe = recipeMap.remove(key);
        if (recipe == null) {
            return false;
        }
        // Keep the underlying registries in sync with the cached map.
        if (recipe instanceof SmeltingRecipe) {
            return ((SmeltingRecipe) recipe).removeFromFurnace();
        }
        if (recipe instanceof IRecipe) {
            List recipes = CraftingManager.getInstance().getRecipeList();
            return recipes.remove(recipe);
        }
        return false;
    }

    /**
     * Updates the output stack for a recipe entry by its output key.
     *
     * @param key output key string
     * @param output new output item stack
     * @return true when the recipe output was updated
     */
    public static boolean setRecipeOutputByKey(String key, ItemStack output) {
        Object recipe = getRecipeByKey(key);
        if (recipe == null) {
            return false;
        }
        // Delegate to the underlying recipe-specific update logic.
        return setRecipeOutput(recipe, output);
    }

    /**
     * Filters recipes by output stack, matching item id and amount.
     * If the target stack size is 0, all amounts for the item id are returned.
     *
     * @param output output item stack to match
     * @return map of output keys to matching recipes
     */
    public static Map filterRecipesByOutput(ItemStack output) {
        Map matches = new LinkedHashMap();
        if (output == null) {
            return matches;
        }
        for (Iterator it = recipeMap.entrySet().iterator(); it.hasNext();) {
            Map.Entry entry = (Map.Entry) it.next();
            IRecipe recipe = (IRecipe) entry.getValue();
            ItemStack recipeOutput = recipe.getRecipeOutput();
            if (matchesOutput(recipeOutput, output)) {
                matches.put(entry.getKey(), recipe);
            }
        }
        return matches;
    }

    /**
     * Filters recipes by input stack, matching item id and damage.
     *
     * @param input input item stack to match
     * @return map of output keys to matching recipes
     */
    public static Map filterRecipesByInput(ItemStack input) {
        Map matches = new LinkedHashMap();
        if (input == null) {
            return matches;
        }
        for (Iterator it = recipeMap.entrySet().iterator(); it.hasNext();) {
            Map.Entry entry = (Map.Entry) it.next();
            Object recipe = entry.getValue();
            if (matchesInput(recipe, input)) {
                matches.put(entry.getKey(), recipe);
            }
        }
        return matches;
    }

    /**
     * Filters crafting recipes to shaped recipes only.
     *
     * @return map of output keys to shaped recipes
     */
    public static Map filterShapedRecipes() {
        return filterRecipesByType(ShapedRecipes.class);
    }

    /**
     * Filters crafting recipes to shapeless recipes only.
     *
     * @return map of output keys to shapeless recipes
     */
    public static Map filterShapelessRecipes() {
        return filterRecipesByType(ShapelessRecipes.class);
    }

    /**
     * Filters recipes to smelting recipes only.
     *
     * @return map of output keys to smelting recipes
     */
    public static Map filterSmeltingRecipes() {
        return filterRecipesByType(SmeltingRecipe.class);
    }

    /**
     * Builds a map of all registered recipes keyed by type and output signature.
     *
     * Keys are formatted as "{type}/{itemName_amount}" with an optional "_n" suffix when
     * multiple recipes share the same output id and amount.
     */
    private static Map mapRecipes() {
        List recipes = CraftingManager.getInstance().getRecipeList();
        Map mapped = new LinkedHashMap();
        Map counts = new HashMap();

        // Copy crafting recipes first so smelting entries append after them.
        for (Iterator it = recipes.iterator(); it.hasNext();) {
            IRecipe recipe = (IRecipe) it.next();
            ItemStack output = recipe.getRecipeOutput();
            if (output == null) {
                continue;
            }
            String type = recipe instanceof ShapedRecipes ? "shaped"
                : recipe instanceof ShapelessRecipes ? "shapeless"
                : "unknown";
            String key = buildOutputKey(output, type, counts);
            mapped.put(key, recipe);
        }

        // Wrap smelting recipes so they can be treated like IRecipe for filtering.
        Map smelting = FurnaceRecipes.smelting().getSmeltingList();
        for (Iterator it = smelting.entrySet().iterator(); it.hasNext();) {
            Map.Entry entry = (Map.Entry) it.next();
            ItemStack output = (ItemStack) entry.getValue();
            if (output == null) {
                continue;
            }
            String key = buildOutputKey(output, "smelting", counts);
            mapped.put(key, new SmeltingRecipe(smelting, entry));
        }
        return mapped;
    }

    /**
     * Compares recipe outputs against a target stack, honoring the "any amount"
     * rule when the target stack size is 0.
     */
    private static boolean matchesOutput(ItemStack recipeOutput, ItemStack target) {
        if (recipeOutput == null || target == null) {
            return false;
        }
        if (recipeOutput.itemID != target.itemID) {
            return false;
        }
        if (target.stackSize == 0) {
            return true;
        }
        return recipeOutput.stackSize == target.stackSize
            && recipeOutput.getItemDamage() == target.getItemDamage();
    }

    /**
     * Filters the cached recipes by concrete recipe class.
     */
    private static Map filterRecipesByType(Class recipeClass) {
        Map matches = new LinkedHashMap();
        // Filter the cached map by concrete recipe type.
        for (Iterator it = recipeMap.entrySet().iterator(); it.hasNext();) {
            Map.Entry entry = (Map.Entry) it.next();
            IRecipe recipe = (IRecipe) entry.getValue();
            if (recipeClass.isInstance(recipe)) {
                matches.put(entry.getKey(), recipe);
            }
        }
        return matches;
    }

    /**
     * Builds a unique key from the output item name and amount, adding suffixes
     * when the same output appears multiple times.
     */
    private static String buildOutputKey(ItemStack output, String recipeType, Map counts) {
        Item item = Item.itemsList[output.itemID];
        String itemName = null;
        if (item != null) {
            itemName = item.getItemName();
        }
        // Some modded items return null for base item names; fall back to stack-aware names.
        if (itemName == null || "null".equals(itemName)) {
            itemName = output.getItemName();
        }
        // If the stack still yields no name, fall back to the numeric id for a stable key.
        if (itemName == null || "null".equals(itemName)) {
            itemName = String.valueOf(output.itemID);
        }
        int damage = output.getItemDamage();
        String baseKey = itemName + (damage > 0 ? ":" + damage : "") + "_" + output.stackSize;
        String typeKey = recipeType + "/" + baseKey;
        Integer count = (Integer) counts.get(typeKey);
        if (count == null) {
            counts.put(typeKey, new Integer(0));
            return typeKey;
        }
        // Append a suffix when multiple recipes share the same output signature.
        int next = count.intValue() + 1;
        counts.put(typeKey, new Integer(next));
        return recipeType + "/" + baseKey + "_" + next;
    }

    /**
     * Checks whether a recipe uses the target stack as an input ingredient.
     */
    private static boolean matchesInput(Object recipe, ItemStack target) {
        if (recipe instanceof SmeltingRecipe) {
            // Smelting recipes use a single input item id.
            return ((SmeltingRecipe) recipe).getInputId() == target.itemID;
        }
        if (!(recipe instanceof IRecipe)) {
            return false;
        }
        if (recipe instanceof ShapedRecipes) {
            ItemStack[] items = getShapedInputs((ShapedRecipes) recipe);
            if (items == null) {
                return false;
            }
            // Match against any slot in the shaped grid.
            for (int i = 0; i < items.length; i++) {
                if (matchesInputStack(items[i], target)) {
                    return true;
                }
            }
            return false;
        }
        if (recipe instanceof ShapelessRecipes) {
            List items = getShapelessInputs((ShapelessRecipes) recipe);
            if (items == null) {
                return false;
            }
            // Match against any ingredient in the shapeless list.
            for (int i = 0; i < items.size(); i++) {
                ItemStack stack = (ItemStack) items.get(i);
                if (matchesInputStack(stack, target)) {
                    return true;
                }
            }
            return false;
        }
        return false;
    }

    /**
     * Compares an input ingredient stack against the target input, honoring wildcard damage.
     */
    private static boolean matchesInputStack(ItemStack input, ItemStack target) {
        if (input == null || target == null) {
            return false;
        }
        if (input.itemID != target.itemID) {
            return false;
        }
        // Damage of -1 acts as a wildcard for either side.
        int targetDamage = target.getItemDamage();
        if (targetDamage == -1) {
            return true;
        }
        int inputDamage = input.getItemDamage();
        if (inputDamage == -1) {
            return true;
        }
        return inputDamage == targetDamage;
    }

    /**
     * Reflects the shaped recipe input grid from its internal field.
     */
    private static ItemStack[] getShapedInputs(ShapedRecipes recipe) {
        try {
            Field field = ShapedRecipes.class.getDeclaredField("recipeItems");
            field.setAccessible(true);
            return (ItemStack[]) field.get(recipe);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Reflects the shapeless recipe input list from its internal field.
     */
    private static List getShapelessInputs(ShapelessRecipes recipe) {
        try {
            Field field = ShapelessRecipes.class.getDeclaredField("recipeItems");
            field.setAccessible(true);
            return (List) field.get(recipe);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Attempts to update the recipe output stack for crafting or smelting recipes.
     */
    private static boolean setRecipeOutput(Object recipe, ItemStack output) {
        if (recipe instanceof SmeltingRecipe) {
            // Smelting recipes store outputs directly in the furnace map wrapper.
            ((SmeltingRecipe) recipe).setOutput(output);
            return true;
        }
        if (!(recipe instanceof IRecipe)) {
            return false;
        }
        try {
            // Recipe outputs are usually stored in a private field named recipeOutput.
            Field field = recipe.getClass().getDeclaredField("recipeOutput");
            field.setAccessible(true);
            int modifiers = field.getModifiers();
            if (Modifier.isFinal(modifiers)) {
                // Strip final so we can replace the output stack in-place.
                Field modifiersField = Field.class.getDeclaredField("modifiers");
                modifiersField.setAccessible(true);
                modifiersField.setInt(field, modifiers & ~Modifier.FINAL);
            }
            // Replace the output stack reference with the new value.
            field.set(recipe, output);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
