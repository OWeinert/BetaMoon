package betamoon.event.context;

import net.minecraft.src.World;

public final class BlockEventCtx extends EventContext {
    private final World world;
    private final int x;
    private final int y;
    private final int z;
    private final int side;
    private final int blockId;
    private final int blockMeta;

    public BlockEventCtx(net.minecraft.client.Minecraft minecraft, World world, int x, int y, int z, int side) {
        this(minecraft, world, x, y, z, side, -1, 0);
    }

    public BlockEventCtx(net.minecraft.client.Minecraft minecraft, World world, int x, int y, int z, int side,
        int blockId, int blockMeta) {
        super(minecraft);
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.side = side;
        this.blockId = blockId;
        this.blockMeta = blockMeta;
    }

    public World getWorld() {
        return world;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getZ() {
        return z;
    }

    public int getSideHit() {
        return side;
    }

    public int getBlockId() {
        return blockId;
    }

    public int getBlockMeta() {
        return blockMeta;
    }
}
