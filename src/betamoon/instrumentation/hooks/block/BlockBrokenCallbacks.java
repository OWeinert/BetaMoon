package betamoon.instrumentation.hooks.block;

import betamoon.BetaMoonMain;
import betamoon.event.Events;
import betamoon.event.context.BlockEventCtx;
import net.minecraft.client.Minecraft;
import net.minecraft.src.World;

/** Runtime callbacks invoked by the transformed block-removal method. */
public final class BlockBrokenCallbacks {
    private BlockBrokenCallbacks() {
    }

    public static BlockSnapshot beforeBlockBroken(Minecraft minecraft, int x, int y, int z, int side) {
        if (minecraft == null || minecraft.theWorld == null) {
            return null;
        }
        try {
            World world = minecraft.theWorld;
            int blockId = world.getBlockId(x, y, z);
            if (blockId <= 0) {
                return null;
            }
            return new BlockSnapshot(minecraft, world, x, y, z, side, blockId,
                world.getBlockMetadata(x, y, z));
        } catch (RuntimeException error) {
            BetaMoonMain.LOGGER.warning("Block-broken capture failed: " + error);
            return null;
        }
    }

    public static void afterBlockBroken(boolean succeeded, BlockSnapshot snapshot) {
        if (!succeeded || snapshot == null) {
            return;
        }
        try {
            Events.BLOCK_BROKEN.publish(new BlockEventCtx(snapshot.getMinecraft(), snapshot.getWorld(),
                snapshot.getX(), snapshot.getY(), snapshot.getZ(), snapshot.getSide(),
                snapshot.getBlockId(), snapshot.getBlockMeta()));
        } catch (RuntimeException error) {
            BetaMoonMain.LOGGER.warning("Block-broken hook listener failed: " + error);
        }
    }
}
