package betamoon.debug;

import betamoon.recipes.SmeltingRecipe;
import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.src.IRecipe;
import net.minecraft.src.ItemStack;
import net.minecraft.src.ShapedRecipes;
import net.minecraft.src.ShapelessRecipes;

/**
 * Formats recipes for debug export.
 */
final class DebugRecipeFormatter {
    private DebugRecipeFormatter() {
    }

    /**
     * Formats a recipe line according to the debug export specification.
     */
    static String formatRecipeLine(String name, Object recipe) {
        if (recipe instanceof SmeltingRecipe) {
            SmeltingRecipe smelting = (SmeltingRecipe) recipe;
            ItemStack output = smelting.getRecipeOutput();
            String outputText = formatItemStack(output);
            String inputText = formatSmeltingInput(smelting.getInputId());
            return name + " : " + outputText + " <- " + inputText;
        }
        if (recipe instanceof ShapedRecipes) {
            ShapedRecipes shaped = (ShapedRecipes) recipe;
            ItemStack output = shaped.getRecipeOutput();
            String outputText = formatItemStack(output);
            // Build a shape matrix plus key list for the formatted output.
            ShapeData shapeData = buildShapeData(shaped);
            if (shapeData == null) {
                return null;
            }
            String shapeText = shapeData.shape;
            String inputsText = shapeData.inputs;
            return name + " : " + outputText + " <-\n    " + shapeText + "\n" + inputsText;
        }
        if (recipe instanceof ShapelessRecipes) {
            ShapelessRecipes shapeless = (ShapelessRecipes) recipe;
            ItemStack output = shapeless.getRecipeOutput();
            String outputText = formatItemStack(output);
            // Expand each ingredient to its own indented line.
            String inputsText = formatInputList(getShapelessInputs(shapeless));
            return name + " : " + outputText + " <-\n" + inputsText;
        }
        if (recipe instanceof IRecipe) {
            ItemStack output = ((IRecipe) recipe).getRecipeOutput();
            String outputText = formatItemStack(output);
            return name + " : " + outputText;
        }
        return null;
    }

    /**
     * Builds the formatted shape matrix and key list for shaped recipes.
     */
    private static ShapeData buildShapeData(ShapedRecipes shaped) {
        ItemStack[] items = getShapedInputs(shaped);
        if (items == null) {
            return null;
        }
        // Infer width/height before building the shape grid.
        int[] dimensions = getShapedDimensions(shaped, items.length);
        if (dimensions == null) {
            return null;
        }
        int width = dimensions[0];
        int height = dimensions[1];
        Map keyMap = new LinkedHashMap();
        StringBuilder shape = new StringBuilder();
        // Build a readable shape grid and collect unique ingredient keys.
        for (int row = 0; row < height; row++) {
            if (row > 0) {
                shape.append("\n    ");
            }
            shape.append("[");
            // Walk the internal grid so the export shows the same layout as the crafting table.
            for (int col = 0; col < width; col++) {
                ItemStack stack = items[col + row * width];
                if (stack == null) {
                    shape.append(' ');
                    continue;
                }
                String signature = stack.itemID + ":" + stack.getItemDamage();
                Character key = (Character) keyMap.get(signature);
                if (key == null) {
                    // Assign a new display key the first time we see a unique ingredient.
                    int index = keyMap.size();
                    key = new Character(nextKey(index));
                    keyMap.put(signature, key);
                }
                shape.append(key.charValue());
            }
            shape.append("]");
        }
        StringBuilder inputs = new StringBuilder();
        // Emit the key mapping list in the order the keys were assigned.
        for (java.util.Iterator it = keyMap.entrySet().iterator(); it.hasNext();) {
            Map.Entry entry = (Map.Entry) it.next();
            String signature = (String) entry.getKey();
            Character key = (Character) entry.getValue();
            // Resolve a representative stack for the signature so we can format it once.
            ItemStack input = findStackForSignature(items, signature);
            if (inputs.length() > 0) {
                inputs.append("\n");
            }
            inputs.append("    \"").append(key.charValue()).append("\": ").append(formatItemStack(input));
        }
        return new ShapeData(shape.toString(), inputs.toString());
    }

