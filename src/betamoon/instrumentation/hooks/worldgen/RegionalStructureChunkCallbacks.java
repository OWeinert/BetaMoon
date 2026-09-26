package betamoon.instrumentation.hooks.worldgen;

import betamoon.worldgen.WorldGenRegistry;
import net.minecraft.src.Chunk;

/** Narrow bridge from native chunk loading to saved regional completion recovery. */
public final class RegionalStructureChunkCallbacks {
    private RegionalStructureChunkCallbacks() {
    }

    public static int entering() {
        return 0;
    }

    public static void loaded(Chunk chunk) {
        WorldGenRegistry.regionalStructureChunkLoaded(chunk);
    }
}
