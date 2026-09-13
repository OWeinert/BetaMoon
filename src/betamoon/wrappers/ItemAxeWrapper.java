package betamoon.wrappers;

import betamoon.luaapi.item.ConfigurableAttackDamage;
import betamoon.luaapi.item.ConfigurableItemDurability;
import betamoon.luaapi.item.ConfigurableItemSubtypes;
import betamoon.luaapi.item.ConfigurableMiningEfficiency;
import betamoon.luaapi.item.ItemBehavior;
import betamoon.luaapi.item.ItemCallback;
import betamoon.luaapi.item.ItemCallbackRegistry;
import betamoon.luaapi.utils.InteractionOutcome;
import forge.IUseItemFirst;
import net.minecraft.src.Block;
import net.minecraft.src.Entity;
import net.minecraft.src.EntityLiving;
import net.minecraft.src.EntityPlayer;
import net.minecraft.src.EnumToolMaterial;
import net.minecraft.src.ItemAxe;
import net.minecraft.src.ItemStack;
import net.minecraft.src.World;

public class ItemAxeWrapper extends ItemAxe
        implements
            IUseItemFirst,
            ConfigurableItemDurability,
            ConfigurableItemSubtypes,
            ConfigurableMiningEfficiency,
            ConfigurableAttackDamage {

    /**
     * Creates an axe wrapper with the provided item id, material, and internal
     * name.
     *
     * @param id
     *            numeric item id (unshifted)
     * @param material
     *            tool material to use
     * @param name
     *            internal item name (unlocalized)
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
     * @param maxDamage
     *            durability value
     * @return this wrapper for chaining
     */
    @Override
    public ItemAxeWrapper setMaxDamageValue(int maxDamage) {
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
    public ItemAxeWrapper setHasSubtypesValue(boolean hasSubtypes) {
        this.setHasSubtypes(hasSubtypes);
        return this;
    }

    /**
     * Sets the tool efficiency applied on effective blocks.
     *
     * @param efficiency
     *            efficiency value
     * @return this wrapper for chaining
     */
    @Override
    public ItemAxeWrapper setEfficiencyValue(float efficiency) {
        this.efficiencyOnProperMaterial = efficiency;
        return this;
    }

    /**
     * Sets the damage applied when hitting entities.
     *
     * @param damage
     *            damage value
     * @return this wrapper for chaining
     */
    @Override
    public ItemAxeWrapper setDamageValue(int damage) {
        this.damageVsEntity = damage;
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