    /**
     * Returns the first stack that matches a cached signature.
     */
    private static ItemStack findStackForSignature(ItemStack[] items, String signature) {
        if (items == null) {
            return null;
        }
        // Match the first stack that shares the same item id and damage.
        for (int i = 0; i < items.length; i++) {
            ItemStack stack = items[i];
            if (stack == null) {
                continue;
            }
            String stackSignature = stack.itemID + ":" + stack.getItemDamage();
            if (signature.equals(stackSignature)) {
                return stack;
            }
        }
        return null;
    }

    /**
     * Returns the next display key for a shaped recipe.
     */
    private static char nextKey(int index) {
        // Use a small stable key set for 3x3 recipes.
        String keys = "ABCDEFGHI";
        if (index < 0 || index >= keys.length()) {
            return '?';
        }
        return keys.charAt(index);
    }

    /**
     * Reflects the shapeless input list from the recipe instance.
     */
    private static List getShapelessInputs(ShapelessRecipes recipe) {
        try {
            Field[] fields = ShapelessRecipes.class.getDeclaredFields();
            // Locate the first List field since mappings may rename it.
            for (int i = 0; i < fields.length; i++) {
                Field field = fields[i];
                if (List.class.isAssignableFrom(field.getType())) {
                    field.setAccessible(true);
                    return (List) field.get(recipe);
                }
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Reflects the shaped input grid from the recipe instance.
     */
    private static ItemStack[] getShapedInputs(ShapedRecipes recipe) {
        try {
            Field[] fields = ShapedRecipes.class.getDeclaredFields();
            // Locate the ItemStack[] field regardless of its mapped name.
            for (int i = 0; i < fields.length; i++) {
                Field field = fields[i];
                if (field.getType().isArray()
                    && ItemStack.class.equals(field.getType().getComponentType())) {
                    field.setAccessible(true);
                    return (ItemStack[]) field.get(recipe);
                }
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Attempts to infer width/height based on integer fields and grid size.
     */
    private static int[] getShapedDimensions(ShapedRecipes recipe, int itemCount) {
        try {
            Field[] fields = ShapedRecipes.class.getDeclaredFields();
            int[] candidates = new int[3];
            int count = 0;
            // Gather small integer fields and then infer width/height by matching the grid size.
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
                    // Shaped recipes store width/height as separate fields, so the product should match the grid.
                    if (width > 0 && height > 0 && width * height == itemCount) {
                        return new int[] { width, height };
                    }
                }
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Formats a list of item stacks, one per line.
     */
    private static String formatInputList(List inputs) {
        if (inputs == null || inputs.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        // Each ingredient prints on its own indented line.
        for (int i = 0; i < inputs.size(); i++) {
            ItemStack stack = (ItemStack) inputs.get(i);
            if (out.length() > 0) {
                out.append("\n");
            }
            out.append("    ").append(formatItemStack(stack));
        }
        return out.toString();
    }

    /**
     * Formats an ItemStack according to the debug output spec.
     */
    private static String formatItemStack(ItemStack stack) {
        if (stack == null) {
            return "[item = [unknown / 0 / \"Unknown\"], amount = 0]";
        }
        int id = stack.itemID;
        // Pull both internal and localized names to keep debug output readable.
        String internalName = DebugExportNames.resolveInternalName(id, stack);
        String displayName = DebugExportNames.resolveDisplayName(id, stack);
        String idText = DebugExportNames.formatIdWithDamage(id, stack.getItemDamage());
        return "[item = [" + internalName + " / " + idText + " / \"" + displayName + "\"], amount = "
            + stack.stackSize + "]";
    }

    /**
     * Formats the smelting input entry according to the debug output spec.
     */
    private static String formatSmeltingInput(int inputId) {
        String internalName = DebugExportNames.resolveInternalName(inputId, null);
        String displayName = DebugExportNames.resolveDisplayName(inputId, null);
        return "[" + internalName + " / " + inputId + " / \"" + displayName + "\"]";
    }

    private static final class ShapeData {
        private final String shape;
        private final String inputs;

        private ShapeData(String shape, String inputs) {
            this.shape = shape;
            this.inputs = inputs;
        }
    }
}
