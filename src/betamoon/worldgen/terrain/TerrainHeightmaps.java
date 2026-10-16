package betamoon.worldgen.terrain;

import java.util.Arrays;
import net.minecraft.src.Block;

/**
 * Derived highest occupied Y values, with -1 for an empty column; distinct from
 * native light heights.
 */
public final class TerrainHeightmaps {
    private final ChunkBlockBuffer buffer;
    private final long[] revisions = new long[256];
    private final int[] worldSurface = new int[256];
    private final int[] oceanFloor = new int[256];
    private final int[] fluidSurface = new int[256];
    private final int[] motionBlocking = new int[256];

    public TerrainHeightmaps(ChunkBlockBuffer buffer) {
        if (buffer == null) {
            throw new IllegalArgumentException("Derived maps require a raw chunk buffer");
        }
        this.buffer = buffer;
        Arrays.fill(worldSurface, -1);
        Arrays.fill(oceanFloor, -1);
        Arrays.fill(fluidSurface, -1);
        Arrays.fill(motionBlocking, -1);
        // Each view tracks revisions independently, including its initial empty
        // columns.
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                refresh(x, z);
            }
        }
    }

    public int worldSurface(int x, int z) {
        update(x, z);
        return worldSurface[ChunkBlockBuffer.columnIndex(x, z)];
    }

    public int oceanFloor(int x, int z) {
        update(x, z);
        return oceanFloor[ChunkBlockBuffer.columnIndex(x, z)];
    }

    public int fluidSurface(int x, int z) {
        update(x, z);
        return fluidSurface[ChunkBlockBuffer.columnIndex(x, z)];
    }

    public int motionBlocking(int x, int z) {
        update(x, z);
        return motionBlocking[ChunkBlockBuffer.columnIndex(x, z)];
    }

    private void update(int x, int z) {
        if (revisions[ChunkBlockBuffer.columnIndex(x, z)] != buffer.columnRevision(x, z)) {
            refresh(x, z);
        }
    }

    private void refresh(int x, int z) {
        int column = ChunkBlockBuffer.columnIndex(x, z);
        worldSurface[column] = -1;
        oceanFloor[column] = -1;
        fluidSurface[column] = -1;
        motionBlocking[column] = -1;
        for (int y = 127; y >= 0; y--) {
            int id = buffer.blockId(x, y, z);
            if (id == 0) {
                continue;
            }
            Block block = Block.blocksList[id];
            if (worldSurface[column] < 0) {
                worldSurface[column] = y;
            }
            if (oceanFloor[column] < 0 && block.blockMaterial.isSolid() && !block.blockMaterial.getIsLiquid()) {
                oceanFloor[column] = y;
            }
            if (fluidSurface[column] < 0 && block.blockMaterial.getIsLiquid()) {
                fluidSurface[column] = y;
            }
            if (motionBlocking[column] < 0 && block.blockMaterial.getIsSolid()) {
                motionBlocking[column] = y;
            }
        }
        revisions[column] = buffer.columnRevision(x, z);
    }
}
