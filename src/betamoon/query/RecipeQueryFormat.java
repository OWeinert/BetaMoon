package betamoon.query;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import net.minecraft.src.ItemStack;
import net.minecraft.src.ShapedRecipes;
import net.minecraft.src.ShapelessRecipes;

final class RecipeQueryFormat {
    private RecipeQueryFormat() {
    }

    static String formatGrid(ItemStack[] grid, int width, int height) {
        if (grid == null || width <= 0 || height <= 0) {
            return "empty";
        }
        StringBuilder builder = new StringBuilder();
        builder.append(width).append("x").append(height).append("[");
        for (int y = 0; y < height; y++) {
            if (y > 0) {
                builder.append("|");
            }
            for (int x = 0; x < width; x++) {
                int index = x + y * width;
                ItemStack stack = index < grid.length ? grid[index] : null;
                if (stack == null || stack.itemID == 0) {
                    builder.append(" .");
                } else {
                    builder.append(" ").append(stack.itemID);
                    if (stack.getItemDamage() != 0) {
                        builder.append(":").append(stack.getItemDamage());
                    }
                }
            }
        }
        builder.append("]");
        return builder.toString();
    }

    static String stackLabel(ItemStack stack) {
        if (stack == null) {
            return "null";
        }
        StringBuilder builder = new StringBuilder();
        builder.append(stack.itemID);
        if (stack.stackSize != 1) {
            builder.append("x").append(stack.stackSize);
        }
        if (stack.getItemDamage() != 0) {
            builder.append(":").append(stack.getItemDamage());
        }
        return builder.toString();
    }

    static String formatInputs(List inputs) {
        if (inputs == null || inputs.isEmpty()) {
            return "[]";
        }
        StringBuilder builder = new StringBuilder();
        builder.append("[");
        for (int i = 0; i < inputs.size(); i++) {
            if (i > 0) {
                builder.append(", ");
            }
            ItemStack stack = (ItemStack) inputs.get(i);
            builder.append(stackLabel(stack));
        }
        builder.append("]");
        return builder.toString();
    }

    static void logShapedQueryFailure(Logger logger, Map source, ContentQueryRecipe.ShapedQuery query) {
        if (query == null || source == null) {
            return;
        }
        List candidates = new ArrayList();
        for (java.util.Iterator it = source.values().iterator(); it.hasNext();) {
            Object recipe = it.next();
            if (!(recipe instanceof ShapedRecipes)) {
                continue;
            }
            ShapedRecipes shaped = (ShapedRecipes) recipe;
            ItemStack output = shaped.getRecipeOutput();
            if (!RecipeQueryUtils.matchesOutput(output, query.output)) {
                continue;
            }
            candidates.add(shaped);
        }
        StringBuilder builder = new StringBuilder();
        builder.append("Query: shaped recipe not found. Output=")
            .append(stackLabel(query.output))
            .append(" pattern=")
            .append(formatGrid(query.grid, query.width, query.height));
        if (candidates.isEmpty()) {
            builder.append(" candidates=0");
            logger.warning(builder.toString());
            return;
        }
        builder.append(" candidates=").append(candidates.size());
        for (int i = 0; i < candidates.size(); i++) {
            ShapedRecipes shaped = (ShapedRecipes) candidates.get(i);
            ItemStack[] items = RecipeQueryUtils.getShapedInputs(shaped);
            int[] dims = items == null ? null : RecipeQueryUtils.getShapedDimensions(shaped, items.length);
            int width = dims == null ? RecipeQueryUtils.inferGridWidth(items == null ? 0 : items.length) : dims[0];
            int height = dims == null ? RecipeQueryUtils.inferGridHeight(items == null ? 0 : items.length, width) : dims[1];
            builder.append(" candidate[").append(i).append("]=")
                .append(formatGrid(items, width, height));
        }
        logger.warning(builder.toString());
    }

    static void logShapelessQueryFailure(Logger logger, Map source, ContentQueryRecipe.ShapelessQuery query) {
        if (query == null || source == null) {
            return;
        }
        List candidates = new ArrayList();
        for (java.util.Iterator it = source.values().iterator(); it.hasNext();) {
            Object recipe = it.next();
            if (!(recipe instanceof ShapelessRecipes)) {
                continue;
            }
            ShapelessRecipes shapeless = (ShapelessRecipes) recipe;
            ItemStack output = shapeless.getRecipeOutput();
            if (!RecipeQueryUtils.matchesOutput(output, query.output)) {
                continue;
            }
            candidates.add(shapeless);
        }
        StringBuilder builder = new StringBuilder();
        builder.append("Query: shapeless recipe not found. Output=")
            .append(stackLabel(query.output))
            .append(" inputs=")
            .append(formatInputs(query.inputs))
            .append(" candidates=")
            .append(candidates.size());
        for (int i = 0; i < candidates.size(); i++) {
            ShapelessRecipes shapeless = (ShapelessRecipes) candidates.get(i);
            List inputs = RecipeQueryUtils.collectRecipeInputs(shapeless);
            builder.append(" candidate[").append(i).append("]=")
                .append(formatInputs(inputs));
        }
        logger.warning(builder.toString());
    }

    static void logSmeltingQueryFailure(Logger logger, ContentQueryRecipe.SmeltingQuery query) {
        if (query == null) {
            return;
        }
        Map smelting = net.minecraft.src.FurnaceRecipes.smelting().getSmeltingList();
        StringBuilder builder = new StringBuilder();
        builder.append("Query: smelting recipe not found. Input=")
            .append(query.inputId)
            .append(" output=")
            .append(stackLabel(query.output));
        List candidates = new ArrayList();
        for (java.util.Iterator it = smelting.entrySet().iterator(); it.hasNext();) {
            Map.Entry entry = (Map.Entry) it.next();
            Object key = entry.getKey();
            if (key instanceof Integer && ((Integer) key).intValue() == query.inputId) {
                candidates.add(entry);
            }
        }
        builder.append(" candidates=").append(candidates.size());
        for (int i = 0; i < candidates.size(); i++) {
            Map.Entry entry = (Map.Entry) candidates.get(i);
            builder.append(" candidate[").append(i).append("]=")
                .append(stackLabel((ItemStack) entry.getValue()));
        }
        logger.warning(builder.toString());
    }
}
