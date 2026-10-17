package betamoon.worldgen.terrain;

import betamoon.worldgen.GenerationMetrics;
import net.minecraft.src.Chunk;
import net.minecraft.src.World;

/**
 * Applies metadata before native height/skylight setup and initializes local
 * emitted block light.
 */
public final class RawChunkFinalizer {
    private RawChunkFinalizer() {
    }

    public static Chunk build(World world, int chunkX, int chunkZ, ChunkBlockBuffer buffer) {
        long started = GenerationMetrics.start();
        Chunk chunk = new Chunk(world, buffer.copyBlocks(), chunkX, chunkZ);
        buffer.copyMetadataTo(chunk.data.data);
        chunk.func_1024_c();
        GeneratedChunkLighting.initialize(chunk);
        GenerationMetrics.finish(GenerationMetrics.Counter.FINALIZATION_NANOS, started);
        return chunk;
    }
}
