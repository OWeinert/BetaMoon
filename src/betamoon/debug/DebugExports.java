package betamoon.debug;

import java.io.File;
import java.util.Collections;

/**
 * Entry point for debug exports.
 */
public final class DebugExports {
    private DebugExports() {
    }

    /** Exports every catalog category while preserving independent successes. */
    public static DebugExportResult exportAll() {
        File root = DebugExportPaths.resolveDebugDir();
        if (root == null) {
            return DebugExportResult.failed(null, "Debug export directory could not be created.", 0L);
        }
        return DebugExportSession.run(root, "complete", DebugExportCatalog.completeDefinitions());
    }

    /** Exports one catalog category. */
    public static DebugExportResult export(DebugExportDefinition definition) {
        if (definition == null || DebugExportCatalog.find(definition.getId()) != definition) {
            return DebugExportResult.failed(null, "Unknown debug export category.", 0L);
        }
        File root = DebugExportPaths.resolveDebugDir();
        if (root == null) {
            return DebugExportResult.failed(null, "Debug export directory could not be created.", 0L);
        }
        return DebugExportSession.run(root, definition.getId(), Collections.singletonList(definition));
    }

    /**
     * Returns the absolute path for the debug export directory.
     */
    public static String getDebugDirPath() {
        return DebugExportPaths.getDebugDirPath();
    }
}
