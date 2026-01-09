package betamoon.wrappers;

import net.minecraft.src.ItemFood;

public class ItemFoodWrapper extends ItemFood {

    /**
     * Creates a food item wrapper with the provided item id and food values.
     *
     * @param id numeric item id (unshifted)
     * @param healAmount hunger restored when eaten
     * @param isWolfFood true if wolves can eat this item
     */
    public ItemFoodWrapper(int id, int healAmount, boolean isWolfFood) {
        super(id, healAmount, isWolfFood);
    }

    /**
     * Sets the maximum damage value for the item.
     *
     * @param maxDamage durability value
     * @return this wrapper for chaining
     */
    public ItemFoodWrapper setMaxDamageValue(int maxDamage) {
        this.setMaxDamage(maxDamage);
        return this;
    }

    /**
     * Enables or disables item subtypes.
     *
     * @param hasSubtypes true to enable subtypes
     * @return this wrapper for chaining
     */
    public ItemFoodWrapper setHasSubtypesValue(boolean hasSubtypes) {
        this.setHasSubtypes(hasSubtypes);
        return this;
    }

    /**
     * Copies configuration fields from a non-food item wrapper.
     *
     * @param source item wrapper to copy from
     * @return this wrapper for chaining
     */
    public ItemFoodWrapper applyFrom(ItemWrapper source) {
        int iconIndex = source.getIconFromDamage(0);
        this.setIconIndex(iconIndex);
        String itemName = source.getItemName();
        if (itemName != null) {
            String baseName = itemName.startsWith("item.") ? itemName.substring(5) : itemName;
            this.setItemName(baseName);
        }
        this.setMaxStackSize(source.getItemStackLimit());
        this.setMaxDamageValue(source.getMaxDamage());
        this.setHasSubtypesValue(source.getHasSubtypes());
        if (source.isFull3D()) {
            this.setFull3D();
        }
        return this;
    }
}
