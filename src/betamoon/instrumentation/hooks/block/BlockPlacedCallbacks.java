package betamoon.instrumentation.hooks.block;

import betamoon.BetaMoonMain;
import betamoon.event.Events;
import betamoon.event.context.BlockEventCtx;
import net.minecraft.client.Minecraft;
import net.minecraft.src.World;

/** Runtime callbacks invoked around the transformed block-placement method. */
public final class BlockPlacedCallbacks {
    private BlockPlacedCallbacks() {
    }

    public static BlockSnapshot beforeBlockPlaced(Minecraft minecraft, World world,
        int x, int y, int z, int side) {
        if (minecraft == null || world == null) {
            return null;
        }
        try {
            int[] target = offsetBySide(x, y, z, side);
            return new BlockSnapshot(minecraft, world, target[0], target[1], target[2], side,
                world.getBlockId(target[0], target[1], target[2]),
                world.getBlockMetadata(target[0], target[1], target[2]));
        } catch (RuntimeException error) {
            BetaMoonMain.LOGGER.warning("Block-placement capture failed: " + error);
            return null;
        }
    }

    public static void afterBlockPlaced(boolean succeeded, BlockSnapshot snapshot) {
        if (!succeeded || snapshot == null) {
            return;
        }
        try {
            World world = snapshot.getWorld();
            int blockId = world.getBlockId(snapshot.getX(), snapshot.getY(), snapshot.getZ());
            int blockMeta = world.getBlockMetadata(snapshot.getX(), snapshot.getY(), snapshot.getZ());
            if (blockId <= 0 || blockId == snapshot.getBlockId()
                && blockMeta == snapshot.getBlockMeta()) {
                return;
            }
            Events.BLOCK_PLACED.publish(new BlockEventCtx(snapshot.getMinecraft(), world,
                snapshot.getX(), snapshot.getY(), snapshot.getZ(), snapshot.getSide(), blockId, blockMeta));
        } catch (RuntimeException error) {
            BetaMoonMain.LOGGER.warning("Block-placed hook listener failed: " + error);
        }
    }

    private static int[] offsetBySide(int x, int y, int z, int side) {
        switch (side) {
            case 0:
                y--;
                break;
            case 1:
                y++;
                break;
            case 2:
                z--;
                break;
            case 3:
                z++;
                break;
            case 4:
                x--;
                break;
            case 5:
                x++;
                break;
            default:
                break;
        }
        return new int[] {x, y, z};
    }
}
