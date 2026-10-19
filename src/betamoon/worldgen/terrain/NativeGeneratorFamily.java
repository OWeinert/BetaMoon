package betamoon.worldgen.terrain;

import net.minecraft.src.BiomeGenBase;
import net.minecraft.src.ChunkProviderGenerate;
import net.minecraft.src.ChunkProviderHell;
import net.minecraft.src.ChunkProviderSky;
import net.minecraft.src.IChunkProvider;
import net.minecraft.src.World;
import net.minecraft.src.WorldChunkManager;
import net.minecraft.src.WorldChunkManagerHell;

/**
 * Explicit native terrain/climate selection without inheriting unrelated
 * environment flags.
 */
public final class NativeGeneratorFamily {
    private NativeGeneratorFamily() {
    }

    public static IChunkProvider provider(World world, String family) {
        if (family.equals("nether")) {
            return new ChunkProviderHell(world, world.getRandomSeed());
        }
        if (family.equals("sky")) {
            return new ChunkProviderSky(world, world.getRandomSeed());
        }
        return new ChunkProviderGenerate(world, world.getRandomSeed());
    }

    public static WorldChunkManager climate(World world, String family) {
        return family.equals("nether")
                ? new WorldChunkManagerHell(BiomeGenBase.hell, 1, 0)
                : family.equals("sky")
                        ? new WorldChunkManagerHell(BiomeGenBase.sky, 0.5, 0)
                        : new WorldChunkManager(world);
    }
}
