package betamoon.worldgen.terrain;

import betamoon.worldgen.spatial.FieldContext;

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
    private final byte[][] neighbors = new byte[4][];
    private final RawTerrainCache cache;

    public RawGenerationNeighborhood(TerrainSource source, GenerationContext context, ChunkBlockBuffer buffer) {
        this(source, context, buffer, null);
    }

    RawGenerationNeighborhood(TerrainSource source, GenerationContext context, ChunkBlockBuffer buffer,
            RawTerrainCache cache) {
        this(source, context, cache == null ? buffer.copyBlocks() : cache.capture(source, context, buffer).blocks,
                cache);
    }

    RawGenerationNeighborhood(TerrainSource source, GenerationContext context, byte[] original, RawTerrainCache cache) {
        this.source = source;
        this.cache = cache;
        fields = context.fields;
        centerX = context.chunkX;
        centerZ = context.chunkZ;
        centerBlocks = original;
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
        if (cx == centerX && cz == centerZ) {
            return centerBlocks[((x & 15) << 11) | ((z & 15) << 7) | y] & 255;
        }
        int side = cx < centerX ? 0 : cx > centerX ? 1 : cz < centerZ ? 2 : 3;
        byte[] blocks = neighbors[side];
        if (blocks == null) {
            blocks = cache == null ? null : cache.get(source, fields, cx, cz);
            if (blocks == null) {
                ChunkBlockBuffer neighbor = new ChunkBlockBuffer();
                GenerationContext context = GenerationContext.preview(fields, cx, cz);
                if (cache == null) {
                    source.fill(context, neighbor);
                    blocks = neighbor.copyBlocks();
                } else {
                    blocks = cache.base(source, context, neighbor).blocks;
                }
            }
            neighbors[side] = blocks;
        }
        return blocks[((x & 15) << 11) | ((z & 15) << 7) | y] & 255;
    }

}
