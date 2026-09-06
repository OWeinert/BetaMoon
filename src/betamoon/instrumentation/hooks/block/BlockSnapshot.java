package betamoon.instrumentation.hooks.block;

import net.minecraft.client.Minecraft;
import net.minecraft.src.World;

/** Original block state retained across a block-removal or placement method call. */
public final class BlockSnapshot {
    private final Minecraft minecraft;
    private final World world;
    private final int x;
    private final int y;
    private final int z;
    private final int side;
    private final int blockId;
    private final int blockMeta;

    BlockSnapshot(Minecraft minecraft, World world, int x, int y, int z, int side,
        int blockId, int blockMeta) {
        this.minecraft = minecraft;
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.side = side;
        this.blockId = blockId;
        this.blockMeta = blockMeta;
    }

    Minecraft getMinecraft() {
        return minecraft;
    }

    World getWorld() {
        return world;
    }

    int getX() {
        return x;
    }

    int getY() {
        return y;
    }

    int getZ() {
        return z;
    }

    int getSide() {
        return side;
    }

    int getBlockId() {
        return blockId;
    }

    int getBlockMeta() {
        return blockMeta;
    }
}
