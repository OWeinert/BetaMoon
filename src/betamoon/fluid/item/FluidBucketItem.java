package betamoon.fluid.item;

import betamoon.dimension.DimensionRules;
import betamoon.fluid.FluidDefinition;
import betamoon.fluid.FluidRegistration;
import betamoon.fluid.FluidRegistry;
import net.minecraft.src.EntityPlayer;
import net.minecraft.src.EnumMovingObjectType;
import net.minecraft.src.Item;
import net.minecraft.src.ItemStack;
import net.minecraft.src.MathHelper;
import net.minecraft.src.MovingObjectPosition;
import net.minecraft.src.Vec3D;
import net.minecraft.src.World;

/**
 * Filled bucket whose placed fluid is resolved through its stable registry
 * slot.
 */
public final class FluidBucketItem extends Item {
    public FluidBucketItem(int shiftedId) {
        super(shiftedId - 256);
        setMaxStackSize(1);
        setContainerItem(Item.bucketEmpty);
    }

    public void configureName(String name) {
        setItemName(name);
    }

    @Override
    public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        FluidRegistration registration = FluidRegistry.instance().getByBucketItemId(shiftedIndex);
        if (registration == null) {
            return stack;
        }
        FluidDefinition fluid = registration.definition();
        float partialTick = 1.0F;
        float pitch = player.prevRotationPitch + (player.rotationPitch - player.prevRotationPitch) * partialTick;
        float yaw = player.prevRotationYaw + (player.rotationYaw - player.prevRotationYaw) * partialTick;
        double eyeX = player.prevPosX + (player.posX - player.prevPosX) * partialTick;
        double eyeY = player.prevPosY + (player.posY - player.prevPosY) * partialTick + 1.62D - player.yOffset;
        double eyeZ = player.prevPosZ + (player.posZ - player.prevPosZ) * partialTick;
        Vec3D start = Vec3D.createVector(eyeX, eyeY, eyeZ);
        float yawCos = MathHelper.cos(-yaw * 0.01745329F - (float) Math.PI);
        float yawSin = MathHelper.sin(-yaw * 0.01745329F - (float) Math.PI);
        float pitchCos = -MathHelper.cos(-pitch * 0.01745329F);
        float pitchSin = MathHelper.sin(-pitch * 0.01745329F);
        double reach = 5.0D;
        Vec3D end = start.addVector(yawSin * pitchCos * reach, pitchSin * reach, yawCos * pitchCos * reach);
        MovingObjectPosition hit = world.rayTraceBlocks_do(start, end, false);
        if (hit == null || hit.typeOfHit != EnumMovingObjectType.TILE) {
            return stack;
        }

        int x = hit.blockX;
        int y = hit.blockY;
        int z = hit.blockZ;
        if (!world.func_6466_a(player, x, y, z)) {
            return stack;
        }
        if (hit.sideHit == 0) {
            y--;
        } else if (hit.sideHit == 1) {
            y++;
        } else if (hit.sideHit == 2) {
            z--;
        } else if (hit.sideHit == 3) {
            z++;
        } else if (hit.sideHit == 4) {
            x--;
        } else if (hit.sideHit == 5) {
            x++;
        }
        if (!world.func_6466_a(player, x, y, z) || !canPlace(world, x, y, z)) {
            return stack;
        }

        if (DimensionRules.evaporatesWater(world.worldProvider) && "water".equals(fluid.preset())) {
            evaporate(world, x, y, z);
            return new ItemStack(Item.bucketEmpty);
        }
        return world.setBlockAndMetadataWithNotify(x, y, z, fluid.flowingBlockId(), 0)
                ? new ItemStack(Item.bucketEmpty)
                : stack;
    }

    private static boolean canPlace(World world, int x, int y, int z) {
        if (!world.blockExists(x, y, z) || world.getBlockTileEntity(x, y, z) != null) {
            return false;
        }
        int targetId = world.getBlockId(x, y, z);
        if (targetId == 0) {
            return true;
        }
        return FluidRegistry.instance().getByBlockId(targetId) == null && !world.getBlockMaterial(x, y, z).isSolid();
    }

    private static void evaporate(World world, int x, int y, int z) {
        world.playSoundEffect(x + 0.5D, y + 0.5D, z + 0.5D, "random.fizz", 0.5F,
                2.6F + (world.rand.nextFloat() - world.rand.nextFloat()) * 0.8F);
        for (int particle = 0; particle < 8; particle++) {
            world.spawnParticle("largesmoke", x + Math.random(), y + Math.random(), z + Math.random(), 0.0D, 0.0D,
                    0.0D);
        }
    }
}
