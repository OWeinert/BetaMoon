package betamoon.wrappers;

import net.minecraft.src.EnumToolMaterial;
import net.minecraft.src.ItemPickaxe;

public class ItemPickaxeWrapper extends ItemPickaxe {

    /**
     * Creates a pickaxe wrapper with the provided item id and material.
     *
     * @param id numeric item id (unshifted)
     * @param material tool material to use
     */
    public ItemPickaxeWrapper(int id, EnumToolMaterial material) {
        super(id, material);
    }

    /**
     * Sets the maximum damage value for the tool.
     *
     * @param maxDamage durability value
     * @return this wrapper for chaining
     */
    public ItemPickaxeWrapper setMaxDamageValue(int maxDamage) {
        this.setMaxDamage(maxDamage);
        return this;
    }

    /**
     * Enables or disables item subtypes.
     *
     * @param hasSubtypes true to enable subtypes
     * @return this wrapper for chaining
     */
    public ItemPickaxeWrapper setHasSubtypesValue(boolean hasSubtypes) {
        this.setHasSubtypes(hasSubtypes);
        return this;
    }

    /**
     * Sets the tool efficiency applied on effective blocks.
     *
     * @param efficiency efficiency value
     * @return this wrapper for chaining
     */
    public ItemPickaxeWrapper setEfficiencyValue(float efficiency) {
        this.efficiencyOnProperMaterial = efficiency;
        return this;
    }

    /**
     * Sets the damage applied when hitting entities.
     *
     * @param damage damage value
     * @return this wrapper for chaining
     */
    public ItemPickaxeWrapper setDamageValue(int damage) {
        this.damageVsEntity = damage;
        return this;
    }
}
