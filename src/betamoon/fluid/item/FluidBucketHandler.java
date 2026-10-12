package betamoon.fluid.item;

import betamoon.fluid.FluidRegistration;
import betamoon.fluid.FluidRegistry;
import forge.IBucketHandler;
import forge.MinecraftForge;
import net.minecraft.src.Item;
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
        if (fluid == null || fluid.definition().isVanilla() || world.getBlockMetadata(x, y, z) != 0) {
            return null;
        }
        if (!fluid.definition().hasBucket()) {
            return unchangedBucket();
        }
        int bucketId = fluid.definition().bucketItemId();
        if (bucketId < 0 || bucketId >= Item.itemsList.length
                || !(Item.itemsList[bucketId] instanceof FluidBucketItem)
                || FluidRegistry.instance().getByBucketItemId(bucketId) != fluid) {
            return unchangedBucket();
        }
        if (!world.setBlockWithNotify(x, y, z, 0)) {
            return unchangedBucket();
        }
        return new ItemStack(bucketId, 1, 0);
    }

    private static ItemStack unchangedBucket() {
        return new ItemStack(Item.bucketEmpty);
    }
}
