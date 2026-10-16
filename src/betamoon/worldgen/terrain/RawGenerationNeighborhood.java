package betamoon.worldgen.terrain;

import betamoon.worldgen.spatial.FieldContext;
import java.util.HashMap;
import java.util.Map;

/**
 * At most the four directly adjacent base buffers, sampled without world chunk
 * access.
 */
public final class RawGenerationNeighborhood {
    private final TerrainSource source;
    private final FieldContext fields;
    private final int centerX;
    private final int centerZ;
    private final byte[] centerBlocks;
    private final Map<Long, byte[]> chunks = new HashMap<Long, byte[]>();

    public RawGenerationNeighborhood(TerrainSource source, GenerationContext context, ChunkBlockBuffer buffer) {
        this.source = source;
        fields = context.fields;
        centerX = context.chunkX;
        centerZ = context.chunkZ;
        centerBlocks = buffer.copyBlocks();
    }

    public int block(int x, int y, int z) {
        if (y < 0 || y > 127) {
            return 0;
        }
        int cx = Math.floorDiv(x, 16);
        int cz = Math.floorDiv(z, 16);
        if (Math.abs(cx - centerX) + Math.abs(cz - centerZ) > 1) {
            throw new IllegalArgumentException("Raw neighbor sampling exceeds its one-cell boundary contract");
        }
        if (cx < -1875000 || cx > 1874999 || cz < -1875000 || cz > 1874999) {
            return 0;
        }
        byte[] blocks = cx == centerX && cz == centerZ ? centerBlocks : chunks.get(key(cx, cz));
        if (blocks == null) {
            ChunkBlockBuffer neighbor = new ChunkBlockBuffer();
            source.fill(GenerationContext.preview(fields, cx, cz), neighbor);
            blocks = neighbor.copyBlocks();
            chunks.put(key(cx, cz), blocks);
        }
        return blocks[((x & 15) << 11) | ((z & 15) << 7) | y] & 255;
    }

    private static long key(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }
}
