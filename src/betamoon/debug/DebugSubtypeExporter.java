package betamoon.debug;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.HashSet;
import net.minecraft.src.ItemStack;

/**
 * Writes subtype entries for items/blocks that rely on metadata.
 */
final class DebugSubtypeExporter {
    private DebugSubtypeExporter() {
    }

    /**
     * Writes subtype entries for items/blocks that rely on metadata.
     */
    static void writeSubItemEntries(BufferedWriter writer, int exportId, int stackId) throws IOException {
        String baseInternal = null;
        String baseDisplay = null;
        java.util.Set seen = new HashSet();
        try {
            ItemStack baseStack = new ItemStack(stackId, 1, 0);
            baseInternal = DebugExportNames.resolveInternalName(stackId, baseStack);
            baseDisplay = DebugExportNames.resolveDisplayName(stackId, baseStack);
            if (baseInternal != null && baseDisplay != null) {
                seen.add(baseInternal + "|" + baseDisplay);
            }
        } catch (RuntimeException e) {
            baseInternal = null;
            baseDisplay = null;
        }
        // Probe common metadata range used by vanilla (e.g., dyes and wool).
        for (int damage = 0; damage <= 15; damage++) {
            if (damage == 0) {
                continue;
            }
            try {
                ItemStack stack = new ItemStack(stackId, 1, damage);
                String rawInternal = DebugExportNames.resolveInternalName(stackId, stack);
                String rawDisplay = DebugExportNames.resolveDisplayName(stackId, stack);
                // Skip subtypes that do not resolve to meaningful names.
                if ("unknown".equals(rawInternal) || DebugExportNames.isUnknownDisplayName(rawDisplay)) {
                    continue;
                }
                // Skip variants that are identical to the base entry.
                if (rawInternal.equals(baseInternal) && rawDisplay.equals(baseDisplay)) {
                    continue;
                }
                // Skip duplicate subtype names so only distinct variants are emitted once.
                String signature = rawInternal + "|" + rawDisplay;
                if (seen.contains(signature)) {
                    continue;
                }
                seen.add(signature);
                String idText = DebugExportNames.formatIdWithDamage(exportId, damage);
                writer.write("    " + idText + " : " + rawInternal + " : \"" + rawDisplay + "\"");
                writer.newLine();
            } catch (RuntimeException e) {
                // Some items only define a subset of subtypes; skip invalid metadata.
                if (e instanceof ArrayIndexOutOfBoundsException) {
                    break;
                }
            }
        }
    }
}
