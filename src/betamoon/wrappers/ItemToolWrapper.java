package betamoon.wrappers;

import net.minecraft.src.Block;
import net.minecraft.src.EnumToolMaterial;
import net.minecraft.src.ItemTool;

public class ItemToolWrapper extends ItemTool {

    /**
     * Creates a tool wrapper with the provided id, base damage, material, and effective blocks.
     *
     * @param id numeric item id (unshifted)
     * @param baseDamage base damage added to material damage
     * @param material tool material to use
     * @param effectiveBlocks blocks the tool is effective against
     */
    public ItemToolWrapper(int id, int baseDamage, EnumToolMaterial material, Block[] effectiveBlocks) {
        super(id, baseDamage, material, effectiveBlocks);
    }

    /**
     * Sets the maximum damage value for the tool.
     *
     * @param maxDamage durability value
     * @return this wrapper for chaining
     */
    public ItemToolWrapper setMaxDamageValue(int maxDamage) {
        this.setMaxDamage(maxDamage);
        return this;
    }

    /**
     * Enables or disables item subtypes.
     *
     * @param hasSubtypes true to enable subtypes
     * @return this wrapper for chaining
     */
    public ItemToolWrapper setHasSubtypesValue(boolean hasSubtypes) {
        this.setHasSubtypes(hasSubtypes);
        return this;
    }

    /**
     * Sets the tool efficiency applied on effective blocks.
     *
     * @param efficiency efficiency value
     * @return this wrapper for chaining
     */
    public ItemToolWrapper setEfficiencyValue(float efficiency) {
        this.efficiencyOnProperMaterial = efficiency;
        return this;
    }

    /**
     * Sets the damage applied when hitting entities.
     *
     * @param damage damage value
     * @return this wrapper for chaining
     */
    public ItemToolWrapper setDamageValue(int damage) {
        this.damageVsEntity = damage;
        return this;
    }
}
