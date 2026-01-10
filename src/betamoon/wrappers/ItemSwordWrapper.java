package betamoon.wrappers;

import java.lang.reflect.Field;
import net.minecraft.src.EnumToolMaterial;
import net.minecraft.src.ItemSword;

public class ItemSwordWrapper extends ItemSword {
    private static final Field WEAPON_DAMAGE_FIELD = resolveWeaponDamageField();

    /**
     * Creates a sword wrapper with the provided item id, material, damage value, and internal name.
     *
     * @param id numeric item id (unshifted)
     * @param material tool material to use
     * @param damage custom damage value to apply
     * @param name internal item name (unlocalized)
     */
    public ItemSwordWrapper(int id, EnumToolMaterial material, int damage, String name) {
        super(id, material);
        setItemName(name);
        setIconCoord(0, 0);
        setFull3D();
        setDamageValue(damage);
    }

    /**
     * Sets the maximum damage value for the tool.
     *
     * @param maxDamage durability value
     * @return this wrapper for chaining
     */
    public ItemSwordWrapper setMaxDamageValue(int maxDamage) {
        this.setMaxDamage(maxDamage);
        return this;
    }

    /**
     * Enables or disables item subtypes.
     *
     * @param hasSubtypes true to enable subtypes
     * @return this wrapper for chaining
     */
    public ItemSwordWrapper setHasSubtypesValue(boolean hasSubtypes) {
        this.setHasSubtypes(hasSubtypes);
        return this;
    }

    /**
     * Overrides the weapon damage applied to entities.
     *
     * @param damage damage value
     * @return this wrapper for chaining
     */
    public ItemSwordWrapper setDamageValue(int damage) {
        if (WEAPON_DAMAGE_FIELD != null) {
            try {
                WEAPON_DAMAGE_FIELD.setInt(this, damage);
            } catch (IllegalAccessException ignored) {
            }
        }
        return this;
    }

    private static Field resolveWeaponDamageField() {
        try {
            Field field = ItemSword.class.getDeclaredField("weaponDamage");
            field.setAccessible(true);
            return field;
        } catch (Exception e) {
            return null;
        }
    }
}
