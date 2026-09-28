package betamoon.worldgen.biome;

import betamoon.worldgen.WorldGenKey;
import betamoon.wrappers.BiomeGenWrapper;
import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;

/** Keyed biome identity and its independent source/decorator attachments. */
public final class BiomeDefinition {
    public final WorldGenKey key;
    public final String resourceOwner;
    public final String owner;
    public final String source;
    public final BiomeGenWrapper biome;
    public final Set<String> tags;
    public final WorldGenKey surface;
    public volatile double minTemperature;
    public volatile double maxTemperature;
    public volatile double minHumidity;
    public volatile double maxHumidity;
    public final boolean legacyClimateRange;
    public final int decoratorCount;

    public BiomeDefinition(WorldGenKey key, String resourceOwner, String owner, String source, BiomeGenWrapper biome,
            Set<String> tags, WorldGenKey surface, double minTemperature, double maxTemperature, double minHumidity,
            double maxHumidity, boolean legacyClimateRange, int decoratorCount) {
        this.key = key;
        this.resourceOwner = resourceOwner;
        this.owner = owner;
        this.source = source;
        this.biome = biome;
        this.tags = Collections.unmodifiableSet(new TreeSet<String>(tags));
        this.surface = surface;
        this.minTemperature = minTemperature;
        this.maxTemperature = maxTemperature;
        this.minHumidity = minHumidity;
        this.maxHumidity = maxHumidity;
        this.legacyClimateRange = legacyClimateRange;
        this.decoratorCount = decoratorCount;
    }
}
