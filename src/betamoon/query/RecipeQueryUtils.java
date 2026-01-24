package betamoon.query;

import betamoon.recipes.SmeltingRecipe;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.src.IRecipe;
import net.minecraft.src.Block;
import net.minecraft.src.Item;
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
        return recipeOutput.stackSize == target.stackSize
            && recipeOutput.getItemDamage() == target.getItemDamage();
    }

    public static boolean matchesStack(ItemStack stack, ItemStack target) {
        if (stack == null || target == null) {
            return false;
        }
        return stack.itemID == target.itemID && stack.getItemDamage() == target.getItemDamage();
    }

    public static ItemStack normalizeIngredient(Object ingredient) {
        if (ingredient == null) {
            return null;
        }
        if (ingredient instanceof ItemStack) {
            return (ItemStack) ingredient;
        }
        if (ingredient instanceof Item) {
            return new ItemStack((Item) ingredient);
        }
        if (ingredient instanceof Block) {
            return new ItemStack((Block) ingredient);
        }
        return null;
    }

    public static List getShapelessInputs(ShapelessRecipes recipe) {
        try {
            Field[] fields = ShapelessRecipes.class.getDeclaredFields();
            for (int i = 0; i < fields.length; i++) {
                Field field = fields[i];
                if (List.class.isAssignableFrom(field.getType())) {
                    field.setAccessible(true);
                    return (List) field.get(recipe);
                }
            }
        } catch (Exception e) {
            return null;
        }
        return null;
    }

    public static ItemStack[] getShapedInputs(ShapedRecipes recipe) {
        try {
            Field[] fields = ShapedRecipes.class.getDeclaredFields();
            for (int i = 0; i < fields.length; i++) {
                Field field = fields[i];
                if (field.getType().isArray()
                    && ItemStack.class.equals(field.getType().getComponentType())) {
                    field.setAccessible(true);
                    return (ItemStack[]) field.get(recipe);
                }
            }
        } catch (Exception e) {
            return null;
        }
        return null;
    }

    public static int[] getShapedDimensions(ShapedRecipes recipe, int itemCount) {
        try {
            Field[] fields = ShapedRecipes.class.getDeclaredFields();
            int[] candidates = new int[3];
            int count = 0;
            for (int i = 0; i < fields.length; i++) {
                Field field = fields[i];
                if (field.getType() == Integer.TYPE) {
                    field.setAccessible(true);
                    candidates[count++] = field.getInt(recipe);
                    if (count == candidates.length) {
                        break;
                    }
                }
            }
            for (int i = 0; i < count; i++) {
                for (int j = 0; j < count; j++) {
                    if (i == j) {
                        continue;
                    }
                    int width = candidates[i];
                    int height = candidates[j];
                    if (width > 0 && height > 0 && width * height == itemCount) {
                        return new int[] { width, height };
                    }
                }
            }
        } catch (Exception e) {
            return null;
        }
        return null;
    }

    public static ItemStack getRecipeOutput(Object recipe) {
        if (recipe instanceof SmeltingRecipe) {
            return ((SmeltingRecipe) recipe).getRecipeOutput();
        }
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
            List items = getShapelessInputs((ShapelessRecipes) craft);
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

    public static boolean matchesInputs(IRecipe recipe, List inputs) {
        List recipeInputs = collectRecipeInputs(recipe);
        if (recipeInputs == null) {
            return false;
        }
        List remaining = new ArrayList(recipeInputs);
        for (int i = 0; i < inputs.size(); i++) {
            ItemStack target = (ItemStack) inputs.get(i);
            boolean matched = false;
            for (int j = 0; j < remaining.size(); j++) {
                ItemStack candidate = (ItemStack) remaining.get(j);
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

    public static List collectRecipeInputs(IRecipe recipe) {
        if (recipe instanceof ShapedRecipes) {
            ItemStack[] items = getShapedInputs((ShapedRecipes) recipe);
            if (items == null) {
                return null;
            }
            List list = new ArrayList();
            for (int i = 0; i < items.length; i++) {
                if (items[i] != null) {
                    list.add(items[i]);
                }
            }
            return list;
        }
        if (recipe instanceof ShapelessRecipes) {
            List items = getShapelessInputs((ShapelessRecipes) recipe);
            if (items == null) {
                return null;
            }
            List list = new ArrayList();
            for (int i = 0; i < items.size(); i++) {
                ItemStack stack = normalizeIngredient(items.get(i));
                if (stack != null) {
                    list.add(stack);
                }
            }
            return list;
        }
        return null;
    }

    public static boolean matchesInputList(List recipeInputs, List desiredInputs) {
        List remaining = new ArrayList(recipeInputs);
        for (int i = 0; i < desiredInputs.size(); i++) {
            ItemStack target = (ItemStack) desiredInputs.get(i);
            boolean matched = false;
            for (int j = 0; j < remaining.size(); j++) {
                ItemStack candidate = (ItemStack) remaining.get(j);
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
