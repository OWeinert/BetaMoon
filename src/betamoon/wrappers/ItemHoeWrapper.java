package betamoon.wrappers;

import net.minecraft.src.EnumToolMaterial;
import net.minecraft.src.ItemHoe;

public class ItemHoeWrapper extends ItemHoe {

    /**
     * Creates a hoe wrapper with the provided item id, material, and internal name.
     *
     * @param id numeric item id (unshifted)
     * @param material tool material to use
     * @param name internal item name (unlocalized)
     */
    public ItemHoeWrapper(int id, EnumToolMaterial material, String name) {
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
