package betamoon.debug;

import betamoon.recipes.NativeRecipeInspector;
import betamoon.recipes.NativeRecipeKind;
import betamoon.recipes.SmeltingRecipe;
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
        NativeRecipeKind kind = NativeRecipeKind.of(recipe);
        if (kind == NativeRecipeKind.SMELTING) {
            SmeltingRecipe smelting = (SmeltingRecipe) recipe;
            ItemStack output = smelting.getRecipeOutput();
            String outputText = DebugItemStackFormatter.format(output);
            String inputText = formatSmeltingInput(smelting.getInputId());
            return name + " : " + outputText + " <- " + inputText;
        }
        if (kind == NativeRecipeKind.SHAPED) {
            ShapedRecipes shaped = (ShapedRecipes) recipe;
            ItemStack output = shaped.getRecipeOutput();
            String outputText = DebugItemStackFormatter.format(output);
            // Build a shape matrix plus key list for the formatted output.
            ShapeData shapeData = buildShapeData(shaped);
            if (shapeData == null) {
                return null;
            }
            String shapeText = shapeData.shape;
            String inputsText = shapeData.inputs;
            return name + " : " + outputText + " <-\n    " + shapeText + "\n" + inputsText;
        }
        if (kind == NativeRecipeKind.SHAPELESS) {
            ShapelessRecipes shapeless = (ShapelessRecipes) recipe;
            ItemStack output = shapeless.getRecipeOutput();
            String outputText = DebugItemStackFormatter.format(output);
            // Expand each ingredient to its own indented line.
            String inputsText = formatInputList(NativeRecipeInspector.shapelessInputs(shapeless));
            return name + " : " + outputText + " <-\n" + inputsText;
        }
        if (recipe instanceof IRecipe) {
            ItemStack output = ((IRecipe) recipe).getRecipeOutput();
            String outputText = DebugItemStackFormatter.format(output);
            return name + " : " + outputText;
        }
        return null;
    }

    /**
     * Builds the formatted shape matrix and key list for shaped recipes.
     */
    private static ShapeData buildShapeData(ShapedRecipes shaped) {
        ItemStack[] items = NativeRecipeInspector.shapedInputs(shaped);
        if (items == null) {
            return null;
        }
        // Infer width/height before building the shape grid.
        int[] dimensions = NativeRecipeInspector.shapedDimensions(shaped, items.length);
        if (dimensions == null) {
            return null;
        }
        int width = dimensions[0];
        int height = dimensions[1];
        Map<String, Character> keyMap = new LinkedHashMap<>();
        StringBuilder shape = new StringBuilder();
        // Build a readable shape grid and collect unique ingredient keys.
        for (int row = 0; row < height; row++) {
            if (row > 0) {
                shape.append("\n    ");
            }
            shape.append("[");
            // Walk the internal grid so the export shows the same layout as the crafting
            // table.
            for (int col = 0; col < width; col++) {
                ItemStack stack = items[col + row * width];
                if (stack == null) {
                    shape.append(' ');
                    continue;
                }
                String signature = stack.itemID + ":" + stack.getItemDamage();
                Character key = keyMap.get(signature);
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
        for (java.util.Iterator<Map.Entry<String, Character>> it = keyMap.entrySet().iterator(); it.hasNext();) {
            Map.Entry<String, Character> entry = it.next();
            String signature = (String) entry.getKey();
            Character key = (Character) entry.getValue();
            // Resolve a representative stack for the signature so we can format it once.
            ItemStack input = findStackForSignature(items, signature);
            if (inputs.length() > 0) {
                inputs.append("\n");
            }
            inputs.append("    \"").append(key.charValue()).append("\": ")
                    .append(DebugItemStackFormatter.format(input));
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
     * Formats a list of item stacks, one per line.
     */
    private static String formatInputList(List<?> inputs) {
        if (inputs == null || inputs.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        // Each ingredient prints on its own indented line.
        for (int i = 0; i < inputs.size(); i++) {
            ItemStack stack = NativeRecipeInspector.normalizeIngredient(inputs.get(i));
            if (out.length() > 0) {
                out.append("\n");
            }
            out.append("    ").append(DebugItemStackFormatter.format(stack));
        }
        return out.toString();
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
