package betamoon.instrumentation.hooks.worldgen;

import betamoon.worldgen.BiomeGenRegistry;
import net.minecraft.src.BiomeGenBase;
import net.minecraft.src.World;

/** Applies compiled BetaMoon surfaces after vanilla builds a chunk surface. */
public final class WorldgenSurfaceCallbacks {
    private WorldgenSurfaceCallbacks() {
    }

    public static int entering() {
        return 0;
    }

    public static void after(World world, int chunkX, int chunkZ, byte[] blocks, BiomeGenBase[] biomes) {
        BiomeGenRegistry.applySurfaces(world, chunkX, chunkZ, blocks, biomes);
    }
}
