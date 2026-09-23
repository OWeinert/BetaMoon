package betamoon.debug;

import betamoon.luamodloader.LuaContentRegistry;
import java.io.BufferedWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.src.Item;
import net.minecraft.src.ItemStack;

/**
 * Exports item ids and names into the debug items file.
 */
final class DebugItemExporter {
    private DebugItemExporter() {
    }

    static void export(DebugExportSession session) throws Exception {
        final Map<Integer, LuaContentRegistry.ContentDescription> ownership = ownership("item");
        session.writeTextFile("items.txt", new DebugExportSession.TextContent() {
            @Override
            public int write(BufferedWriter writer) throws IOException {
                int records = 0;
                boolean inferredSubtypes = false;
                // Traverse the item registry in runtime ID order for stable output.
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
                    String rawDisplay = DebugExportNames.resolveDisplayName(item.shiftedIndex,
                            new ItemStack(item.shiftedIndex, 1, 0));
                    // Fall back to class names when items do not expose internal identifiers.
                    String internalName = rawInternal == null || rawInternal.length() == 0
                            ? DebugExportNames.safeClassName(item.getClass(), false)
                            : DebugExportNames.safeString(rawInternal);
                    String displayName = DebugExportNames.isUnknownDisplayName(rawDisplay)
                            ? "Unknown Item (Probably only used internally)"
                            : DebugExportNames.safeString(rawDisplay);
                    LuaContentRegistry.ContentDescription content = ownership.get(Integer.valueOf(item.shiftedIndex));
                    writer.write("item registry ID: " + item.shiftedIndex + " | legacy constructor ID: " + itemId
                            + " | canonical key: unavailable | internal name: " + internalName
                            + " | display name: \"" + displayName + "\" | owner: "
                            + (content == null ? "minecraft/foreign" : DebugExportNames.safeString(content.owner)));
                    writer.newLine();
                    records++;
                    // Emit subtype entries when metadata is enabled.
                    if (item.getHasSubtypes()) {
                        records += DebugSubtypeExporter.writeSubItemEntries(writer, item.shiftedIndex,
                                item.shiftedIndex);
                        inferredSubtypes = true;
                    }
                }
                if (inferredSubtypes) {
                    session.warn("Item subtype rows were inferred by probing damage 0 through 15 "
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
