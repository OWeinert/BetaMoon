package betamoon.query;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.src.Block;
import net.minecraft.src.Item;
import net.minecraft.src.ItemStack;
import net.minecraft.src.StatCollector;

public final class QueryEntries {
    private QueryEntries() {
    }

    public static List buildBlockEntries() {
        List entries = new ArrayList();
        for (int id = 0; id < Block.blocksList.length && id <= 255; id++) {
            Block block = Block.blocksList[id];
            if (block == null) {
                continue;
            }
            addEntriesForId(entries, id);
        }
        return entries;
    }

    public static List buildItemEntries() {
        List entries = new ArrayList();
        for (int id = 256; id < Item.itemsList.length; id++) {
            Item item = Item.itemsList[id];
            if (item == null) {
                continue;
            }
            addEntriesForId(entries, id);
        }
        return entries;
    }

    private static void addEntriesForId(List entries, int id) {
        String baseInternal = resolveInternalName(id, 0);
        String baseDisplay = resolveDisplayName(id, 0);
        entries.add(new QueryEntry(id, 0, baseInternal, baseDisplay));
        Item item = id >= 0 && id < Item.itemsList.length ? Item.itemsList[id] : null;
        if (item == null || !item.getHasSubtypes()) {
            return;
        }
        Set seen = new HashSet();
        if (baseInternal != null && baseDisplay != null) {
            seen.add(baseInternal + "|" + baseDisplay);
        }
        for (int damage = 1; damage <= 15; damage++) {
            try {
                String internal = resolveInternalName(id, damage);
                String display = resolveDisplayName(id, damage);
                if (internal == null || display == null) {
                    continue;
                }
                if ("unknown".equals(internal) || isUnknownDisplayName(display)) {
                    continue;
                }
                if (internal.equals(baseInternal) && display.equals(baseDisplay)) {
                    continue;
                }
                String signature = internal + "|" + display;
                if (seen.contains(signature)) {
                    continue;
                }
                seen.add(signature);
                entries.add(new QueryEntry(id, damage, internal, display));
            } catch (RuntimeException e) {
                if (e instanceof ArrayIndexOutOfBoundsException) {
                    break;
                }
            }
        }
    }

    private static String resolveInternalName(int id, int damage) {
        Item item = id >= 0 && id < Item.itemsList.length ? Item.itemsList[id] : null;
        ItemStack stack = null;
        if (item != null) {
            stack = new ItemStack(id, 1, damage);
            String name = stack == null ? item.getItemName() : item.getItemNameIS(stack);
            if (name != null) {
                return name;
            }
        }
        Block block = id >= 0 && id < Block.blocksList.length ? Block.blocksList[id] : null;
        if (block != null && block.getBlockName() != null) {
            return block.getBlockName();
        }
        return "unknown";
    }

    private static String resolveDisplayName(int id, int damage) {
        Item item = id >= 0 && id < Item.itemsList.length ? Item.itemsList[id] : null;
        ItemStack stack = null;
        if (item != null) {
            stack = new ItemStack(id, 1, damage);
            String key = stack == null ? item.getItemName() : item.getItemNameIS(stack);
            if (key != null) {
                return StatCollector.translateToLocal(key + ".name");
            }
        }
        Block block = id >= 0 && id < Block.blocksList.length ? Block.blocksList[id] : null;
        if (block != null) {
            return block.translateBlockName();
        }
        return "Unknown";
    }

    private static boolean isUnknownDisplayName(String value) {
        return value == null || "null.name".equals(value) || "Unknown".equals(value)
            || value.endsWith(".name");
    }

    public static QueryEntry findById(List entries, int id, int damage) {
        for (int i = 0; i < entries.size(); i++) {
            QueryEntry entry = (QueryEntry) entries.get(i);
            if (entry.id == id && entry.damage == damage) {
                return entry;
            }
        }
        return null;
    }

    public static QueryEntry findFirstById(List entries, int id) {
        for (int i = 0; i < entries.size(); i++) {
            QueryEntry entry = (QueryEntry) entries.get(i);
            if (entry.id == id) {
                return entry;
            }
        }
        return null;
    }

    public static <T> List<T> filterEntries(List<T> entries, Predicate<T> predicate) {
        List<T> filtered = new ArrayList<T>();
        if (entries == null || predicate == null) {
            return filtered;
        }
        for (int i = 0; i < entries.size(); i++) {
            T entry = entries.get(i);
            if (predicate.test(entry)) {
                filtered.add(entry);
            }
        }
        return filtered;
    }

    public static QueryEntry findFirst(List entries, Predicate predicate) {
        if (entries == null || predicate == null) {
            return null;
        }
        for (int i = 0; i < entries.size(); i++) {
            QueryEntry entry = (QueryEntry) entries.get(i);
            if (predicate.test(entry)) {
                return entry;
            }
        }
        return null;
    }
}
