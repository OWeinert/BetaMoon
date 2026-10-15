package betamoon.luaapi.item;

import forge.IUseItemFirst;
import net.minecraft.src.Block;
import net.minecraft.src.Entity;
import net.minecraft.src.EntityLiving;
import net.minecraft.src.EntityPlayer;
import net.minecraft.src.Item;
import net.minecraft.src.ItemStack;
import net.minecraft.src.World;

public class LuaItem extends Item implements IUseItemFirst, ConfigurableItemDurability, ConfigurableItemSubtypes {

    /**
     * Creates an item implementation for the provided item id and internal name.
     *
     * @param id
     *            numeric item id (unshifted)
     * @param name
     *            internal item name (unlocalized)
     */
    public LuaItem(int id, String name) {
        super(id);
        this.setItemName(name);
    }

    /**
     * Sets the maximum damage value for the item.
     *
     * @param maxDamage
     *            durability value
     * @return this instance for chaining
     */
    @Override
    public LuaItem setMaxDamageValue(int maxDamage) {
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
    public LuaItem setHasSubtypesValue(boolean hasSubtypes) {
        this.setHasSubtypes(hasSubtypes);
        return this;
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
