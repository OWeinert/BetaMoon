package betamoon.recipes;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.src.Block;
import net.minecraft.src.IRecipe;
import net.minecraft.src.Item;
import net.minecraft.src.ItemStack;
import net.minecraft.src.ShapedRecipes;
import net.minecraft.src.ShapelessRecipes;

/** Central structural inspection for native crafting and smelting recipes. */
public final class NativeRecipeInspector {
    private static final Field SHAPED_INPUTS = findField(ShapedRecipes.class, ItemStack[].class);
    private static final Field SHAPELESS_INPUTS = findAssignableField(ShapelessRecipes.class, List.class);
    private static final List<Field> SHAPED_INTEGER_FIELDS = findFields(ShapedRecipes.class, Integer.TYPE);

    private NativeRecipeInspector() {
    }

    public static NativeRecipeKind kind(IRecipe recipe) {
        return NativeRecipeKind.of(recipe);
    }

    public static ItemStack output(IRecipe recipe) {
        return recipe == null ? null : recipe.getRecipeOutput();
    }

    public static Object identity(IRecipe recipe) {
        return recipe instanceof SmeltingRecipe ? Integer.valueOf(((SmeltingRecipe) recipe).getInputId()) : recipe;
    }

    public static Object ownershipTarget(IRecipe recipe) {
        return recipe instanceof SmeltingRecipe ? ((SmeltingRecipe) recipe).getOutput() : recipe;
    }

    public static boolean representsSameRegistration(IRecipe existing, IRecipe current) {
        if (existing instanceof SmeltingRecipe && current instanceof SmeltingRecipe) {
            SmeltingRecipe left = (SmeltingRecipe) existing;
            SmeltingRecipe right = (SmeltingRecipe) current;
            return left.getInputId() == right.getInputId() && left.getOutput() == right.getOutput();
        }
        return existing == current;
    }

    public static ItemStack[] shapedInputs(ShapedRecipes recipe) {
        if (recipe == null || SHAPED_INPUTS == null) {
            return null;
        }
        try {
            return (ItemStack[]) SHAPED_INPUTS.get(recipe);
        } catch (IllegalAccessException ignored) {
            return null;
        }
    }

    public static List<?> shapelessInputs(ShapelessRecipes recipe) {
        if (recipe == null || SHAPELESS_INPUTS == null) {
            return null;
        }
        try {
            return (List<?>) SHAPELESS_INPUTS.get(recipe);
        } catch (IllegalAccessException ignored) {
            return null;
        }
    }

    public static int[] shapedDimensions(ShapedRecipes recipe, int itemCount) {
        if (recipe == null || itemCount <= 0) {
            return null;
        }
        try {
            for (int i = 0; i < SHAPED_INTEGER_FIELDS.size(); i++) {
                int width = SHAPED_INTEGER_FIELDS.get(i).getInt(recipe);
                for (int j = 0; j < SHAPED_INTEGER_FIELDS.size(); j++) {
                    if (i == j) {
                        continue;
                    }
                    int height = SHAPED_INTEGER_FIELDS.get(j).getInt(recipe);
                    if (width > 0 && height > 0 && width * height == itemCount) {
                        return new int[]{width, height};
                    }
                }
            }
        } catch (IllegalAccessException ignored) {
            return null;
        }
        return null;
    }

    public static List<ItemStack> collectInputs(IRecipe recipe) {
        if (recipe instanceof ShapedRecipes) {
            ItemStack[] inputs = shapedInputs((ShapedRecipes) recipe);
            if (inputs == null) {
                return null;
            }
            List<ItemStack> result = new ArrayList<>();
            for (int i = 0; i < inputs.length; i++) {
                if (inputs[i] != null) {
                    result.add(inputs[i]);
                }
            }
            return result;
        }
        if (recipe instanceof ShapelessRecipes) {
            List<?> inputs = shapelessInputs((ShapelessRecipes) recipe);
            if (inputs == null) {
                return null;
            }
            List<ItemStack> result = new ArrayList<>();
            for (int i = 0; i < inputs.size(); i++) {
                ItemStack stack = normalizeIngredient(inputs.get(i));
                if (stack != null) {
                    result.add(stack);
                }
            }
            return result;
        }
        return null;
    }

    public static ItemStack normalizeIngredient(Object ingredient) {
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

    private static Field findField(Class<?> owner, Class<?> type) {
        try {
            Field[] fields = owner.getDeclaredFields();
            for (int i = 0; i < fields.length; i++) {
                if (fields[i].getType() == type) {
                    fields[i].setAccessible(true);
                    return fields[i];
                }
            }
        } catch (Exception ignored) {
            return null;
        }
        return null;
    }

    private static Field findAssignableField(Class<?> owner, Class<?> type) {
        try {
            Field[] fields = owner.getDeclaredFields();
            for (int i = 0; i < fields.length; i++) {
                if (type.isAssignableFrom(fields[i].getType())) {
                    fields[i].setAccessible(true);
                    return fields[i];
                }
            }
        } catch (Exception ignored) {
            return null;
        }
        return null;
    }

    private static List<Field> findFields(Class<?> owner, Class<?> type) {
        List<Field> matches = new ArrayList<>();
        try {
            Field[] fields = owner.getDeclaredFields();
            for (int i = 0; i < fields.length; i++) {
                if (fields[i].getType() == type) {
                    fields[i].setAccessible(true);
                    matches.add(fields[i]);
                }
            }
        } catch (Exception ignored) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(matches);
    }
}
