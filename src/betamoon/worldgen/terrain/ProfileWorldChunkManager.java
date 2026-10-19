package betamoon.worldgen.terrain;

import betamoon.worldgen.BiomeGenRegistry;
import betamoon.worldgen.SeedMixer;
import betamoon.worldgen.WorldGenKey;
import betamoon.worldgen.biome.BiomeDefinition;
import betamoon.worldgen.biome.BiomeSourceDefinition;
import betamoon.worldgen.creation.GenerationBiomes;
import betamoon.worldgen.creation.GenerationSelection;
import betamoon.worldgen.spatial.FieldContext;
import betamoon.worldgen.spatial.NativeClimateSamples;
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
import net.minecraft.src.WorldChunkManagerHell;

/**
 * Profile-bound biome lookup, preserving the native climate samplers and their
 * arrays.
 */
public final class ProfileWorldChunkManager extends WorldChunkManager implements FieldContext.Environment {
    private final GenerationSelection selection;
    private final WorldChunkManager climate;
    private final NativeClimateSamples climateSamples;
    private final BiomeSourceDefinition source;
    private final BiomeGenRegistry.WorldCatalog catalog;
    private final Map<WorldGenKey, BiomeGenBase> resolved = new LinkedHashMap<WorldGenKey, BiomeGenBase>();
    private FieldContext fields;
    private final long selectionSalt;
    private final BiomeGenBase[] lookup;
    private final Map<BiomeGenBase, Set<String>> selectors = new IdentityHashMap<BiomeGenBase, Set<String>>();

    public ProfileWorldChunkManager(WorldChunkManager climate, BiomeSourceDefinition source) {
        this(climate, source, null);
    }

    public ProfileWorldChunkManager(WorldChunkManager climate, BiomeSourceDefinition source,
            GenerationSelection selection) {
        this(climate, source, selection, false, BiomeGenRegistry.captureWorldCatalog());
    }

    public ProfileWorldChunkManager(WorldChunkManager climate, BiomeSourceDefinition source,
            GenerationSelection selection, BiomeGenRegistry.WorldCatalog catalog) {
        this(climate, source, selection, false, catalog);
    }

    public static ProfileWorldChunkManager minecraftFallback(WorldChunkManager climate) {
        return new ProfileWorldChunkManager(climate, null, null, true, BiomeGenRegistry.captureWorldCatalog());
    }

