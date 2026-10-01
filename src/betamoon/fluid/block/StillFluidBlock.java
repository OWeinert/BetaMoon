package betamoon.fluid.block;

import betamoon.fluid.FluidAppearance;
import betamoon.fluid.interaction.FluidInteractionRegistry;
import net.minecraft.src.Material;
import net.minecraft.src.World;

/** Stable member of a custom fluid pair; relevant neighbor changes wake it. */
public final class StillFluidBlock extends FluidBlock {
    public StillFluidBlock(int id, Material material, FluidAppearance appearance) {
        super(id, material, appearance, false);
    }

    @Override
    public void onBlockAdded(World world, int x, int y, int z) {
        FluidInteractionRegistry.instance().evaluateAround(world, x, y, z);
    }

    @Override
    public void onNeighborBlockChange(World world, int x, int y, int z, int neighborId) {
        if (world.getBlockId(x, y, z) != blockID) {
            return;
        }
        if (FluidInteractionRegistry.instance().evaluateAround(world, x, y, z)) {
            return;
        }
        int metadata = world.getBlockMetadata(x, y, z);
        int flowingId = definition().flowingBlockId();
        boolean previousEditing = world.editingBlocks;
        world.editingBlocks = true;
        try {
            world.setBlockAndMetadata(x, y, z, flowingId, metadata);
            world.markBlocksDirty(x, y, z, x, y, z);
            world.scheduleBlockUpdate(x, y, z, flowingId, tickRate(world));
        } finally {
            world.editingBlocks = previousEditing;
        }
    }
}
