package betamoon.worldgen.terrain;

import betamoon.worldgen.spatial.FieldContext;

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

    default int expandedSourceCount() {
        return 1;
    }
}
