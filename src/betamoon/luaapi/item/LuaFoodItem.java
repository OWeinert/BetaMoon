package betamoon.luaapi.item;

import forge.IUseItemFirst;
import java.lang.reflect.Field;
import net.minecraft.src.Block;
import net.minecraft.src.Entity;
import net.minecraft.src.EntityLiving;
import net.minecraft.src.EntityPlayer;
import net.minecraft.src.ItemFood;
import net.minecraft.src.ItemStack;
import net.minecraft.src.World;

public class LuaFoodItem extends ItemFood
        implements
            IUseItemFirst,
            ConfigurableItemDurability,
            ConfigurableItemSubtypes {

    /**
     * Creates a food item implementation with the provided item id and food values.
     *
     * @param id
     *            numeric item id (unshifted)
     * @param healAmount
     *            hunger restored when eaten
     * @param isWolfFood
     *            true if wolves can eat this item
     */
    public LuaFoodItem(int id, int healAmount, boolean isWolfFood) {
        super(id, healAmount, isWolfFood);
    }

    /**
     * Sets the maximum damage value for the item.
     *
     * @param maxDamage
     *            durability value
     * @return this instance for chaining
     */
    @Override
    public LuaFoodItem setMaxDamageValue(int maxDamage) {
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
    public LuaFoodItem setHasSubtypesValue(boolean hasSubtypes) {
        this.setHasSubtypes(hasSubtypes);
        return this;
    }

    /**
     * Copies configuration fields from a non-food item implementation.
     *
     * @param source
     *            item implementation to copy from
     * @return this instance for chaining
     */
    public LuaFoodItem applyFrom(LuaItem source) {
        int iconIndex = source.getIconFromDamage(0);
        this.setIconIndex(iconIndex);
        String itemName = source.getItemName();
        if (itemName != null) {
            String baseName = itemName.startsWith("item.") ? itemName.substring(5) : itemName;
            this.setItemName(baseName);
        }
        this.setMaxStackSize(source.getItemStackLimit());
        this.setMaxDamageValue(source.getMaxDamage());
        this.setHasSubtypesValue(source.getHasSubtypes());
        if (source.isFull3D()) {
            this.setFull3D();
        }
        return this;
    }

    /** Updates food definition without replacing the registered item identity. */
    public LuaFoodItem setFoodValues(int healAmount, boolean wolfFood) {
        setField("healAmount", "a", Integer.valueOf(healAmount));
        setField("isWolfsFavoriteMeat", "bk", Boolean.valueOf(wolfFood));
        return this;
    }

    private void setField(String mappedName, String obfuscatedName, Object value) {
        try {
            Field field;
            try {
                field = ItemFood.class.getDeclaredField(mappedName);
            } catch (NoSuchFieldException ignored) {
                field = ItemFood.class.getDeclaredField(obfuscatedName);
            }
            field.setAccessible(true);
            field.set(this, value);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to update food values", e);
        }
    }

    @Override
    public String getItemNameIS(ItemStack stack) {
        return ItemBehavior.name(shiftedIndex, stack.getItemDamage(), super.getItemNameIS(stack));
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
