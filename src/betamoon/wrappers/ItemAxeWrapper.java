package betamoon.wrappers;

import net.minecraft.src.EnumToolMaterial;
import net.minecraft.src.ItemAxe;

public class ItemAxeWrapper extends ItemAxe {

    /**
     * Creates an axe wrapper with the provided item id, material, and internal name.
     *
     * @param id numeric item id (unshifted)
     * @param material tool material to use
     * @param name internal item name (unlocalized)
     */
    public ItemAxeWrapper(int id, EnumToolMaterial material, String name) {
        super(id, material);
        setItemName(name);
        setIconCoord(0, 0);
        setFull3D();
    }

    /**
     * Sets the maximum damage value for the tool.
     *
     * @param maxDamage durability value
     * @return this wrapper for chaining
     */
    public ItemAxeWrapper setMaxDamageValue(int maxDamage) {
        this.setMaxDamage(maxDamage);
        return this;
    }

    /**
     * Enables or disables item subtypes.
     *
     * @param hasSubtypes true to enable subtypes
     * @return this wrapper for chaining
     */
    public ItemAxeWrapper setHasSubtypesValue(boolean hasSubtypes) {
        this.setHasSubtypes(hasSubtypes);
        return this;
    }

    /**
     * Sets the tool efficiency applied on effective blocks.
     *
     * @param efficiency efficiency value
     * @return this wrapper for chaining
     */
    public ItemAxeWrapper setEfficiencyValue(float efficiency) {
        this.efficiencyOnProperMaterial = efficiency;
        return this;
    }

    /**
     * Sets the damage applied when hitting entities.
     *
     * @param damage damage value
     * @return this wrapper for chaining
     */
    public ItemAxeWrapper setDamageValue(int damage) {
        this.damageVsEntity = damage;
        return this;
    }
}
