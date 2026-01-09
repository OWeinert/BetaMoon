package betamoon.wrappers;

import net.minecraft.src.EnumToolMaterial;
import net.minecraft.src.ItemHoe;

public class ItemHoeWrapper extends ItemHoe {

    /**
     * Creates a hoe wrapper with the provided item id and material.
     *
     * @param id numeric item id (unshifted)
     * @param material tool material to use
     */
    public ItemHoeWrapper(int id, EnumToolMaterial material) {
        super(id, material);
    }

    /**
     * Sets the maximum damage value for the tool.
     *
     * @param maxDamage durability value
     * @return this wrapper for chaining
     */
    public ItemHoeWrapper setMaxDamageValue(int maxDamage) {
        this.setMaxDamage(maxDamage);
        return this;
    }

    /**
     * Enables or disables item subtypes.
     *
     * @param hasSubtypes true to enable subtypes
     * @return this wrapper for chaining
     */
    public ItemHoeWrapper setHasSubtypesValue(boolean hasSubtypes) {
        this.setHasSubtypes(hasSubtypes);
        return this;
    }
}
