package betamoon.worldgen.spatial;

import betamoon.worldgen.BiomeGenRegistry;
import java.util.Collections;
import net.minecraft.src.BiomeGenBase;
import net.minecraft.src.WorldChunkManager;

/**
 * Climate and biome queries for worlds retaining their original native manager.
 */
public final class NativeBiomeEnvironment implements FieldContext.Environment {
    private final WorldChunkManager manager;
    private final NativeClimateSamples climate;

    public NativeBiomeEnvironment(WorldChunkManager manager) {
        this.manager = manager;
        climate = new NativeClimateSamples(manager);
    }

    public WorldChunkManager manager() {
        return manager;
    }

    @Override
    public double biomeValue(String property, int x, int z) {
        return climate.value(property, x, z);
    }

    @Override
    public boolean biomeMatches(String selector, int x, int z) {
        return BiomeGenRegistry.matchesSelectors(manager.getBiomeGenAt(x, z), Collections.singleton(selector));
    }

    @Override
    public BiomeGenBase biome(int x, int z) {
        return manager.getBiomeGenAt(x, z);
    }
}
