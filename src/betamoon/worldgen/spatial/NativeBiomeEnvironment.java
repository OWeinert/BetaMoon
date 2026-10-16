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

    public NativeBiomeEnvironment(WorldChunkManager manager) {
        this.manager = manager;
    }

    public WorldChunkManager manager() {
        return manager;
    }

    @Override
    public double biomeValue(String property, int x, int z) {
        manager.loadBlockGeneratorData(null, x, z, 1, 1);
        return property.equals("temperature") ? manager.temperature[0] : manager.humidity[0];
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
