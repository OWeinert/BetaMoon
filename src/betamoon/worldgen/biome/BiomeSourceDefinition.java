package betamoon.worldgen.biome;

import betamoon.worldgen.WorldGenKey;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Fixed or vanilla-climate biome selection compiled at publication. */
public final class BiomeSourceDefinition {
    public final WorldGenKey key;
    public final String resourceOwner;
    public final String owner;
    public final String source;
    public final String type;
    public final WorldGenKey fixedBiome;
    public final List<ClimateEntry> entries;
    public final boolean active;
    public final int priority;

    public BiomeSourceDefinition(WorldGenKey key, String resourceOwner, String owner, String source, String type,
            WorldGenKey fixedBiome, List<ClimateEntry> entries, boolean active, int priority) {
        this.key = key;
        this.resourceOwner = resourceOwner;
        this.owner = owner;
        this.source = source;
        this.type = type;
        this.fixedBiome = fixedBiome;
        this.entries = Collections.unmodifiableList(new ArrayList<ClimateEntry>(entries));
        this.active = active;
        this.priority = priority;
    }

    public static final class ClimateEntry {
        public final WorldGenKey biome;
        public final double minTemperature;
        public final double maxTemperature;
        public final double minHumidity;
        public final double maxHumidity;
        public final int priority;

        public ClimateEntry(WorldGenKey biome, double minTemperature, double maxTemperature, double minHumidity,
                double maxHumidity, int priority) {
            this.biome = biome;
            this.minTemperature = minTemperature;
            this.maxTemperature = maxTemperature;
            this.minHumidity = minHumidity;
            this.maxHumidity = maxHumidity;
            this.priority = priority;
        }

        public boolean contains(double temperature, double humidity) {
            return temperature >= minTemperature && temperature <= maxTemperature
                    && humidity >= minHumidity && humidity <= maxHumidity;
        }
    }
}
