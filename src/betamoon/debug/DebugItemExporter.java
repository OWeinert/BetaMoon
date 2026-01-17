package betamoon.debug;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.logging.Level;
import betamoon.BetaMoonMain;
import net.minecraft.src.Item;
import net.minecraft.src.ItemStack;

/**
 * Exports item ids and names into the debug items file.
 */
final class DebugItemExporter {
    private static final java.util.logging.Logger LOGGER = BetaMoonMain.LOGGER;

    private DebugItemExporter() {
    }

    /**
     * Exports item ids and names into the debug items file.
     *
     * @return exception when export fails, otherwise null
     */
    static Exception exportItems() {
        File outputFile;
        try {
            outputFile = DebugExportPaths.resolveDebugFile("items.txt");
        } catch (IOException e) {
            return e;
        }
        BufferedWriter writer = null;
        try {
            writer = new BufferedWriter(new FileWriter(outputFile));
            // Traverse the item registry in id order and normalize to unshifted ids.
            for (int i = 0; i < Item.itemsList.length; i++) {
                Item item = Item.itemsList[i];
                if (item == null) {
                    continue;
                }
                int itemId = item.shiftedIndex - 256;
                if (itemId < 0) {
                    continue;
                }
                // Use the base unlocalized and localized names for consistent output.
                String rawInternal = item.getItemName();
                String rawDisplay = DebugExportNames.resolveDisplayName(item.shiftedIndex, new ItemStack(item.shiftedIndex, 1, 0));
                // Fall back to class names when items do not expose internal identifiers.
                String internalName = rawInternal == null || rawInternal.length() == 0
                    ? DebugExportNames.safeClassName(item.getClass(), false)
                    : DebugExportNames.safeString(rawInternal);
                String displayName = DebugExportNames.isUnknownDisplayName(rawDisplay)
                    ? "Unknown Item (Probably only used internally)"
                    : DebugExportNames.safeString(rawDisplay);
                writer.write(itemId + " : " + internalName + " : \"" + displayName + "\"");
                writer.newLine();
                // Emit subtype entries when metadata is enabled.
                if (item.getHasSubtypes()) {
                    DebugSubtypeExporter.writeSubItemEntries(writer, itemId, item.shiftedIndex);
                }
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Debug export failed: items", e);
            return e;
        } finally {
            closeQuietly(writer);
        }
        return null;
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
