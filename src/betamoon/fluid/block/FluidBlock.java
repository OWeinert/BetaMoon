package betamoon.fluid.block;

import betamoon.fluid.FluidAppearance;
import betamoon.fluid.FluidDefinition;
import betamoon.fluid.FluidRegistration;
import betamoon.fluid.FluidRegistry;
import betamoon.fluid.FluidRenderType;
import betamoon.fluid.FluidState;
import java.util.Random;
import net.minecraft.src.AxisAlignedBB;
import net.minecraft.src.Block;
import net.minecraft.src.Entity;
import net.minecraft.src.IBlockAccess;
import net.minecraft.src.Material;
import net.minecraft.src.Vec3D;
import net.minecraft.src.World;

/** Common native behavior for a registered custom fluid block. */
public abstract class FluidBlock extends Block {
    private volatile FluidAppearance appearance;

    protected FluidBlock(int id, Material material, FluidAppearance appearance, boolean ticksOnLoad) {
        super(id, material);
        setBlockBounds(0.0F, 0.0F, 0.0F, 1.0F, 1.0F, 1.0F);
        setTickOnLoad(ticksOnLoad);
        setHardness(100.0F);
        disableStats();
        disableNeighborNotifyOnMetadataChange();
        applyAppearance(appearance);
    }

    public final FluidRegistration fluid() {
        FluidRegistration registration = FluidRegistry.instance().getByBlockId(blockID);
        if (registration == null) {
            throw new IllegalStateException("Fluid block " + blockID + " has not been published.");
        }
        return registration;
    }

    public final FluidDefinition definition() {
        return fluid().definition();
    }

    public final void applyAppearance(FluidAppearance appearance) {
        this.appearance = appearance;
        blockIndexInTexture = appearance.stillTexture();
        setLightValue(appearance.light() / 15.0F);
        setLightOpacity(appearance.opacity());
    }

    public final void configureName(String name) {
        setBlockName(name);
    }

    public final int getBlockTextureFromSide(int side) {
        FluidAppearance appearance = this.appearance;
        return side == 0 || side == 1 ? appearance.stillTexture() : appearance.flowingTexture();
    }

    @Override
    public final int colorMultiplier(IBlockAccess world, int x, int y, int z) {
        return appearance.tint();
    }

    @Override
    public final boolean renderAsNormalBlock() {
        return false;
    }

    @Override
    public final boolean isOpaqueCube() {
        return false;
    }

    @Override
    public final int getRenderType() {
        return FluidRenderType.get();
    }

    @Override
    public final boolean canCollideCheck(int metadata, boolean hitLiquids) {
        return hitLiquids && FluidState.of(metadata).isSource();
    }

    @Override
    public final AxisAlignedBB getCollisionBoundingBoxFromPool(World world, int x, int y, int z) {
        return null;
    }

    @Override
    public final boolean getIsBlockSolid(IBlockAccess world, int x, int y, int z, int side) {
        int neighborId = world.getBlockId(x, y, z);
        if (FluidRegistry.instance().isSameFluid(blockID, neighborId)) {
            return false;
        }
        Material material = world.getBlockMaterial(x, y, z);
        return material != Material.ice && (side == 1 || super.getIsBlockSolid(world, x, y, z, side));
    }

    @Override
    public final boolean shouldSideBeRendered(IBlockAccess world, int x, int y, int z, int side) {
        int neighborId = world.getBlockId(x, y, z);
        if (FluidRegistry.instance().isSameFluid(blockID, neighborId)) {
            return false;
        }
        Material material = world.getBlockMaterial(x, y, z);
        return material != Material.ice && (side == 1 || super.shouldSideBeRendered(world, x, y, z, side));
    }

    @Override
    public final int idDropped(int metadata, Random random) {
        return 0;
    }

    @Override
    public final int quantityDropped(Random random) {
        return 0;
    }

    @Override
    public final int tickRate() {
        return definition().flow().tickRate();
    }

    public final int tickRate(World world) {
        return definition().flow().tickRate(world.worldProvider.isHellWorld);
    }

    @Override
    public final int getRenderBlockPass() {
        return appearance.renderPass();
    }

    @Override
    public final float getBlockBrightness(IBlockAccess world, int x, int y, int z) {
        return Math.max(world.getLightBrightness(x, y, z), world.getLightBrightness(x, y + 1, z));
    }

    @Override
    public final void velocityToAddToEntity(World world, int x, int y, int z, Entity entity, Vec3D velocity) {
        Vec3D flow = flowVector(world, x, y, z);
        velocity.xCoord += flow.xCoord;
        velocity.yCoord += flow.yCoord;
        velocity.zCoord += flow.zCoord;
    }

    public final Vec3D flowVector(IBlockAccess world, int x, int y, int z) {
        Vec3D result = Vec3D.createVector(0.0D, 0.0D, 0.0D);
        int current = effectiveDecay(world, x, y, z);
        int[] offsetX = {-1, 0, 1, 0};
        int[] offsetZ = {0, -1, 0, 1};

        for (int direction = 0; direction < 4; direction++) {
            int neighborX = x + offsetX[direction];
            int neighborZ = z + offsetZ[direction];
            int neighbor = effectiveDecay(world, neighborX, y, neighborZ);
            if (neighbor < 0) {
                if (!world.getBlockMaterial(neighborX, y, neighborZ).getIsSolid()) {
                    neighbor = effectiveDecay(world, neighborX, y - 1, neighborZ);
                    if (neighbor >= 0) {
                        int difference = neighbor - (current - 8);
                        result = result.addVector(offsetX[direction] * difference, 0.0D,
                                offsetZ[direction] * difference);
                    }
                }
            } else {
                int difference = neighbor - current;
                result = result.addVector(offsetX[direction] * difference, 0.0D,
                        offsetZ[direction] * difference);
            }
        }

        if (FluidState.of(world.getBlockMetadata(x, y, z)).isFalling() && touchesSolidWall(world, x, y, z)) {
            result = result.normalize().addVector(0.0D, -6.0D, 0.0D);
        }
        return result.normalize();
    }

    public final double flowAngle(IBlockAccess world, int x, int y, int z) {
        Vec3D flow = flowVector(world, x, y, z);
        return flow.xCoord == 0.0D && flow.zCoord == 0.0D
                ? -1000.0D
                : Math.atan2(flow.zCoord, flow.xCoord) - Math.PI * 0.5D;
    }

    private int effectiveDecay(IBlockAccess world, int x, int y, int z) {
        if (!FluidRegistry.instance().isSameFluid(blockID, world.getBlockId(x, y, z))) {
            return -1;
        }
        return FluidState.of(world.getBlockMetadata(x, y, z)).level();
    }

    private boolean touchesSolidWall(IBlockAccess world, int x, int y, int z) {
        return getIsBlockSolid(world, x, y, z - 1, 2)
                || getIsBlockSolid(world, x, y, z + 1, 3)
                || getIsBlockSolid(world, x - 1, y, z, 4)
                || getIsBlockSolid(world, x + 1, y, z, 5)
                || getIsBlockSolid(world, x, y + 1, z - 1, 2)
                || getIsBlockSolid(world, x, y + 1, z + 1, 3)
                || getIsBlockSolid(world, x - 1, y + 1, z, 4)
                || getIsBlockSolid(world, x + 1, y + 1, z, 5);
    }
}
