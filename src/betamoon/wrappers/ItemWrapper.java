package betamoon.wrappers;

import net.minecraft.src.Item;

public class ItemWrapper extends Item {

    /**
     * Creates an item wrapper for the provided item id and internal name.
     *
     * @param id numeric item id (unshifted)
     * @param name internal item name (unlocalized)
     */
    public ItemWrapper(int id, String name) {
        super(id);
        this.setItemName(name);
    }

    /**
     * Sets the maximum damage value for the item.
     *
     * @param maxDamage durability value
     * @return this wrapper for chaining
     */
    public ItemWrapper setMaxDamageValue(int maxDamage) {
        this.setMaxDamage(maxDamage);
        return this;
    }

    /**
     * Enables or disables item subtypes.
     *
     * @param hasSubtypes true to enable subtypes
     * @return this wrapper for chaining
     */
    public ItemWrapper setHasSubtypesValue(boolean hasSubtypes) {
        this.setHasSubtypes(hasSubtypes);
        return this;
    }
}
