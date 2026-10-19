package betamoon.worldgen.biome;

import betamoon.worldgen.BiomeGenRegistry;
import betamoon.worldgen.WorldGenKey;
import betamoon.worldgen.WorldGenKind;
import betamoon.worldgen.spatial.SpatialRegistry;
import betamoon.worldgen.spatial.SpatialSelector;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static betamoon.worldgen.spatial.GenerationValues.bool;
import static betamoon.worldgen.spatial.GenerationValues.error;
import static betamoon.worldgen.spatial.GenerationValues.fields;
import static betamoon.worldgen.spatial.GenerationValues.integer;
import static betamoon.worldgen.spatial.GenerationValues.list;
import static betamoon.worldgen.spatial.GenerationValues.number;
import static betamoon.worldgen.spatial.GenerationValues.object;
import static betamoon.worldgen.spatial.GenerationValues.required;
import static betamoon.worldgen.spatial.GenerationValues.string;

/**
 * One structural parser for registered and inline biome-source declarations.
 */
public final class BiomeSourceReader {
    private BiomeSourceReader() {
    }

    public static BiomeSourceDefinition read(WorldGenKey key, String owner, String displayOwner, String path,
            Map<String, Object> value, SpatialRegistry.Snapshot spatial, boolean allowActive) {
        fields(value, path, "key", "type", "biome", "entries", "fallback", "active", "priority");
        String type = string(required(value, "type", path), path + ".type").toLowerCase(Locale.ROOT);
        WorldGenKey fixed = null;
        List<BiomeSourceDefinition.ClimateEntry> entries = new ArrayList<BiomeSourceDefinition.ClimateEntry>();
        if (type.equals("fixed")) {
            fields(value, path, "key", "type", "biome", "fallback", "active", "priority");
            fixed = biome(required(value, "biome", path), path);
        } else if (type.equals("vanilla_climate") || type.equals("spatial_climate")) {
            if (value.containsKey("biome")) {
                throw error(path + ".biome", "only fixed sources accept biome");
            }
            for (Object entry : list(required(value, "entries", path), path + ".entries", 1, 256)) {
                Map<String, Object> row = object(entry, path);
                fields(row, path, "biome", "temperature", "humidity", "priority", "spatial", "weight");
                double[] temperature = range(required(row, "temperature", path), path + ".temperature");
                double[] humidity = range(required(row, "humidity", path), path + ".humidity");
                if (row.containsKey("spatial") && !type.equals("spatial_climate")) {
                    throw error(path, "spatial selectors require spatial_climate");
                }
                SpatialSelector selector = row.containsKey("spatial")
                        ? new SpatialSelector(row.get("spatial"), path + ".spatial", spatial)
                        : null;
                double weight = number(row, "weight", 1, path);
                if (weight < 0.000001 || weight > 1024) {
                    throw error(path + ".weight", "expected 0.000001..1024");
                }
                entries.add(new BiomeSourceDefinition.ClimateEntry(biome(required(row, "biome", path), path),
                        temperature[0], temperature[1], humidity[0], humidity[1], priority(row, path), selector,
                        weight));
            }
        } else {
            throw error(path + ".type", "expected fixed, vanilla_climate or spatial_climate");
        }
        boolean active = bool(value, "active", false, path);
        if (active && (!allowActive || type.equals("spatial_climate"))) {
            throw error(path, "this biome source must be selected through a saved generator profile");
        }
        WorldGenKey fallback = !value.containsKey("fallback") || "vanilla".equals(value.get("fallback"))
                ? null
                : biome(value.get("fallback"), path + ".fallback");
        return new BiomeSourceDefinition(key, owner, displayOwner, path, type, fixed, entries, active,
                priority(value, path), fallback);
    }

    private static int priority(Map<String, Object> value, String path) {
        return integer(value.getOrDefault("priority", 0.0), path + ".priority", Integer.MIN_VALUE, Integer.MAX_VALUE);
    }

    private static WorldGenKey biome(Object value, String path) {
        WorldGenKey key = WorldGenKey.parse(string(value, path), WorldGenKind.BIOME);
        if (BiomeGenRegistry.biomeFor(key) == null) {
            throw error(path, "unknown biome " + key);
        }
        return key;
    }

    private static double[] range(Object value, String path) {
        double min;
        double max;
        if (value instanceof List) {
            List<Object> pair = list(value, path, 2, 2);
            min = number(pair.get(0), path);
            max = number(pair.get(1), path);
        } else {
            Map<String, Object> pair = object(value, path);
            fields(pair, path, "min", "max");
            min = number(required(pair, "min", path), path);
            max = number(required(pair, "max", path), path);
        }
        if (min < 0 || max > 1 || min > max) {
            throw error(path, "expected 0 <= min <= max <= 1");
        }
        return new double[]{min, max};
    }
}
