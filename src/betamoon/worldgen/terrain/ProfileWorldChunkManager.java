package betamoon.worldgen.terrain;

import betamoon.worldgen.BiomeGenRegistry;
import betamoon.worldgen.SeedMixer;
import betamoon.worldgen.WorldGenKey;
import betamoon.worldgen.biome.BiomeDefinition;
import betamoon.worldgen.biome.BiomeSourceDefinition;
import betamoon.worldgen.spatial.FieldContext;
import betamoon.worldgen.spatial.NoiseDefinition;
import java.util.Arrays;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.src.BiomeGenBase;
import net.minecraft.src.WorldChunkManager;

/**
 * Profile-bound biome lookup, preserving the native climate samplers and their
 * arrays.
 */
public final class ProfileWorldChunkManager extends WorldChunkManager implements FieldContext.Environment {
    private final WorldChunkManager climate;
    private final BiomeSourceDefinition source;
    private final Map<WorldGenKey, BiomeGenBase> resolved = new LinkedHashMap<WorldGenKey, BiomeGenBase>();
    private FieldContext fields;
    private final long selectionSalt;
    private final BiomeGenBase[] lookup;
    private final Map<BiomeGenBase, Set<String>> selectors = new IdentityHashMap<BiomeGenBase, Set<String>>();

    public ProfileWorldChunkManager(WorldChunkManager climate, BiomeSourceDefinition source) {
        this.climate = climate;
        this.source = source;
        selectionSalt = SeedMixer.hash(source == null ? "native" : source.key.toString());
        for (BiomeDefinition definition : BiomeGenRegistry.definitions()) {
            Set<String> names = new HashSet<String>();
            names.add(definition.key.toString());
            for (String tag : definition.tags) {
                names.add("#" + tag);
            }
            selectors.put(definition.biome, names);
        }
        if (source == null) {
            lookup = BiomeGenRegistry.copyClimateLookup();
            return;
        }
        lookup = new BiomeGenBase[4096];

        if (source.fixedBiome != null) {
            resolved.put(source.fixedBiome, requiredBiome(source.fixedBiome));
        }
        if (source.fallbackBiome != null) {
            resolved.put(source.fallbackBiome, requiredBiome(source.fallbackBiome));
        }
        for (BiomeSourceDefinition.ClimateEntry entry : source.entries) {
            resolved.put(entry.biome, requiredBiome(entry.biome));
        }
        for (int t = 0; t < 64; t++) {
            for (int h = 0; h < 64; h++) {
                BiomeGenBase biome = BiomeGenBase.getBiome(t / 63.0f, h / 63.0f);
                if (source.type.equals("fixed")) {
                    biome = resolved.get(source.fixedBiome);
                } else if (!source.type.equals("spatial_climate")) {
                    BiomeSourceDefinition.ClimateEntry selected = null;
                    for (BiomeSourceDefinition.ClimateEntry entry : source.entries) {
                        if (entry.contains(t / 63.0, h / 63.0) && (selected == null
                                || entry.priority > selected.priority
                                || entry.priority == selected.priority && entry.biome.compareTo(selected.biome) < 0)) {
                            selected = entry;
                        }
                    }
                    if (selected != null) {
                        biome = resolved.get(selected.biome);
                    }
                }
                lookup[t + h * 64] = biome;
            }
        }
    }

    public WorldChunkManager climateManager() {
        return climate;
    }

    public boolean usesSource(BiomeSourceDefinition definition) {
        return source == definition;
    }

    @Override
    public double biomeValue(String property, int x, int z) {
        climate.loadBlockGeneratorData(null, x, z, 1, 1);
        return property.equals("temperature") ? climate.temperature[0] : climate.humidity[0];
    }

    @Override
    public boolean biomeMatches(String selector, int x, int z) {
        BiomeGenBase biome = getBiomeGenAt(x, z);
        Set<String> names = selectors.get(biome);
        String normalized = selector.toLowerCase(Locale.ROOT);
        return biome.biomeName != null && biome.biomeName.equalsIgnoreCase(normalized)
                || names != null && names.contains(normalized);
    }

