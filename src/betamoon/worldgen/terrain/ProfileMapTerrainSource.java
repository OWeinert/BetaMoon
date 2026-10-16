package betamoon.worldgen.terrain;

import betamoon.worldgen.SeedMixer;
import betamoon.worldgen.spatial.FieldContext;
import betamoon.worldgen.spatial.NoiseDefinition;
import betamoon.worldgen.spatial.SpatialRegistry;
import betamoon.worldgen.spatial.SpatialSelector;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static betamoon.worldgen.spatial.GenerationValues.canonical;
import static betamoon.worldgen.spatial.GenerationValues.error;
import static betamoon.worldgen.spatial.GenerationValues.fields;
import static betamoon.worldgen.spatial.GenerationValues.integer;
import static betamoon.worldgen.spatial.GenerationValues.list;
import static betamoon.worldgen.spatial.GenerationValues.number;
import static betamoon.worldgen.spatial.GenerationValues.object;
import static betamoon.worldgen.spatial.GenerationValues.required;
import static betamoon.worldgen.spatial.GenerationValues.string;

/**
 * Positional profile selection with continuous, chunk-independent envelope
 * blending.
 */
public strictfp final class ProfileMapTerrainSource implements TerrainSource {
    private final List<Entry> entries = new ArrayList<Entry>();
    private final HeightEnvelope fallback;
    private final TerrainSource nativeEnvelope;
    private final CompiledTerrainSource terrain;
    private final int blendWidth;
    private final long salt;
    private final String signature;

    public ProfileMapTerrainSource(String key, Map<String, Object> value, SpatialRegistry.Snapshot spatial,
            MaterialRule.Resolver materials, Resolver profiles, Map<String, String> dependencies) {
        fields(value, key, "key", "type", "entries", "fallback", "blendWidth", "minY", "maxY", "seaLevel", "solid",
                "material", "fluid", "air", "floorHeight", "floorMaterial");
        Map<String, String> semantics = new TreeMap<String, String>(dependencies);
        semantics.put("source", canonical(value));
        salt = SeedMixer.hash(key);
        blendWidth = integer(value.getOrDefault("blendWidth", 64.0), key + ".blendWidth", 0, 256);
        Object fallbackValue = value.getOrDefault("fallback", "vanilla");
        fallback = "vanilla".equals(fallbackValue)
                ? null
                : fallbackValue instanceof String
                        ? profiles.profile((String) fallbackValue)
                        : profiles.inlineProfile(key + "/fallback", object(fallbackValue, key + ".fallback"));
        nativeEnvelope = fallback == null
                ? new CompiledTerrainSource(key + "/native_envelope",
                        Collections.<String, Object>singletonMap("type", "beta_compatible"), spatial, materials,
                        dependencies)
                : null;
        semantics.put("fallback", fallback == null ? nativeEnvelope.signature() : fallback.signature);
        for (Object declaration : list(required(value, "entries", key), key + ".entries", 1, 64)) {
            Map<String, Object> entry = object(declaration, key + ".entries");
            fields(entry, key, "profile", "biomes", "spatial", "weight");
            Object profileValue = required(entry, "profile", key);
            HeightEnvelope profile = profileValue instanceof String
                    ? profiles.profile((String) profileValue)
                    : profiles.inlineProfile(key + "/profile_" + entries.size(),
                            object(profileValue, key + ".profile"));
            SpatialSelector selector = entry.containsKey("spatial")
                    ? new SpatialSelector(entry.get("spatial"), key + ".spatial", spatial)
                    : null;
            double weight = number(entry, "weight", 1, key);
            if (weight <= 0 || weight > 1024) {
                throw error(key + ".weight", "expected (0,1024]");
            }
            List<String> include = new ArrayList<String>();
            List<String> exclude = new ArrayList<String>();
            if (entry.containsKey("biomes")) {
                Map<String, Object> biomes = object(entry.get("biomes"), key + ".biomes");
                fields(biomes, key, "include", "exclude", "includeTags", "excludeTags");
                selectors(biomes.get("include"), include, false, key);
                selectors(biomes.get("includeTags"), include, true, key);
                selectors(biomes.get("exclude"), exclude, false, key);
                selectors(biomes.get("excludeTags"), exclude, true, key);
            }
            entries.add(new Entry(profile, selector, weight, include, exclude));
            semantics.put("profile_" + entries.size(), profile.signature);
            if (selector != null) {
                semantics.put("selector_" + entries.size(), selector.signature);
            }
        }
        Map<String, Object> fill = new LinkedHashMap<String, Object>(value);
        fill.remove("entries");
        fill.remove("fallback");
        fill.remove("blendWidth");
        fill.put("type", "heightmap");
        fill.putIfAbsent("seaLevel", 63.0);
        terrain = new CompiledTerrainSource(key, fill, spatial, materials, semantics, this::height);
        signature = terrain.signature();
    }

    @Override
    public boolean usesHeightProfile(FieldContext context, int x, int z, String key) {
        for (Map.Entry<HeightEnvelope, Double> entry : weights(context, x, z).entrySet()) {
            if (entry.getKey() != null && entry.getKey().key.equals(key) && entry.getValue() > 0) {
                return true;
            }
        }
        return false;
    }

    @Override
    public double height(FieldContext context, int x, int z) {
        double result = 0;
        for (Map.Entry<HeightEnvelope, Double> entry : weights(context, x, z).entrySet()) {
            double height = entry.getKey() == null
                    ? nativeEnvelope.height(context, x, z)
                    : entry.getKey().height(context, x, z);
            result += height * entry.getValue();
        }
        return result;
    }

    public Map<HeightEnvelope, Double> weights(FieldContext context, int x, int z) {
        Map<HeightEnvelope, Double> result = new LinkedHashMap<HeightEnvelope, Double>();
        if (blendWidth == 0) {
            result.put(select(context, x, z), 1.0);
            return result;
        }
        int gx = Math.floorDiv(x, blendWidth) * blendWidth;
        int gz = Math.floorDiv(z, blendWidth) * blendWidth;
        double tx = (x - gx) / (double) blendWidth;
        double tz = (z - gz) / (double) blendWidth;
        tx = tx * tx * (3 - 2 * tx);
        tz = tz * tz * (3 - 2 * tz);
        for (int dx = 0; dx < 2; dx++) {
            for (int dz = 0; dz < 2; dz++) {
                double weight = (dx == 0 ? 1 - tx : tx) * (dz == 0 ? 1 - tz : tz);
                if (weight > 0) {
                    HeightEnvelope selected = select(context, gx + dx * blendWidth, gz + dz * blendWidth);
                    result.put(selected, result.getOrDefault(selected, 0.0) + weight);
                }
            }
        }
        return result;
    }

    private HeightEnvelope select(FieldContext context, int x, int z) {
        double total = 0;
        double[] weights = new double[entries.size()];
        for (int i = 0; i < entries.size(); i++) {
            Entry entry = entries.get(i);
            if (entry.matches(context, x, z)) {
                weights[i] = entry.weight * (entry.spatial == null ? 1 : entry.spatial.weight(context, x, z));
                total += weights[i];
            }
        }
        if (total == 0) {
            return fallback;
        }
        double selected = NoiseDefinition.unit(SeedMixer.derive(context.seed, salt), x, 0, z) * total;
        for (int i = 0; i < entries.size(); i++) {
            selected -= weights[i];
            if (selected < 0) {
                return entries.get(i).profile;
            }
        }
        throw new AssertionError("Profile selection exhausted");
    }

    private static void selectors(Object value, List<String> result, boolean tags, String path) {
        if (value == null) {
            return;
        }
        for (Object entry : list(value, path, 0, 64)) {
            result.add((tags ? "#" : "") + string(entry, path));
        }
    }

    @Override
    public void fill(GenerationContext context, ChunkBlockBuffer buffer) {
        terrain.fill(context, buffer);
    }

    @Override
    public boolean isNative() {
        return false;
    }

    @Override
    public String signature() {
        return signature;
    }

    public interface Resolver {
        HeightEnvelope profile(String key);

        default HeightEnvelope inlineProfile(String path, Map<String, Object> value) {
            throw error(path, "inline height-profile resolution is unavailable");
        }
    }

    private static final class Entry {
        private final HeightEnvelope profile;
        private final SpatialSelector spatial;
        private final double weight;
        private final List<String> include;
        private final List<String> exclude;

        private Entry(HeightEnvelope profile, SpatialSelector spatial, double weight, List<String> include,
                List<String> exclude) {
            this.profile = profile;
            this.spatial = spatial;
            this.weight = weight;
            this.include = include;
            this.exclude = exclude;
        }

        private boolean matches(FieldContext context, int x, int z) {
            if (context.environment == null) {
                return include.isEmpty() && exclude.isEmpty();
            }
            for (String selector : exclude) {
                if (context.environment.biomeMatches(selector, x, z)) {
                    return false;
                }
            }
            if (include.isEmpty()) {
                return true;
            }
            for (String selector : include) {
                if (context.environment.biomeMatches(selector, x, z)) {
                    return true;
                }
            }
            return false;
        }
    }
}
