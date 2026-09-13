package betamoon.wrappers;

import betamoon.luaapi.item.ConfigurableAttackDamage;
import betamoon.luaapi.item.ConfigurableItemDurability;
import betamoon.luaapi.item.ConfigurableItemSubtypes;
import betamoon.luaapi.item.ItemBehavior;
import betamoon.luaapi.item.ItemCallback;
import betamoon.luaapi.item.ItemCallbackRegistry;
import betamoon.luaapi.utils.InteractionOutcome;
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

public class ItemSwordWrapper extends ItemSword
        implements
            IUseItemFirst,
            ConfigurableItemDurability,
            ConfigurableItemSubtypes,
            ConfigurableAttackDamage {
    private static final Field WEAPON_DAMAGE_FIELD = resolveWeaponDamageField();

    /**
     * Creates a sword wrapper with the provided item id, material, damage value,
     * and internal name.
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
     * @param maxDamage
     *            durability value
     * @return this wrapper for chaining
     */
    @Override
    public ItemSwordWrapper setMaxDamageValue(int maxDamage) {
        this.setMaxDamage(maxDamage);
        return this;
    }

    /**
     * Enables or disables item subtypes.
     *
     * @param hasSubtypes
     *            true to enable subtypes
     * @return this wrapper for chaining
     */
    @Override
    public ItemSwordWrapper setHasSubtypesValue(boolean hasSubtypes) {
        this.setHasSubtypes(hasSubtypes);
        return this;
    }

    /**
     * Overrides the weapon damage applied to entities.
     *
     * @param damage
     *            damage value
     * @return this wrapper for chaining
     */
    @Override
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

    @Override
    public int getIconFromDamage(int metadata) {
        return ItemBehavior.icon(shiftedIndex, metadata, () -> super.getIconFromDamage(metadata));
    }

    @Override
    public int getColorFromDamage(int metadata) {
        return ItemBehavior.color(shiftedIndex, metadata, () -> super.getColorFromDamage(metadata));
    }

    public boolean onItemUseFirst(ItemStack stack, EntityPlayer player, World world, int x, int y, int z, int side) {
        return ItemCallbackRegistry.interact(ItemCallback.USE_FIRST, stack, player, world, x, y, z, side,
                null) != InteractionOutcome.PASS;
    }

    public boolean onItemUse(ItemStack stack, EntityPlayer player, World world, int x, int y, int z, int side) {
        InteractionOutcome result = ItemCallbackRegistry.interact(ItemCallback.USE_ON_BLOCK, stack, player, world, x, y,
                z, side, null);
        return result != InteractionOutcome.PASS || super.onItemUse(stack, player, world, x, y, z, side);
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
        return ItemCallbackRegistry.canHarvest(shiftedIndex, block, 0, super.canHarvestBlock(block));
    }

    public float getStrVsBlock(ItemStack stack, Block block, int metadata) {
        return ItemCallbackRegistry.miningSpeed(stack, block, metadata, super.getStrVsBlock(stack, block, metadata));
    }
}
