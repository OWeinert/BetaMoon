package betamoon.instrumentation.hooks.worldgen;

import betamoon.worldgen.BiomeGenRegistry;
import betamoon.worldgen.terrain.WorldGenerationRuntime;
import betamoon.worldgen.terrain.NativeBiomeOverrideRuntime;
import betamoon.worldgen.terrain.WorldGeneratorProfile;
import net.minecraft.src.BiomeGenBase;
import net.minecraft.src.ChunkProviderGenerate;
import net.minecraft.src.World;

/** Applies compiled BetaMoon surfaces after vanilla builds a chunk surface. */
public final class WorldgenSurfaceCallbacks {
    private WorldgenSurfaceCallbacks() {
    }

    public static int entering() {
        return 0;
    }

    public static void after(ChunkProviderGenerate provider, World world, int chunkX, int chunkZ, byte[] blocks,
            BiomeGenBase[] biomes) {
        WorldGeneratorProfile profile = WorldGenerationRuntime.profile(world);
        if (profile != null && profile.terrain != null) {
            if (profile.applySurfaces) {
                profile.surfaces.applyDeclared(world.getRandomSeed(), chunkX, chunkZ, blocks, biomes);
            }
            NativeBiomeOverrideRuntime.afterSurface(provider, world, chunkX, chunkZ, blocks, biomes);
            return;
        }
        BiomeGenRegistry.applySurfaces(world, chunkX, chunkZ, blocks, biomes);
        NativeBiomeOverrideRuntime.afterSurface(provider, world, chunkX, chunkZ, blocks, biomes);
    }
}
