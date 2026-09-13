package betamoon.debug;

import betamoon.BetaMoonMain;
import betamoon.io.IoUtils;
import betamoon.recipes.custom.RecipeTypes;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Level;

/**
 * Exports recipe type schemas into their own debug file.
 */
final class DebugRecipeTypeExporter {
    private static final java.util.logging.Logger LOGGER = BetaMoonMain.LOGGER;

    private DebugRecipeTypeExporter() {
    }

    static Exception exportRecipeTypes() {
        File outputFile;
        try {
            outputFile = DebugExportPaths.resolveDebugFile("recipe_types.txt");
        } catch (IOException e) {
            return e;
        }
        BufferedWriter writer = null;
        try {
            writer = new BufferedWriter(new FileWriter(outputFile));
            List<RecipeTypes.Type> builtInTypes = new ArrayList<RecipeTypes.Type>();
            List<RecipeTypes.Type> customTypes = new ArrayList<RecipeTypes.Type>();
            partitionTypes(builtInTypes, customTypes);
            sortByName(customTypes);
            writeTypes(writer, builtInTypes);
            if (!builtInTypes.isEmpty() && !customTypes.isEmpty()) {
                writer.newLine();
            }
            writeTypes(writer, customTypes);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Debug export failed: recipe types", e);
            return e;
        } finally {
            IoUtils.closeQuietly(writer, "debug recipe types");
        }
        return null;
    }

    private static void partitionTypes(List<RecipeTypes.Type> builtInTypes, List<RecipeTypes.Type> customTypes) {
        for (RecipeTypes.Type type : RecipeTypes.all()) {
            if (type.builtin) {
                builtInTypes.add(type);
            } else {
                customTypes.add(type);
            }
        }
    }

    private static void sortByName(List<RecipeTypes.Type> types) {
        Collections.sort(types, new Comparator<RecipeTypes.Type>() {
            public int compare(RecipeTypes.Type first, RecipeTypes.Type second) {
                return first.name.compareTo(second.name);
            }
        });
    }

    private static void writeTypes(BufferedWriter writer, List<RecipeTypes.Type> types) throws IOException {
        for (int i = 0; i < types.size(); i++) {
            if (i > 0) {
                writer.newLine();
            }
            writer.write(DebugRecipeTypeFormatter.format(types.get(i)));
            writer.newLine();
        }
    }
}
