package betamoon.debug;

import betamoon.recipes.RecipeModificationHandler;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Exports recipe data into the debug recipes file.
 */
final class DebugRecipeExporter {
    private static final Logger LOGGER = Logger.getLogger("BetaMoon");

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
            Map recipeMap = RecipeModificationHandler.getRecipeMap();
            // Iterate deterministically in map order so keys line up with the handler output.
            for (java.util.Iterator it = recipeMap.entrySet().iterator(); it.hasNext();) {
                Map.Entry entry = (Map.Entry) it.next();
                String name = (String) entry.getKey();
                Object recipe = entry.getValue();
                // Each entry produces a single formatted line unless skipped.
                String line = DebugRecipeFormatter.formatRecipeLine(name, recipe);
                if (line != null) {
                    writer.write(line);
                    writer.newLine();
                }
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Debug export failed: recipes", e);
            return e;
        } finally {
            closeQuietly(writer);
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

    /**
     * Closes a writer without throwing.
     */
    private static void closeQuietly(BufferedWriter writer) {
        if (writer == null) {
            return;
        }
        try {
            writer.close();
        } catch (IOException e) {
            LOGGER.log(Level.FINE, "Debug export close failed", e);
        }
    }
}