    @Override
    public BiomeGenBase biome(int x, int z) {
        return getBiomeGenAt(x, z);
    }

    public void setFieldContext(FieldContext fields) {
        this.fields = fields;
    }

    private BiomeGenBase spatialBiome(int x, int z, double temperature, double humidity, BiomeGenBase nativeBiome) {
        if (fields == null) {
            throw new IllegalStateException("Spatial biome source has no pinned sampling context");
        }
        double[] weights = new double[source.entries.size()];
        double total = 0;
        int priority = Integer.MIN_VALUE;
        for (int i = 0; i < weights.length; i++) {
            BiomeSourceDefinition.ClimateEntry entry = source.entries.get(i);
            if (!entry.contains(temperature, humidity)) {
                continue;
            }
            double weight = entry.weight * (entry.spatial == null ? 1 : entry.spatial.weight(fields, x, z));
            if (weight <= 0 || entry.priority < priority) {
                continue;
            }
            if (entry.priority > priority) {
                Arrays.fill(weights, 0);
                total = 0;
                priority = entry.priority;
            }
            weights[i] = weight;
            total += weight;
        }
        if (total == 0) {
            return source.fallbackBiome == null ? nativeBiome : resolved.get(source.fallbackBiome);
        }
        double choice = NoiseDefinition.unit(SeedMixer.derive(fields.seed, selectionSalt), x, 0, z) * total;
        for (int i = 0; i < weights.length; i++) {
            choice -= weights[i];
            if (choice < 0) {
                return resolved.get(source.entries.get(i).biome);
            }
        }
        throw new AssertionError("Spatial biome choice exhausted");
    }

    private static BiomeGenBase requiredBiome(WorldGenKey key) {
        BiomeGenBase biome = BiomeGenRegistry.biomeFor(key);
        if (biome == null) {
            throw new IllegalStateException("Profile requires missing biome: " + key);
        }
        return biome;
    }

    @Override
    public BiomeGenBase[] loadBlockGeneratorData(BiomeGenBase[] output, int x, int z, int width, int depth) {
        // Native Beta assumes square climate requests. Sample rows to keep rectangular
        // API queries valid.
        if (width < 1 || depth < 1 || (long) width * depth > 65536) {
            throw new IllegalArgumentException("Biome sampling area exceeds its budget");
        }
        int count = width * depth;
        if (output == null || output.length < count) {
            output = new BiomeGenBase[count];
        }
        temperature = new double[count];
        humidity = new double[count];
        field_4196_c = new double[count];
        if (width == depth) {
            climate.loadBlockGeneratorData(null, x, z, width, depth);
            System.arraycopy(climate.temperature, 0, temperature, 0, count);
            System.arraycopy(climate.humidity, 0, humidity, 0, count);
            System.arraycopy(climate.field_4196_c, 0, field_4196_c, 0, count);
        } else {
            for (int ix = 0; ix < width; ix++) {
                for (int iz = 0; iz < depth; iz++) {
                    climate.loadBlockGeneratorData(null, x + ix, z + iz, 1, 1);
                    int index = ix * depth + iz;
                    temperature[index] = climate.temperature[0];
                    humidity[index] = climate.humidity[0];
                    field_4196_c[index] = climate.field_4196_c[0];
                }
            }
        }
        for (int i = 0; i < count; i++) {
            int t = Math.max(0, Math.min(63, (int) (temperature[i] * 63)));
            int h = Math.max(0, Math.min(63, (int) (humidity[i] * 63)));
            output[i] = source != null && source.type.equals("spatial_climate")
                    ? spatialBiome(x + i / depth, z + i % depth, temperature[i], humidity[i], lookup[t + h * 64])
                    : lookup[t + h * 64];
        }
        field_4195_d = output;
        return output;
    }

    @Override
    public double[] getTemperatures(double[] output, int x, int z, int width, int depth) {
        return climate.getTemperatures(output, x, z, width, depth);
    }

    @Override
    public double getTemperature(int x, int z) {
        return climate.getTemperature(x, z);
    }
}
