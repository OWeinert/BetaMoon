package betamoon.gui.control;

import java.util.Arrays;
import java.util.Map;
import net.minecraft.src.Item;
import net.minecraft.src.ItemStack;

/**
 * One detached cosmetic descriptor or semantic HUD slot; never an inventory
 * slot.
 */
public final class GuiItemModel {
    public final int id;
    public final int damage;
    public final int count;
    public final int hotbarSlot;
    public final boolean showCount;
    public final boolean showDurability;
    public final boolean pickupAnimation;

    public GuiItemModel(Map<String, Object> values, boolean hudAvailable) {
        Map<?, ?> descriptor = record(values.get("item"), "item");
        Map<?, ?> source = record(values.get("source"), "source");
        if (descriptor != null && source != null) {
            throw new IllegalArgumentException("Item view chooses item or source, not both");
        }
        if (source != null && !source.containsKey("hotbarSlot")
                || descriptor != null && !descriptor.containsKey("id")) {
            throw new IllegalArgumentException("Item descriptor requires id; semantic source requires hotbarSlot");
        }
        hotbarSlot = source == null ? 0 : integer(source, "hotbarSlot", 0, 1, 9);
        if (source != null && (!hudAvailable || source.size() != 1)) {
            throw new IllegalArgumentException("Semantic hotbar source requires HUD observations and only hotbarSlot");
        }
        id = descriptor == null ? 0 : integer(descriptor, "id", 0, 1, Item.itemsList.length - 1);
        damage = descriptor == null ? 0 : integer(descriptor, "damage", 0, 0, 32767);
        count = descriptor == null ? 0 : integer(descriptor, "count", 1, 1, 32767);
        if (descriptor != null) {
            if (Item.itemsList[id] == null) {
                throw new IllegalArgumentException("Item view references an unregistered native item ID");
            }
            for (Object key : descriptor.keySet()) {
                if (!Arrays.asList("id", "damage", "count").contains(key)) {
                    throw new IllegalArgumentException("Unknown item descriptor field: " + key);
                }
            }
        }
        showCount = !Boolean.FALSE.equals(values.get("showCount"));
        showDurability = !Boolean.FALSE.equals(values.get("showDurability"));
        pickupAnimation = !Boolean.FALSE.equals(values.get("pickupAnimation"));
    }

    private static Map<?, ?> record(Object value, String path) {
        if (value == null) {
            return null;
        }
        if (!(value instanceof Map)) {
            throw new IllegalArgumentException(path + " requires a detached record");
        }
        return (Map<?, ?>) value;
    }

    private static int integer(Map<?, ?> values, String name, int fallback, int minimum, int maximum) {
        Object value = values.get(name);
        if (value == null) {
            if (fallback < minimum || fallback > maximum) {
                throw new IllegalArgumentException("Item requires " + name);
            }
            return fallback;
        }
        double number = value instanceof Number ? ((Number) value).doubleValue() : Double.NaN;
        if (!Double.isFinite(number) || number != Math.rint(number) || number < minimum || number > maximum) {
            throw new IllegalArgumentException(
                    "Item " + name + " requires an integer within " + minimum + ".." + maximum);
        }
        return (int) number;
    }

    public ItemStack stack() {
        return id == 0 ? null : new ItemStack(id, count, damage);
    }

    public boolean sameAppearance(GuiItemModel previous) {
        return previous != null && id == previous.id && damage == previous.damage && count == previous.count
                && hotbarSlot == previous.hotbarSlot && showCount == previous.showCount
                && showDurability == previous.showDurability;
    }
}
