package betamoon.worldgen.terrain;

import net.minecraft.src.Block;
import net.minecraft.src.NibbleArray;

/**
 * Owned raw cells without a world reference, callbacks, chunk loads or tile
 * entity creation.
 */
public final class ChunkBlockBuffer {
    public static final int WIDTH = 16;
    public static final int HEIGHT = 128;
    public static final int CELL_COUNT = WIDTH * WIDTH * HEIGHT;
    private final byte[] blocks = new byte[CELL_COUNT];
    private final NibbleArray metadata = new NibbleArray(CELL_COUNT);
    private final long[] columnRevisions = new long[WIDTH * WIDTH];

    public int blockId(int x, int y, int z) {
        return blocks[index(x, y, z)] & 255;
    }

    public int metadata(int x, int y, int z) {
        index(x, y, z);
        return metadata.getNibble(x, y, z);
    }

    public void set(int x, int y, int z, int id, int damage) {
        int index = index(x, y, z);
        validateState(id, damage);
        if ((blocks[index] & 255) != id || metadata.getNibble(x, y, z) != damage) {
            blocks[index] = (byte) id;
            metadata.setNibble(x, y, z, damage);
            columnRevisions[columnIndex(x, z)]++;
        }
    }

    public void fillColumn(int x, int z, int minY, int maxY, int id, int damage) {
        index(x, minY, z);
        index(x, maxY, z);
        if (minY > maxY) {
            throw new IllegalArgumentException("Raw column minimum exceeds maximum");
        }
        validateState(id, damage);
        for (int y = minY; y <= maxY; y++) {
            blocks[index(x, y, z)] = (byte) id;
            metadata.setNibble(x, y, z, damage);
        }
        columnRevisions[columnIndex(x, z)]++;
    }

    public byte[] copyBlocks() {
        return blocks.clone();
    }

    public byte[] copyMetadata() {
        return metadata.data.clone();
    }

    long columnRevision(int x, int z) {
        return columnRevisions[columnIndex(x, z)];
    }

    static int columnIndex(int x, int z) {
        if (x < 0 || x >= WIDTH || z < 0 || z >= WIDTH) {
            throw new IllegalArgumentException("Raw column coordinates must be in 0..15");
        }
        return z * WIDTH + x;
    }

    private static int index(int x, int y, int z) {
        columnIndex(x, z);
        if (y < 0 || y >= HEIGHT) {
            throw new IllegalArgumentException("Raw block Y must be in 0..127");
        }
        return (x << 11) | (z << 7) | y;
    }

    static void validateState(int id, int damage) {
        if (id < 0 || id > 255 || damage < 0 || damage > 15 || (id == 0 && damage != 0)) {
            throw new IllegalArgumentException("Raw block state requires ID 0..255 and metadata 0..15; air uses zero");
        }
        if (id != 0 && (Block.blocksList[id] == null || Block.isBlockContainer[id])) {
            throw new IllegalArgumentException("Raw terrain cannot use a missing block or tile entity: " + id);
        }
    }
}
