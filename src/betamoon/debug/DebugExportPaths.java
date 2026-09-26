package betamoon.debug;

import betamoon.io.IoUtils;
import java.io.File;
import net.minecraft.client.Minecraft;

/**
 * Resolves debug export paths under the Minecraft directory.
 */
final class DebugExportPaths {
    private static final String DEBUG_DIR = "betamoon_debug";

    private DebugExportPaths() {
    }

    /**
     * Returns the absolute path for the debug export directory.
     */
    static String getDebugDirPath() {
        File debugDir = resolveDebugDir();
        return debugDir == null ? "" : debugDir.getAbsolutePath();
    }

    /**
     * Resolves the root debug export directory, creating it if needed.
     */
    static File resolveDebugDir() {
        File minecraftDir = Minecraft.getMinecraftDir();
        return IoUtils.resolveChildDirectory(minecraftDir, DEBUG_DIR, true);
    }
}
