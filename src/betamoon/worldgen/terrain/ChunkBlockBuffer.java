package betamoon.worldgen.terrain;

import betamoon.worldgen.GenerationMetrics;
import betamoon.worldgen.surface.SurfaceRuleSet;
import java.util.Arrays;
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
        setGenerated(index, id, damage);
    }

    /**
     * Coordinates and immutable states are validated by the compiled generation
     * caller.
     */
    void setGenerated(int index, int id, int damage) {
        int shift = (index & 1) * 4;
        int previous = (metadata.data[index >> 1] >> shift) & 15;
        if ((blocks[index] & 255) != id || previous != damage) {
            blocks[index] = (byte) id;
            metadata.data[index >> 1] = (byte) ((metadata.data[index >> 1] & ~(15 << shift)) | (damage << shift));
            columnRevisions[((index >> 7) & 15) * 16 + (index >> 11)]++;
        }
    }

    public void fillColumn(int x, int z, int minY, int maxY, int id, int damage) {
        index(x, minY, z);
        index(x, maxY, z);
        if (minY > maxY) {
            throw new IllegalArgumentException("Raw column minimum exceeds maximum");
        }
        validateState(id, damage);
        int columnStart = (x << 11) | (z << 7);
        int start = columnStart + minY;
        int end = columnStart + maxY + 1;
        Arrays.fill(blocks, start, end, (byte) id);
        if ((start & 1) != 0) {
            metadata.data[start >> 1] = (byte) ((metadata.data[start >> 1] & 15) | (damage << 4));
            start++;
        }
        if ((end & 1) != 0) {
            metadata.data[end >> 1] = (byte) ((metadata.data[end >> 1] & 240) | damage);
            end--;
        }
        Arrays.fill(metadata.data, start >> 1, end >> 1, (byte) (damage * 17));
        columnRevisions[columnIndex(x, z)]++;
    }

    public byte[] copyBlocks() {
        GenerationMetrics.record(GenerationMetrics.Counter.BLOCK_BYTES_COPIED, blocks.length);
        return blocks.clone();
    }

    public byte[] copyMetadata() {
        GenerationMetrics.record(GenerationMetrics.Counter.METADATA_BYTES_COPIED, metadata.data.length);
        return metadata.data.clone();
    }

    void copyMetadataTo(byte[] target) {
        GenerationMetrics.record(GenerationMetrics.Counter.METADATA_BYTES_COPIED, metadata.data.length);
        System.arraycopy(metadata.data, 0, target, 0, metadata.data.length);
    }

    void restore(byte[] sourceBlocks, byte[] sourceMetadata) {
        System.arraycopy(sourceBlocks, 0, blocks, 0, CELL_COUNT);
        System.arraycopy(sourceMetadata, 0, metadata.data, 0, metadata.data.length);
        for (int column = 0; column < columnRevisions.length; column++) {
            columnRevisions[column]++;
        }
    }

    void clear() {
        Arrays.fill(blocks, (byte) 0);
        Arrays.fill(metadata.data, (byte) 0);
        for (int column = 0; column < columnRevisions.length; column++) {
            columnRevisions[column]++;
        }
    }

    void carveNative(NativeCaveSampler sampler, long seed, int chunkX, int chunkZ) {
        byte[] original = blocks.clone();
        sampler.carve(seed, chunkX, chunkZ, blocks);
        for (int index = 0; index < blocks.length; index++) {
            if (blocks[index] != original[index]) {
                int shift = (index & 1) * 4;
                metadata.data[index >> 1] &= ~(15 << shift);
                columnRevisions[((index >> 7) & 15) * 16 + (index >> 11)]++;
            }
        }
    }

    void applySurface(SurfaceRuleSet rule, GenerationContext context, int x, int z) {
        rule.apply(context.fields.seed, context.chunkX, context.chunkZ, blocks, x, z, index -> {
            validateState(blocks[index] & 255, 0);
            int shift = (index & 1) * 4;
            metadata.data[index >> 1] &= ~(15 << shift);
            columnRevisions[((index >> 7) & 15) * 16 + (index >> 11)]++;
        });
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
