package betamoon.wrappers;

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
import net.minecraft.src.ItemFood;
import net.minecraft.src.ItemStack;
import net.minecraft.src.World;

public class ItemFoodWrapper extends ItemFood
        implements
            IUseItemFirst,
            ConfigurableItemDurability,
            ConfigurableItemSubtypes {

    /**
     * Creates a food item wrapper with the provided item id and food values.
     *
     * @param id
     *            numeric item id (unshifted)
     * @param healAmount
     *            hunger restored when eaten
     * @param isWolfFood
     *            true if wolves can eat this item
     */
    public ItemFoodWrapper(int id, int healAmount, boolean isWolfFood) {
        super(id, healAmount, isWolfFood);
    }

    /**
     * Sets the maximum damage value for the item.
     *
     * @param maxDamage
     *            durability value
     * @return this wrapper for chaining
     */
    @Override
    public ItemFoodWrapper setMaxDamageValue(int maxDamage) {
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
    public ItemFoodWrapper setHasSubtypesValue(boolean hasSubtypes) {
        this.setHasSubtypes(hasSubtypes);
        return this;
    }

    /**
     * Copies configuration fields from a non-food item wrapper.
     *
     * @param source
     *            item wrapper to copy from
     * @return this wrapper for chaining
     */
    public ItemFoodWrapper applyFrom(ItemWrapper source) {
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
    public ItemFoodWrapper setFoodValues(int healAmount, boolean wolfFood) {
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
