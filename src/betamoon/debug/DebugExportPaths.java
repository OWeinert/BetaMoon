package betamoon.debug;

import java.io.File;
import java.io.IOException;
import net.minecraft.client.Minecraft;

/**
 * Resolves debug export paths under the Minecraft directory.
 */
final class DebugExportPaths {
    private static final String DEBUG_DIR = "betamoon_debug";

    private DebugExportPaths() {
    }

    /**
     * Resolves a file within the debug export directory.
     */
    static File resolveDebugFile(String name) throws IOException {
        File debugDir = resolveDebugDir();
        if (debugDir == null) {
            throw new IOException("Debug export directory could not be created.");
        }
        return new File(debugDir, name);
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
    private static File resolveDebugDir() {
        File minecraftDir = Minecraft.getMinecraftDir();
        File debugDir = new File(minecraftDir, DEBUG_DIR);
        if (!debugDir.isDirectory()) {
            debugDir.mkdirs();
        }
        return debugDir.isDirectory() ? debugDir : null;
    }
}
