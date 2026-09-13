package betamoon.debug;

import betamoon.BetaMoonMain;
import betamoon.io.IoUtils;
import betamoon.recipes.RecipeModificationHandler;
import betamoon.recipes.custom.CustomRecipes;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Map;
import java.util.logging.Level;
import net.minecraft.src.IRecipe;

/**
 * Exports recipe data into the debug recipes file.
 */
final class DebugRecipeExporter {
    private static final java.util.logging.Logger LOGGER = BetaMoonMain.LOGGER;

    private DebugRecipeExporter() {
    }

    /**
     * Exports recipe data into the debug recipes file.
     *
     * @return exception when export fails, otherwise null
     */
    static Exception exportRecipes() {
        try {
            ensureRecipeMap();
        } catch (Exception e) {
            return e;
        }
        File outputFile;
        try {
            outputFile = DebugExportPaths.resolveDebugFile("recipes.txt");
        } catch (IOException e) {
            return e;
        }
        BufferedWriter writer = null;
        try {
            writer = new BufferedWriter(new FileWriter(outputFile));
            Map<String, IRecipe> recipeMap = RecipeModificationHandler.getRecipeMap();
            boolean wroteRecipe = false;
            // Iterate deterministically in map order so keys line up with the handler
            // output.
            for (java.util.Iterator<Map.Entry<String, IRecipe>> it = recipeMap.entrySet().iterator(); it.hasNext();) {
                Map.Entry<String, IRecipe> entry = it.next();
                String name = (String) entry.getKey();
                Object recipe = entry.getValue();
                // Each entry produces a single formatted line unless skipped.
                String line = DebugRecipeFormatter.formatRecipeLine(name, recipe);
                if (line != null) {
                    writer.write(line);
                    writer.newLine();
                    wroteRecipe = true;
                }
            }
            DebugCustomRecipeFormatter customFormatter = new DebugCustomRecipeFormatter();
            for (CustomRecipes.Entry entry : CustomRecipes.all()) {
                if (wroteRecipe) {
                    writer.newLine();
                }
                writer.write(customFormatter.format(entry));
                writer.newLine();
                wroteRecipe = true;
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Debug export failed: recipes", e);
            return e;
        } finally {
            IoUtils.closeQuietly(writer, "debug recipes");
        }
        return null;
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
