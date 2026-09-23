package betamoon.debug;

import betamoon.recipes.RecipeModificationHandler;
import betamoon.recipes.custom.CustomRecipes;
import java.io.BufferedWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import net.minecraft.src.IRecipe;

/**
 * Exports recipe data into the debug recipes file.
 */
final class DebugRecipeExporter {
    private DebugRecipeExporter() {
    }

    static void export(final DebugExportSession session) throws Exception {
        ensureRecipeMap();
        session.writeTextFile("recipes.txt", new DebugExportSession.TextContent() {
            @Override
            public int write(BufferedWriter writer) throws IOException {
                Map<String, IRecipe> recipeMap = RecipeModificationHandler.getRecipeMap();
                List<Map.Entry<String, IRecipe>> nativeRecipes = new ArrayList<Map.Entry<String, IRecipe>>(
                        recipeMap.entrySet());
                Collections.sort(nativeRecipes, new Comparator<Map.Entry<String, IRecipe>>() {
                    @Override
                    public int compare(Map.Entry<String, IRecipe> left, Map.Entry<String, IRecipe> right) {
                        return left.getKey().compareTo(right.getKey());
                    }
                });
                int records = 0;
                boolean wroteRecipe = false;
                for (Map.Entry<String, IRecipe> entry : nativeRecipes) {
                    String line = DebugRecipeFormatter.formatRecipeLine(entry.getKey(), entry.getValue());
                    if (line != null) {
                        writer.write(line);
                        writer.newLine();
                        wroteRecipe = true;
                        records++;
                    }
                }
                List<CustomRecipes.Entry> customRecipes = new ArrayList<CustomRecipes.Entry>(CustomRecipes.all());
                Collections.sort(customRecipes, new Comparator<CustomRecipes.Entry>() {
                    @Override
                    public int compare(CustomRecipes.Entry left, CustomRecipes.Entry right) {
                        return left.key.compareTo(right.key);
                    }
                });
                DebugCustomRecipeFormatter customFormatter = new DebugCustomRecipeFormatter();
                for (CustomRecipes.Entry entry : customRecipes) {
                    if (wroteRecipe) {
                        writer.newLine();
                    }
                    writer.write(customFormatter.format(entry));
                    writer.newLine();
                    wroteRecipe = true;
                    records++;
                }
                return records;
            }
        });
    }

    /**
     * Ensures the recipe map is ready before exporting.
     */
    private static void ensureRecipeMap() {
        if (RecipeModificationHandler.getRecipeMap() == null) {
            throw new IllegalStateException("Recipe map is not initialized.");
        }
    }

}
