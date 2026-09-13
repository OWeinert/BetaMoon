package betamoon.debug;

/**
 * Entry point for debug exports.
 */
public final class DebugExports {
    private DebugExports() {
    }

    /**
     * Exports recipe types, recipes, blocks, and items in sequence.
     *
     * @return first exception encountered or null on success
     */
    public static Exception exportAll() {
        Exception error = DebugRecipeTypeExporter.exportRecipeTypes();
        if (error != null) {
            return error;
        }
        error = DebugRecipeExporter.exportRecipes();
        if (error != null) {
            return error;
        }
        error = DebugBlockExporter.exportBlocks();
        if (error != null) {
            return error;
        }
        return DebugItemExporter.exportItems();
    }

    /**
     * Exports recipe type schemas into the debug recipe types file.
     *
     * @return exception when export fails, otherwise null
     */
    public static Exception exportRecipeTypes() {
        return DebugRecipeTypeExporter.exportRecipeTypes();
    }

    /**
     * Exports recipes into the debug recipes file.
     *
     * @return exception when export fails, otherwise null
     */
    public static Exception exportRecipes() {
        return DebugRecipeExporter.exportRecipes();
    }

    /**
     * Exports blocks into the debug blocks file.
     *
     * @return exception when export fails, otherwise null
     */
    public static Exception exportBlocks() {
        return DebugBlockExporter.exportBlocks();
    }

    /**
     * Exports items into the debug items file.
     *
     * @return exception when export fails, otherwise null
     */
    public static Exception exportItems() {
        return DebugItemExporter.exportItems();
    }

    /**
     * Returns the absolute path for the debug export directory.
     */
    public static String getDebugDirPath() {
        return DebugExportPaths.getDebugDirPath();
    }
}
