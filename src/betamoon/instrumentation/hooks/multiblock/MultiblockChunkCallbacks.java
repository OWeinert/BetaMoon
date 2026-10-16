package betamoon.instrumentation.hooks.multiblock;

import betamoon.multiblock.MultiblockRuntime;
import net.minecraft.src.Chunk;

/**
 * Captured old values live in native stack locals, avoiding nested-call or
 * exception stack leaks.
 */
public final class MultiblockChunkCallbacks {
    private MultiblockChunkCallbacks() {
    }

    public static int entering() {
        return 0;
    }

    public static int id(Chunk chunk, int x, int y, int z, int id) {
        int oldId = chunk.getBlockID(x, y, z);
        if (oldId == id) {
            return oldId << 4 | chunk.getBlockMetadata(x, y, z);
        }
        return state(chunk, x, y, z, id, 0);
    }

    public static int state(Chunk chunk, int x, int y, int z, int id, int metadata) {
        int old = chunk.getBlockID(x, y, z) << 4 | chunk.getBlockMetadata(x, y, z);
        if (old != (id << 4 | metadata)) {
            MultiblockRuntime.changing(chunk, x, y, z);
        }
        return old;
    }

    public static int metadata(Chunk chunk, int x, int y, int z, int metadata) {
        return state(chunk, x, y, z, chunk.getBlockID(x, y, z), metadata);
    }

    public static void changed(Chunk chunk, int x, int y, int z, int old) {
        MultiblockRuntime.changed(chunk, x, y, z, old);
    }

    public static void loaded(Chunk chunk) {
        MultiblockRuntime.chunkLoaded(chunk);
    }

    public static void unloaded(Chunk chunk) {
        MultiblockRuntime.chunkUnloaded(chunk);
    }
}
