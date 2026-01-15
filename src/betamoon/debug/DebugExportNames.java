package betamoon.debug;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.src.Block;
import net.minecraft.src.Item;
import net.minecraft.src.ItemStack;
import net.minecraft.src.StatCollector;

/**
 * Formatting and name resolution helpers for debug exports.
 */
final class DebugExportNames {
    private static final Map OBF_BLOCK_CLASS_NAMES = new LinkedHashMap();

    static {
        // Obfuscated -> unobfuscated class name mapping for unknown-name exports.
        OBF_BLOCK_CLASS_NAMES.put("h", "BlockPistonExtension");
        OBF_BLOCK_CLASS_NAMES.put("ut", "BlockPistonMoving");
    }

    private DebugExportNames() {
    }

    /**
     * Resolves the internal (unlocalized) name for item or block ids.
     */
    static String resolveInternalName(int id, ItemStack stack) {
        Item item = id >= 0 && id < Item.itemsList.length ? Item.itemsList[id] : null;
        if (item != null) {
            // Prefer per-stack unlocalized names when available (subtypes).
            String name = stack == null ? item.getItemName() : item.getItemNameIS(stack);
            if (name != null) {
                return escapeText(name);
            }
        }
        Block block = id >= 0 && id < Block.blocksList.length ? Block.blocksList[id] : null;
        if (block != null && block.getBlockName() != null) {
            return escapeText(block.getBlockName());
        }
        return "unknown";
    }

    /**
     * Resolves the localized display name for item or block ids.
     */
    static String resolveDisplayName(int id, ItemStack stack) {
        Item item = id >= 0 && id < Item.itemsList.length ? Item.itemsList[id] : null;
        if (item != null) {
            // Prefer stack-aware localization for subtypes (dyes, wool, etc.).
            String key = stack == null ? item.getItemName() : item.getItemNameIS(stack);
            if (key != null) {
                return escapeText(StatCollector.translateToLocal(key + ".name"));
            }
        }
        Block block = id >= 0 && id < Block.blocksList.length ? Block.blocksList[id] : null;
        if (block != null) {
            return escapeText(block.translateBlockName());
        }
        return "Unknown";
    }

    /**
     * Returns true when the localized display name is missing.
     */
    static boolean isUnknownDisplayName(String value) {
        return value == null || "null.name".equals(value) || "Unknown".equals(value)
            || value.endsWith(".name");
    }

    /**
     * Formats id strings with metadata when a non-zero damage value is present.
     */
    static String formatIdWithDamage(int id, int damage) {
        if (damage > 0) {
            return id + ":" + damage;
        }
        return String.valueOf(id);
    }

    /**
     * Returns a safe string for export.
     */
    static String safeString(String value) {
        return value == null ? "" : escapeText(value);
    }

    /**
     * Returns a readable class name for fallback internal names.
     */
    static String safeClassName(Class type, boolean isBlock) {
        if (type == null) {
            return "unknown";
        }
        String name = type.getSimpleName();
        if (name == null || name.length() == 0) {
            name = type.getName();
        }
        if (name == null || name.length() == 0) {
            return "unknown";
        }
        String mapped = mapObfuscatedClassName(name, isBlock);
        return escapeText(mapped + ".class");
    }

    private static String mapObfuscatedClassName(String name, boolean isBlock) {
        // Only blocks currently have known obfuscated mappings.
        String mapped = (String) (isBlock ? OBF_BLOCK_CLASS_NAMES.get(name) : null);
        return mapped == null ? name : mapped;
    }

    /**
     * Escapes quotes in exported strings.
     */
    static String escapeText(String value) {
        return value.replace("\"", "\\\"");
    }
}
