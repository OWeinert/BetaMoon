package betamoon.instrumentation.hooks.worldgen;

import betamoon.worldgen.terrain.NativeBiomeOverrideRuntime;
import net.minecraft.src.BiomeGenBase;
import net.minecraft.src.IChunkProvider;
import net.minecraft.src.MapGenBase;
import net.minecraft.src.World;

/** Typed native generation callbacks; inert without a pinned world selection. */
public final class WorldgenOverrideCallbacks {
    private WorldgenOverrideCallbacks() {
    }

    public static int entering() {
        return 0;
    }

    public static void afterTerrain(World world, int x, int z, byte[] blocks, BiomeGenBase[] biomes) {
        NativeBiomeOverrideRuntime.afterTerrain(world, x, z, blocks, biomes);
    }

    public static void carve(MapGenBase caves, IChunkProvider provider, World world, int x, int z, byte[] blocks) {
        NativeBiomeOverrideRuntime.carve(caves, provider, world, x, z, blocks);
    }
}
