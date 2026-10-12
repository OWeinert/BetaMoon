package betamoon.fluid.block;

import betamoon.fluid.FluidAppearance;
import betamoon.fluid.interaction.FluidInteractionRegistry;
import java.util.Random;
import net.minecraft.src.Material;
import net.minecraft.src.World;

/** Scheduled member of a custom fluid pair. */
public final class FlowingFluidBlock extends FluidBlock {
    public FlowingFluidBlock(int id, Material material, FluidAppearance appearance) {
        super(id, material, appearance, true);
    }

    @Override
    public void updateTick(World world, int x, int y, int z, Random random) {
        FluidSimulation.update(this, world, x, y, z, random);
    }

    @Override
    public void onBlockAdded(World world, int x, int y, int z) {
        if (world.getBlockId(x, y, z) == blockID
                && !FluidInteractionRegistry.instance().evaluateAround(world, x, y, z)) {
            world.scheduleBlockUpdate(x, y, z, blockID, tickRate(world));
        }
    }

    @Override
    public void onNeighborBlockChange(World world, int x, int y, int z, int neighborId) {
        FluidInteractionRegistry.instance().evaluateAround(world, x, y, z);
    }
}
