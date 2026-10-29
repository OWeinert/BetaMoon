package betamoon.debug;

import java.io.File;
import betamoon.interop.discovery.NativeDiscoveryRuntime;
import java.util.Collections;

/**
 * Entry point for debug exports.
 */
public final class DebugExports {
    private DebugExports() {
    }

    /** Exports every catalog category while preserving independent successes. */
    public static DebugExportResult exportAll() {
        return exportAll(false);
    }

    /**
     * Includes bulky native catalogs only when explicitly selected for this run.
     */
    public static DebugExportResult exportAll(boolean includeNativeCatalogs) {
        File root = DebugExportPaths.resolveDebugDir();
        if (root == null) {
            return DebugExportResult.failed(null, "Debug export directory could not be created.", 0L);
        }
        return DebugExportSession.run(root, "complete", DebugExportCatalog.completeDefinitions(includeNativeCatalogs));
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
     * Captures only detached native metadata/counters on the calling client thread.
     */
    public static DebugNativeExportTask exportNativeAsync() {
        NativeDiscoveryRuntime.requireClientAccess();
        return DebugNativeExportTask.start(DebugExportPaths.resolveDebugDir(), NativeDiscoveryRuntime.snapshot(),
                NativeDiscoveryRuntime.status());
    }

    /**
     * Live categories stay on the caller's client thread; only the captured native
     * category is deferred.
     */
    public static DebugNativeExportTask exportAllWithNativeAsync() {
        NativeDiscoveryRuntime.requireClientAccess();
        return DebugNativeExportTask.start(DebugExportPaths.resolveDebugDir(), NativeDiscoveryRuntime.snapshot(),
                NativeDiscoveryRuntime.status(), true);
    }

    /**
     * Returns the absolute path for the debug export directory.
     */
    public static String getDebugDirPath() {
        return DebugExportPaths.getDebugDirPath();
    }
}