    private ProfileWorldChunkManager(WorldChunkManager climate, BiomeSourceDefinition source,
            GenerationSelection selection, boolean minecraftOnly, BiomeGenRegistry.WorldCatalog catalog) {
        this.selection = selection;
        this.climate = climate;
        climateSamples = new NativeClimateSamples(climate);
        this.source = source;
        this.catalog = catalog;
        selectionSalt = SeedMixer.hash(source == null ? "native" : source.key.toString());
        for (BiomeDefinition definition : catalog.definitions()) {
            Set<String> names = new HashSet<String>();
            names.add(definition.key.toString());
            for (String tag : definition.tags) {
                names.add("#" + tag);
            }
            selectors.put(definition.biome, names);
        }
        if (source == null) {
            lookup = catalog.copyClimate();
            if (climate instanceof WorldChunkManagerHell) {
                Arrays.fill(lookup, climate.getBiomeGenAt(0, 0));
            }
            if (minecraftOnly) {
                for (int t = 0; t < 64; t++) {
                    for (int h = 0; h < 64; h++) {
                        lookup[t + h * 64] = BiomeGenBase.getBiome(t / 63.0f, h / 63.0f);
                    }
                }
            }
            if (selection != null) {
                for (int i = 0; i < lookup.length; i++) {
                    String key = selection.climate.get(i);
                    lookup[i] = selection.enabled("biome/" + key) ? GenerationBiomes.resolve(key) : null;
                }
                filterLookup();
            }
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
            if (selection == null || selection.enabled("biome/" + entry.biome)) {
                resolved.put(entry.biome, requiredBiome(entry.biome));
            }
        }
        for (int t = 0; t < 64; t++) {
            for (int h = 0; h < 64; h++) {
                BiomeGenBase biome = BiomeGenBase.getBiome(t / 63.0f, h / 63.0f);
                if (source.type.equals("fixed")) {
                    biome = resolved.get(source.fixedBiome);
                } else if (!source.type.equals("spatial_climate")) {
                    BiomeSourceDefinition.ClimateEntry selected = null;
                    for (BiomeSourceDefinition.ClimateEntry entry : source.entries) {
                        if (resolved.containsKey(entry.biome) && entry.contains(t / 63.0, h / 63.0) && (selected == null
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
        filterLookup();
    }

    private void filterLookup() {
        if (selection == null) {
            return;
        }
        BiomeGenBase[] eligible = lookup.clone();
        BiomeGenBase fallback = source != null && source.fallbackBiome != null
                ? resolved.get(source.fallbackBiome)
                : null;
        for (int i = 0; i < eligible.length; i++) {
            if (eligible[i] != null && !selection.biomeEnabled(eligible[i])) {
                eligible[i] = null;
            }
        }
        int[] eligibleCells = new int[eligible.length];
        int eligibleCount = 0;
        for (int i = 0; i < eligible.length; i++) {
            if (eligible[i] != null) {
                eligibleCells[eligibleCount++] = i;
            }
        }
        for (int i = 0; i < lookup.length; i++) {
            if (eligible[i] != null) {
                lookup[i] = eligible[i];
                continue;
            }
            BiomeGenBase nearest = fallback;
            int distance = Integer.MAX_VALUE;
            for (int candidateIndex = 0; candidateIndex < eligibleCount; candidateIndex++) {
                int j = eligibleCells[candidateIndex];
                int dt = i % 64 - j % 64;
                int dh = i / 64 - j / 64;
                int candidate = dt * dt + dh * dh;
                if (eligible[j] != null && candidate < distance) {
                    nearest = eligible[j];
                    distance = candidate;
                }
            }
            if (nearest == null) {
                for (String key : selection.enabled) {
                    if (key.startsWith("biome/")) {
                        nearest = GenerationBiomes.resolve(key.substring(6));
                        if (nearest != null) {
                            break;
                        }
                    }
                }
            }
            if (nearest == null) {
                throw new IllegalStateException("Selected biomes cannot cover the climate map");
            }
            lookup[i] = nearest;
        }
    }

    public WorldChunkManager climateManager() {
        return climate;
    }

    public BiomeGenRegistry.WorldCatalog biomeCatalog() {
        return catalog;
    }

    public boolean usesSource(BiomeSourceDefinition definition) {
        return source == definition;
    }

    @Override
    public double biomeValue(String property, int x, int z) {
        return climateSamples.value(property, x, z);
    }

    @Override
    public boolean biomeMatches(String selector, int x, int z) {
        return matchesBiome(getBiomeGenAt(x, z), selector);
    }

    boolean matchesBiome(BiomeGenBase biome, String selector) {
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
            if (!resolved.containsKey(entry.biome) || !entry.contains(temperature, humidity)) {
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

    private BiomeGenBase requiredBiome(WorldGenKey key) {
        BiomeGenBase biome = catalog.biome(key);
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
        if (temperature == null || temperature.length < count) {
            temperature = new double[count];
        }
        if (humidity == null || humidity.length < count) {
            humidity = new double[count];
        }
        if (field_4196_c == null || field_4196_c.length < count) {
            field_4196_c = new double[count];
        }
        if (width == depth) {
            climate.loadBlockGeneratorData(null, x, z, width, depth);
            System.arraycopy(climate.temperature, 0, temperature, 0, count);
            System.arraycopy(climate.humidity, 0, humidity, 0, count);
            if (climate.field_4196_c == null) {
                Arrays.fill(field_4196_c, 0, count, 0);
            } else {
                System.arraycopy(climate.field_4196_c, 0, field_4196_c, 0, count);
            }
        } else {
            for (int ix = 0; ix < width; ix++) {
                for (int iz = 0; iz < depth; iz++) {
                    climate.loadBlockGeneratorData(null, x + ix, z + iz, 1, 1);
                    int index = ix * depth + iz;
                    temperature[index] = climate.temperature[0];
                    humidity[index] = climate.humidity[0];
                    field_4196_c[index] = climate.field_4196_c == null ? 0 : climate.field_4196_c[0];
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
