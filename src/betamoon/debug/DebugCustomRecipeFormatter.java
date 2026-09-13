package betamoon.debug;

import betamoon.recipes.custom.CustomRecipes;
import betamoon.recipes.custom.RecipeDefinition;
import betamoon.recipes.custom.RecipeTypes;
import betamoon.recipes.custom.RecipeValues;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.src.ItemStack;
import org.luaj.vm2.LuaValue;

/**
 * Formats custom recipe instances as readable domain data.
 */
final class DebugCustomRecipeFormatter {
    private final Map<String, Integer> headingCounts = new LinkedHashMap<String, Integer>();

    String format(CustomRecipes.Entry entry) {
        RecipeDefinition recipe = entry.effective;
        ItemStack primaryOutput = recipe.getOutput(entry.type.primary);
        StringBuilder out = new StringBuilder();
        out.append(uniqueHeading(entry.type.name, primaryOutput)).append(" : ")
                .append(DebugItemStackFormatter.format(primaryOutput)).append(" <-");
        appendIngredients(out, recipe);
        appendAdditionalOutputs(out, entry, recipe);
        appendValues(out, "data", recipe.getData());
        appendValues(out, "conditions", recipe.getConditions());
        out.append("\n    key = ").append(DebugValueFormatter.quote(entry.key));
        out.append("\n    owner = ").append(DebugValueFormatter.quote(entry.owner));
        if (recipe.priority != 0) {
            out.append("\n    priority = ").append(recipe.priority);
        }
        if (!recipe.enabled) {
            out.append("\n    enabled = false");
        }
        return out.toString();
    }

    private String uniqueHeading(String typeName, ItemStack output) {
        int amount = output == null ? 0 : output.stackSize;
        String base = typeName + "/" + DebugItemStackFormatter.identifier(output) + "_" + amount;
        Integer previous = headingCounts.get(base);
        int index = previous == null ? 0 : previous.intValue() + 1;
        headingCounts.put(base, Integer.valueOf(index));
        return index == 0 ? base : base + "_" + index;
    }

    private static void appendIngredients(StringBuilder out, RecipeDefinition recipe) {
        out.append("\n    ingredients:");
        for (RecipeTypes.Role role : recipe.type.ingredients.values()) {
            RecipeDefinition.Ingredient item = recipe.ingredients.get(role.name);
            if (item != null) {
                appendIngredient(out, role.name, item, 8);
            }
            RecipeDefinition.PoolIngredients pool = recipe.getIngredientPools().get(role.name);
            if (pool != null) {
                out.append("\n        ").append(DebugValueFormatter.quote(role.name)).append(":");
                for (int i = 0; i < pool.requirements.size(); i++) {
                    appendIngredient(out, "[" + (i + 1) + "]", pool.requirements.get(i), 12);
                }
            }
            RecipeDefinition.GridIngredients grid = recipe.getIngredientGrids().get(role.name);
            if (grid != null) {
                appendGrid(out, role.name, grid);
            }
        }
    }

    private static void appendIngredient(StringBuilder out, String role, RecipeDefinition.Ingredient ingredient,
            int indentation) {
        List<ItemStack> alternatives = ingredient.getAlternatives();
        out.append('\n').append(spaces(indentation))
                .append(role.charAt(0) == '[' ? role : DebugValueFormatter.quote(role)).append(": ");
        if (alternatives.size() == 1) {
            out.append(DebugItemStackFormatter.format(alternatives.get(0), ingredient.count, ingredient.anyDamage));
        } else {
            out.append("one of");
            for (ItemStack alternative : alternatives) {
                out.append('\n').append(spaces(indentation + 4))
                        .append(DebugItemStackFormatter.format(alternative, ingredient.count, ingredient.anyDamage));
            }
        }
        ItemStack remainder = ingredient.getRemainder();
        if (remainder != null) {
            out.append('\n').append(spaces(indentation + 4)).append("remainder -> ")
                    .append(DebugValueFormatter.quote(ingredient.remainderOutput)).append(": ")
                    .append(DebugItemStackFormatter.format(remainder));
        }
    }

    private static void appendGrid(StringBuilder out, String role, RecipeDefinition.GridIngredients grid) {
        out.append("\n        ").append(DebugValueFormatter.quote(role)).append(":");
        out.append("\n            pattern = {");
        for (String row : grid.pattern) {
            out.append("\n                ").append(DebugValueFormatter.quote(row)).append(',');
        }
        out.append("\n            }");
        out.append("\n            key:");
        for (Map.Entry<Character, RecipeDefinition.Ingredient> entry : grid.key.entrySet()) {
            appendIngredient(out, "[" + DebugValueFormatter.quote(String.valueOf(entry.getKey())) + "]",
                    entry.getValue(), 16);
        }
    }

    private static void appendAdditionalOutputs(StringBuilder out, CustomRecipes.Entry entry, RecipeDefinition recipe) {
        Map<String, ItemStack> outputs = recipe.getOutputs();
        boolean headingWritten = false;
        for (Map.Entry<String, ItemStack> output : outputs.entrySet()) {
            if (entry.type.primary.equals(output.getKey())) {
                continue;
            }
            if (!headingWritten) {
                out.append("\n    additional outputs:");
                headingWritten = true;
            }
            out.append("\n        ").append(DebugValueFormatter.quote(output.getKey())).append(": ")
                    .append(DebugItemStackFormatter.format(output.getValue()));
        }
        for (Map.Entry<String, List<ItemStack>> output : recipe.getOutputPools().entrySet()) {
            if (!headingWritten) {
                out.append("\n    additional outputs:");
                headingWritten = true;
            }
            out.append("\n        ").append(DebugValueFormatter.quote(output.getKey())).append(":");
            for (int i = 0; i < output.getValue().size(); i++) {
                out.append("\n            [").append(i + 1).append("]: ")
                        .append(DebugItemStackFormatter.format(output.getValue().get(i)));
            }
        }
    }

    private static String spaces(int count) {
        StringBuilder result = new StringBuilder(count);
        for (int i = 0; i < count; i++) {
            result.append(' ');
        }
        return result.toString();
    }

    private static void appendValues(StringBuilder out, String heading, LuaValue values) {
        List<String> keys = RecipeValues.keys(values, heading);
        if (keys.isEmpty()) {
            return;
        }
        out.append("\n    ").append(heading).append(":");
        for (String key : keys) {
            out.append("\n        ").append(key).append(" = ").append(DebugValueFormatter.format(values.get(key)));
        }
    }
}
