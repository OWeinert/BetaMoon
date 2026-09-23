package betamoon.debug;

import betamoon.luamodloader.LuaContentRegistry;
import java.io.BufferedWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.src.Block;
import net.minecraft.src.Item;
import net.minecraft.src.ItemStack;

/**
 * Exports block ids and names into the debug blocks file.
 */
final class DebugBlockExporter {
    private DebugBlockExporter() {
    }

    static void export(DebugExportSession session) throws Exception {
        final Map<Integer, LuaContentRegistry.ContentDescription> ownership = ownership("block");
        session.writeTextFile("blocks.txt", new DebugExportSession.TextContent() {
            @Override
            public int write(BufferedWriter writer) throws IOException {
                int records = 0;
                boolean inferredSubtypes = false;
                // Traverse the block registry in ID order for stable output.
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
                    LuaContentRegistry.ContentDescription content = ownership.get(Integer.valueOf(i));
                    writer.write("block ID: " + i + " | canonical key: unavailable | internal name: " + internalName
                            + " | display name: \"" + displayName + "\" | owner: "
                            + (content == null ? "minecraft/foreign" : DebugExportNames.safeString(content.owner)));
                    writer.newLine();
                    records++;
                    // Emit subtype entries when the corresponding item supports metadata.
                    Item blockItem = Item.itemsList[i];
                    if (blockItem != null && blockItem.getHasSubtypes()) {
                        records += DebugSubtypeExporter.writeSubItemEntries(writer, i, i);
                        inferredSubtypes = true;
                    }
                }
                if (inferredSubtypes) {
                    session.warn("Block subtype rows were inferred by probing metadata 0 through 15 "
                            + "and may be incomplete.");
                }
                return records;
            }
        });
    }

    private static Map<Integer, LuaContentRegistry.ContentDescription> ownership(String namespace) {
        Map<Integer, LuaContentRegistry.ContentDescription> result =
                new HashMap<Integer, LuaContentRegistry.ContentDescription>();
        List<LuaContentRegistry.ContentDescription> content = LuaContentRegistry.snapshot();
        for (LuaContentRegistry.ContentDescription entry : content) {
            if (namespace.equals(entry.namespace)) {
                result.put(Integer.valueOf(entry.id), entry);
            }
        }
        return result;
    }
}
