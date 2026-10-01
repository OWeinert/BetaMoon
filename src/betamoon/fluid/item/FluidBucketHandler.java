package betamoon.fluid.item;

import betamoon.fluid.FluidRegistration;
import betamoon.fluid.FluidRegistry;
import forge.IBucketHandler;
import forge.MinecraftForge;
import net.minecraft.src.ItemStack;
import net.minecraft.src.World;

/** Single permanent Forge bucket handler backed by the reloadable fluid registry. */
public final class FluidBucketHandler implements IBucketHandler {
    private static final FluidBucketHandler INSTANCE = new FluidBucketHandler();
    private static boolean registered;

    private FluidBucketHandler() {
    }

    public static synchronized void register() {
        if (!registered) {
            MinecraftForge.registerCustomBucketHandler(INSTANCE);
            registered = true;
        }
    }

    @Override
    public ItemStack fillCustomBucket(World world, int x, int y, int z) {
        FluidRegistration fluid = FluidRegistry.instance().getByBlockId(world.getBlockId(x, y, z));
        if (fluid == null || fluid.definition().isVanilla() || !fluid.definition().hasBucket()
                || world.getBlockMetadata(x, y, z) != 0) {
            return null;
        }
        world.setBlockWithNotify(x, y, z, 0);
        return new ItemStack(fluid.definition().bucketItemId(), 1, 0);
    }
}
