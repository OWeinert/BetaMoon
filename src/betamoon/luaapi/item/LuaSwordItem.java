package betamoon.luaapi.item;

import forge.IUseItemFirst;
import java.lang.reflect.Field;
import net.minecraft.src.Block;
import net.minecraft.src.Entity;
import net.minecraft.src.EntityLiving;
import net.minecraft.src.EntityPlayer;
import net.minecraft.src.EnumToolMaterial;
import net.minecraft.src.ItemStack;
import net.minecraft.src.ItemSword;
import net.minecraft.src.World;

public class LuaSwordItem extends ItemSword
        implements
            IUseItemFirst,
            ConfigurableItemDurability,
            ConfigurableItemSubtypes,
            ConfigurableAttackDamage {
    private static final Field WEAPON_DAMAGE_FIELD = resolveWeaponDamageField();

    /**
     * Creates a sword implementation with the provided item id, material, damage
     * value, and internal name.
     *
     * @param id
     *            numeric item id (unshifted)
     * @param material
     *            tool material to use
     * @param damage
     *            custom damage value to apply
     * @param name
     *            internal item name (unlocalized)
     */
    public LuaSwordItem(int id, EnumToolMaterial material, int damage, String name) {
        super(id, material);
        setItemName(name);
        setIconCoord(0, 0);
        setFull3D();
        setDamageValue(damage);
    }

    /**
     * Sets the maximum damage value for the tool.
     *
     * @param maxDamage
     *            durability value
     * @return this instance for chaining
     */
    @Override
    public LuaSwordItem setMaxDamageValue(int maxDamage) {
        this.setMaxDamage(maxDamage);
        return this;
    }

    /**
     * Enables or disables item subtypes.
     *
     * @param hasSubtypes
     *            true to enable subtypes
     * @return this instance for chaining
     */
    @Override
    public LuaSwordItem setHasSubtypesValue(boolean hasSubtypes) {
        this.setHasSubtypes(hasSubtypes);
        return this;
    }

    /**
     * Overrides the weapon damage applied to entities.
     *
     * @param damage
     *            damage value
     * @return this instance for chaining
     */
    @Override
    public LuaSwordItem setDamageValue(int damage) {
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

    @Override
    public int getIconFromDamage(int metadata) {
        return ItemBehavior.icon(shiftedIndex, metadata, () -> super.getIconFromDamage(metadata));
    }

    @Override
    public int getColorFromDamage(int metadata) {
        return ItemBehavior.color(shiftedIndex, metadata, () -> super.getColorFromDamage(metadata));
    }

    public boolean onItemUseFirst(ItemStack stack, EntityPlayer player, World world, int x, int y, int z, int side) {
        return ItemBehavior.useFirst(stack, player, world, x, y, z, side);
    }

    public boolean onItemUse(ItemStack stack, EntityPlayer player, World world, int x, int y, int z, int side) {
        return ItemBehavior.useOnBlock(stack, player, world, x, y, z, side,
                () -> super.onItemUse(stack, player, world, x, y, z, side));
    }

    @Override
    public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        return ItemBehavior.use(stack, world, player, () -> super.onItemRightClick(stack, world, player));
    }

    @Override
    public void onUpdate(ItemStack stack, World world, Entity entity, int slot, boolean selected) {
        super.onUpdate(stack, world, entity, slot, selected);
        ItemBehavior.inventoryTick(stack, world, entity, slot, selected);
    }

    @Override
    public void onCreated(ItemStack stack, World world, EntityPlayer player) {
        super.onCreated(stack, world, player);
        ItemBehavior.crafted(stack, world, player);
    }

    @Override
    public boolean hitEntity(ItemStack stack, EntityLiving target, EntityLiving attacker) {
        return ItemBehavior.hit(shiftedIndex, stack, target, attacker, () -> super.hitEntity(stack, target, attacker));
    }

    @Override
    public boolean onBlockDestroyed(ItemStack stack, int id, int x, int y, int z, EntityLiving entity) {
        return ItemBehavior.destroyedBlock(shiftedIndex, stack, x, y, z, entity,
                () -> super.onBlockDestroyed(stack, id, x, y, z, entity));
    }

    public boolean canHarvestBlock(Block block) {
        return ItemBehavior.canHarvest(shiftedIndex, block, super.canHarvestBlock(block));
    }

    public float getStrVsBlock(ItemStack stack, Block block, int metadata) {
        return ItemBehavior.miningSpeed(stack, block, metadata, super.getStrVsBlock(stack, block, metadata));
    }
}
