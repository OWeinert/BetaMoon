package betamoon.wrappers;

import net.minecraft.src.EnumToolMaterial;
import net.minecraft.src.ItemSpade;

public class ItemSpadeWrapper extends ItemSpade {

    /**
     * Creates a shovel wrapper with the provided item id and material.
     *
     * @param id numeric item id (unshifted)
     * @param material tool material to use
     */
    public ItemSpadeWrapper(int id, EnumToolMaterial material) {
        super(id, material);
    }

    /**
     * Sets the maximum damage value for the tool.
     *
     * @param maxDamage durability value
     * @return this wrapper for chaining
     */
    public ItemSpadeWrapper setMaxDamageValue(int maxDamage) {
        this.setMaxDamage(maxDamage);
        return this;
    }

    /**
     * Enables or disables item subtypes.
     *
     * @param hasSubtypes true to enable subtypes
     * @return this wrapper for chaining
     */
    public ItemSpadeWrapper setHasSubtypesValue(boolean hasSubtypes) {
        this.setHasSubtypes(hasSubtypes);
        return this;
    }

    /**
     * Sets the tool efficiency applied on effective blocks.
     *
     * @param efficiency efficiency value
     * @return this wrapper for chaining
     */
    public ItemSpadeWrapper setEfficiencyValue(float efficiency) {
        this.efficiencyOnProperMaterial = efficiency;
        return this;
    }

    /**
     * Sets the damage applied when hitting entities.
     *
     * @param damage damage value
     * @return this wrapper for chaining
     */
    public ItemSpadeWrapper setDamageValue(int damage) {
        this.damageVsEntity = damage;
        return this;
    }
}
