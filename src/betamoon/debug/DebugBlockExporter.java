package betamoon.debug;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import betamoon.BetaMoonMain;
import betamoon.io.IoUtils;
import net.minecraft.src.Block;
import net.minecraft.src.Item;
import net.minecraft.src.ItemStack;
import java.util.logging.Level;

/**
 * Exports block ids and names into the debug blocks file.
 */
final class DebugBlockExporter {
    private static final java.util.logging.Logger LOGGER = BetaMoonMain.LOGGER;

    private DebugBlockExporter() {
    }

    /**
     * Exports block ids and names into the debug blocks file.
     *
     * @return exception when export fails, otherwise null
     */
    static Exception exportBlocks() {
        File outputFile;
        try {
            outputFile = DebugExportPaths.resolveDebugFile("blocks.txt");
        } catch (IOException e) {
            return e;
        }
        BufferedWriter writer = null;
        try {
            writer = new BufferedWriter(new FileWriter(outputFile));
            // Traverse the block registry in id order for stable output.
            for (int i = 0; i < Block.blocksList.length; i++) {
                Block block = Block.blocksList[i];
                if (block == null) {
                    continue;
                }
                // Include both the unlocalized and localized names for readability.
                String rawInternal = block.getBlockName();
                String rawDisplay = DebugExportNames.resolveDisplayName(i, new ItemStack(i, 1, 0));
                // Fall back to class names when blocks do not expose internal identifiers.
                String internalName = rawInternal == null || rawInternal.length() == 0
                    ? DebugExportNames.safeClassName(block.getClass(), true)
                    : DebugExportNames.safeString(rawInternal);
                String displayName = DebugExportNames.isUnknownDisplayName(rawDisplay)
                    ? "Unknown Block (Probably only used internally)"
                    : DebugExportNames.safeString(rawDisplay);
                writer.write(i + " : " + internalName + " : \"" + displayName + "\"");
                writer.newLine();
                // Emit subtype entries when the corresponding item supports metadata.
                Item blockItem = Item.itemsList[i];
                if (blockItem != null && blockItem.getHasSubtypes()) {
                    DebugSubtypeExporter.writeSubItemEntries(writer, i, i);
                }
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Debug export failed: blocks", e);
            return e;
        } finally {
            IoUtils.closeQuietly(writer, "debug blocks");
        }
        return null;
    }
}
