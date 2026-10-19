package betamoon.luaapi.block;

import net.minecraft.src.Block;
import net.minecraft.src.EntityPlayer;
import net.minecraft.src.ItemBlock;
import net.minecraft.src.ItemStack;
import net.minecraft.src.World;

/**
 * Native block placement with metadata-aware names and explicitly declared
 * inventory subtypes.
 */
public final class LuaBlockItem extends ItemBlock {
    public LuaBlockItem(int id) {
        super(id);
        setMaxDamage(0);
    }

    @Override
    public boolean getHasSubtypes() {
        BlockDefinition definition = BlockCallbackRegistry.get(shiftedIndex);
        return definition != null && definition.subtypes.enabled;
    }

    @Override
    public int getPlacedBlockMetadata(int metadata) {
        BlockDefinition definition = BlockCallbackRegistry.get(shiftedIndex);
        return definition == null ? 0 : definition.subtypes.placedMetadata(metadata);
    }

    @Override
    public String getItemNameIS(ItemStack stack) {
        BlockDefinition definition = BlockCallbackRegistry.get(shiftedIndex);
        int metadata = stack.getItemDamage();
        if (definition != null && definition.subtypes.enabled) {
            metadata = definition.subtypes.variant(metadata);
        }
        String base = super.getItemNameIS(stack);
        return definition == null ? base : definition.names.key(base, metadata);
    }

    @Override
    public int getIconFromDamage(int metadata) {
        return Block.blocksList[shiftedIndex].getBlockTextureFromSideAndMetadata(2, metadata);
    }

    @Override
    public int getColorFromDamage(int metadata) {
        return Block.blocksList[shiftedIndex].getRenderColor(metadata);
    }

    @Override
    public boolean onItemUse(ItemStack stack, EntityPlayer player, World world, int x, int y, int z, int side) {
        BlockDefinition definition = BlockCallbackRegistry.get(shiftedIndex);
        if (definition != null && definition.subtypes.enabled
                && !definition.subtypes.validItemMetadata(stack.getItemDamage())) {
            return false;
        }
        return super.onItemUse(stack, player, world, x, y, z, side);
    }
}
