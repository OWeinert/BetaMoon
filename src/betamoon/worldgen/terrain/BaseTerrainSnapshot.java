package betamoon.worldgen.terrain;

import java.util.BitSet;
import net.minecraft.src.BiomeGenBase;

/**
 * Immutable result of base/material generation, before carving and surfaces.
 */
final class BaseTerrainSnapshot {
    final byte[] blocks;
    private final byte[] metadata;
    private final double[] preliminarySurface;
    private final BiomeGenBase[] biomes;
    private final BitSet blockIds = new BitSet(256);

    BaseTerrainSnapshot(byte[] blocks) {
        this.blocks = blocks;
        metadata = null;
        preliminarySurface = null;
        biomes = null;
    }

    BaseTerrainSnapshot(GenerationContext context, ChunkBlockBuffer buffer) {
        blocks = buffer.copyBlocks();
        metadata = buffer.copyMetadata();
        preliminarySurface = context.preliminarySurface.clone();
        biomes = context.copyBiomes();
        for (byte block : blocks) {
            blockIds.set(block & 255);
        }
    }

    boolean matches(GenerationContext context) {
        return metadata != null && context.matchesBiomes(biomes);
    }

    int retainedBytes() {
        return blocks.length + (metadata == null ? 0 : metadata.length + 256 * 8 + 256 * 8) + 512;
    }

    void restore(GenerationContext context, ChunkBlockBuffer buffer) {
        for (int id = blockIds.nextSetBit(0); id >= 0; id = blockIds.nextSetBit(id + 1)) {
            ChunkBlockBuffer.validateState(id, 0);
        }
        buffer.restore(blocks, metadata);
        System.arraycopy(preliminarySurface, 0, context.preliminarySurface, 0, 256);
    }
}
