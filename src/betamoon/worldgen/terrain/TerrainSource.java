package betamoon.worldgen.terrain;

import betamoon.worldgen.spatial.FieldContext;
import java.util.BitSet;

/**
 * Base terrain occupancy. Native sources delegate whole raw chunks instead of
 * synthesizing a buffer.
 */
public interface TerrainSource {
    boolean isNative();

    void fill(GenerationContext context, ChunkBlockBuffer buffer);

    double height(FieldContext context, int x, int z);

    String signature();

    default boolean usesHeightProfile(FieldContext context, int x, int z, String key) {
        return false;
    }

    /**
     * Raw occupied cells attributed to a profile; composition retains only
     * surviving material.
     */
    default BitSet heightProfileCells(FieldContext context, int x, int z, String key) {
        BitSet cells = new BitSet(128);
        if (usesHeightProfile(context, x, z, key)) {
            ChunkBlockBuffer buffer = TerrainHeightPreview.buffer(this, context, x >> 4, z >> 4);
            for (int y = 0; y < 128; y++) {
                if (buffer.blockId(x & 15, y, z & 15) != 0) {
                    cells.set(y);
                }
            }
        }
        return cells;
    }

    default int expandedSourceCount() {
        return 1;
    }
}
