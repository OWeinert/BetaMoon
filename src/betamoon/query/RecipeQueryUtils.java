package betamoon.query;

import betamoon.recipes.NativeRecipeInspector;
import betamoon.recipes.SmeltingRecipe;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.src.IRecipe;
import net.minecraft.src.ItemStack;
import net.minecraft.src.ShapedRecipes;
import net.minecraft.src.ShapelessRecipes;

public final class RecipeQueryUtils {
    private RecipeQueryUtils() {
    }

    public static boolean matchesOutput(ItemStack recipeOutput, ItemStack target) {
        if (recipeOutput == null || target == null) {
            return false;
        }
        if (recipeOutput.itemID != target.itemID) {
            return false;
        }
        if (target.stackSize == 0) {
            return true;
        }
        return recipeOutput.stackSize == target.stackSize && recipeOutput.getItemDamage() == target.getItemDamage();
    }

    public static boolean matchesStack(ItemStack stack, ItemStack target) {
        if (stack == null || target == null) {
            return false;
        }
        return stack.itemID == target.itemID && stack.getItemDamage() == target.getItemDamage();
    }

    public static ItemStack normalizeIngredient(Object ingredient) {
        return NativeRecipeInspector.normalizeIngredient(ingredient);
    }

    public static List<?> getShapelessInputs(ShapelessRecipes recipe) {
        return NativeRecipeInspector.shapelessInputs(recipe);
    }

    public static ItemStack[] getShapedInputs(ShapedRecipes recipe) {
        return NativeRecipeInspector.shapedInputs(recipe);
    }

    public static int[] getShapedDimensions(ShapedRecipes recipe, int itemCount) {
        return NativeRecipeInspector.shapedDimensions(recipe, itemCount);
    }

    public static ItemStack getRecipeOutput(Object recipe) {
        if (recipe instanceof IRecipe) {
            return ((IRecipe) recipe).getRecipeOutput();
        }
        return null;
    }

    public static boolean matchesSmeltingOutput(ItemStack actual, ItemStack expected) {
        if (matchesOutput(actual, expected)) {
            return true;
        }
        if (actual == null || expected == null) {
            return false;
        }
        return actual.itemID == expected.itemID && actual.getItemDamage() == expected.getItemDamage();
    }

    public static boolean matchesInput(Object recipe, ItemStack target) {
        if (recipe instanceof SmeltingRecipe) {
            return ((SmeltingRecipe) recipe).getInputId() == target.itemID;
        }
        if (!(recipe instanceof IRecipe)) {
            return false;
        }
        IRecipe craft = (IRecipe) recipe;
        if (craft instanceof ShapedRecipes) {
            ItemStack[] items = getShapedInputs((ShapedRecipes) craft);
            if (items == null) {
                return false;
            }
            for (int i = 0; i < items.length; i++) {
                ItemStack stack = items[i];
                if (stack != null && matchesStack(stack, target)) {
                    return true;
                }
            }
            return false;
        }
        if (craft instanceof ShapelessRecipes) {
            List<?> items = getShapelessInputs((ShapelessRecipes) craft);
            if (items == null) {
                return false;
            }
            for (int i = 0; i < items.size(); i++) {
                ItemStack stack = normalizeIngredient(items.get(i));
                if (stack != null && matchesStack(stack, target)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean matchesInputs(IRecipe recipe, List<ItemStack> inputs) {
        List<ItemStack> recipeInputs = collectRecipeInputs(recipe);
        if (recipeInputs == null) {
            return false;
        }
        return matchesInputList(recipeInputs, inputs);
    }

    public static List<ItemStack> collectRecipeInputs(IRecipe recipe) {
        return NativeRecipeInspector.collectInputs(recipe);
    }

    public static boolean matchesInputList(List<ItemStack> recipeInputs, List<ItemStack> desiredInputs) {
        List<ItemStack> remaining = new ArrayList<>(recipeInputs);
        for (int i = 0; i < desiredInputs.size(); i++) {
            ItemStack target = desiredInputs.get(i);
            boolean matched = false;
            for (int j = 0; j < remaining.size(); j++) {
                ItemStack candidate = remaining.get(j);
                if (candidate != null && matchesStack(candidate, target)) {
                    remaining.remove(j);
                    matched = true;
                    break;
                }
            }
            if (!matched) {
                return false;
            }
        }
        return true;
    }

    public static int inferGridWidth(int length) {
        if (length <= 0) {
            return -1;
        }
        int size = (int) Math.round(Math.sqrt(length));
        if (size * size == length && size >= 1 && size <= 3) {
            return size;
        }
        return -1;
    }

    public static int inferGridHeight(int length, int width) {
        if (width <= 0 || length % width != 0) {
            return -1;
        }
        int height = length / width;
        if (height >= 1 && height <= 3) {
            return height;
        }
        return -1;
    }
}
