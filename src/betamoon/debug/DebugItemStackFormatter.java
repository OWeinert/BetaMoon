package betamoon.debug;

import net.minecraft.src.ItemStack;

/**
 * Formats item stacks consistently across recipe debug exports.
 */
final class DebugItemStackFormatter {
    private DebugItemStackFormatter() {
    }

    static String format(ItemStack stack) {
        if (stack == null) {
            return "[item = [unknown / 0 / \"Unknown\"], amount = 0]";
        }
        return format(stack, stack.stackSize, false);
    }

    static String format(ItemStack stack, int amount, boolean anyDamage) {
        if (stack == null) {
            return "[item = [unknown / 0 / \"Unknown\"], amount = 0]";
        }
        int id = stack.itemID;
        String internalName = DebugExportNames.safeString(DebugExportNames.resolveInternalName(id, stack));
        String displayName = DebugExportNames.safeString(DebugExportNames.resolveDisplayName(id, stack));
        String idText = anyDamage ? id + ":*" : DebugExportNames.formatIdWithDamage(id, stack.getItemDamage());
        return "[item = [" + internalName + " / " + idText + " / \"" + displayName + "\"], amount = " + amount + "]";
    }

    static String identifier(ItemStack stack) {
        if (stack == null) {
            return "unknown";
        }
        String name = DebugExportNames.safeString(DebugExportNames.resolveInternalName(stack.itemID, stack));
        if ("unknown".equals(name)) {
            name = String.valueOf(stack.itemID);
        }
        if (stack.getItemDamage() > 0) {
            return name + ":" + stack.getItemDamage();
        }
        return name;
    }
}
