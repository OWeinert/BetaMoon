package betamoon.loot;

import betamoon.assets.AssetKey;
import betamoon.assets.model.ModelJson;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** Strict reader shared by JSON assets, Lua data, and inline structure pools. */
final class LootTableReader {
    private static final int MAX_POOLS = 16;
    private static final int MAX_ENTRIES = 64;
    private static final int MAX_ROLLS = 64;

    private LootTableReader() {
    }

    static LootTableDefinition document(Map<String, Object> root, String path) throws IOException {
        fields(root, path, "format", "pools");
        if (!"betamoon_loot_table".equals(ModelJson.name(root.get("format"), path + ".format"))) {
            throw new IOException(path + ".format: expected betamoon_loot_table");
        }
        return pools(root.get("pools"), path + ".pools");
    }

    static LootTableDefinition inline(Object input, String path) throws IOException {
        return pools(input, path);
    }

    private static LootTableDefinition pools(Object input, String path) throws IOException {
        List<Object> values = ModelJson.array(input, path);
        if (values.isEmpty() || values.size() > MAX_POOLS) {
            throw new IOException(path + ": expected 1.." + MAX_POOLS + " pools");
        }
        List<LootTableDefinition.Pool> pools = new ArrayList<LootTableDefinition.Pool>();
        List<String> keys = new ArrayList<String>();
        int totalRolls = 0;
        for (int poolIndex = 0; poolIndex < values.size(); poolIndex++) {
            String poolPath = path + "[" + poolIndex + "]";
            Map<String, Object> value = ModelJson.object(values.get(poolIndex), poolPath);
            fields(value, poolPath, "key", "rolls", "entries");
            String key = identifier(value.get("key"), poolPath + ".key");
            if (keys.contains(key)) {
                throw new IOException(poolPath + ".key: duplicate pool key " + key);
            }
            keys.add(key);
            LootStackDefinition.IntRange rolls = range(value.get("rolls"), poolPath + ".rolls",
                    0, MAX_ROLLS, 1, true);
            totalRolls += rolls.maximum;
            if (totalRolls > MAX_ROLLS) {
                throw new IOException(path + ": total maximum rolls exceed " + MAX_ROLLS);
            }
            List<Object> entryValues = ModelJson.array(value.get("entries"), poolPath + ".entries");
            if (entryValues.isEmpty() || entryValues.size() > MAX_ENTRIES) {
                throw new IOException(poolPath + ".entries: expected 1.." + MAX_ENTRIES + " entries");
            }
            List<LootTableDefinition.Entry> entries = new ArrayList<LootTableDefinition.Entry>();
            int totalWeight = 0;
            for (int entryIndex = 0; entryIndex < entryValues.size(); entryIndex++) {
                String entryPath = poolPath + ".entries[" + entryIndex + "]";
                Map<String, Object> entry = ModelJson.object(entryValues.get(entryIndex), entryPath);
                String type = ModelJson.name(entry.get("type"), entryPath + ".type");
                int weight;
                try {
                    weight = optionalInteger(entry.get("weight"), entryPath + ".weight", 1, 1000000, 1);
                    totalWeight = Math.addExact(totalWeight, weight);
                } catch (ArithmeticException error) {
                    throw new IOException(poolPath + ".entries: total weight is too large");
                }
                if (type.equals("item")) {
                    fields(entry, entryPath, "type", "weight", "stack");
                    entries.add(LootTableDefinition.Entry.item(totalWeight,
                            LootStackDefinition.read(entry.get("stack"), entryPath + ".stack", true)));
                } else if (type.equals("empty")) {
                    fields(entry, entryPath, "type", "weight");
                    entries.add(LootTableDefinition.Entry.empty(totalWeight));
                } else if (type.equals("table")) {
                    fields(entry, entryPath, "type", "weight", "table");
                    entries.add(LootTableDefinition.Entry.table(totalWeight,
                            key(entry.get("table"), entryPath + ".table")));
                } else {
                    throw new IOException(entryPath + ".type: expected item, empty, or table");
                }
            }
            pools.add(new LootTableDefinition.Pool(key, rolls, entries, totalWeight));
        }
        return new LootTableDefinition(pools);
    }

    static LootStackDefinition.IntRange range(Object input, String path, int minimum, int maximum,
            int fallback, boolean allowRange) throws IOException {
        if (input == null) {
            return new LootStackDefinition.IntRange(fallback, fallback);
        }
        if (input instanceof Number) {
            int value = integer(input, path, minimum, maximum);
            return new LootStackDefinition.IntRange(value, value);
        }
        if (!allowRange) {
            throw new IOException(path + ": expected an exact integer");
        }
        Map<String, Object> range = ModelJson.object(input, path);
        fields(range, path, "min", "max");
        int min = integer(range.get("min"), path + ".min", minimum, maximum);
        int max = integer(range.get("max"), path + ".max", min, maximum);
        return new LootStackDefinition.IntRange(min, max);
    }

    static int integer(Object input, String path, int minimum, int maximum) throws IOException {
        double value = ModelJson.number(input, path);
        if (value != Math.rint(value) || value < minimum || value > maximum) {
            throw new IOException(path + ": expected integer from " + minimum + " to " + maximum);
        }
        return (int) value;
    }

    static int optionalInteger(Object input, String path, int minimum, int maximum, int fallback)
            throws IOException {
        return input == null ? fallback : integer(input, path, minimum, maximum);
    }

    static AssetKey key(Object input, String path) throws IOException {
        try {
            return AssetKey.parse(ModelJson.name(input, path));
        } catch (IllegalArgumentException error) {
            throw new IOException(path + ": " + error.getMessage());
        }
    }

    static String identifier(Object input, String path) throws IOException {
        String value = ModelJson.name(input, path);
        if (!value.matches("[a-z][a-z0-9_.-]{0,63}")) {
            throw new IOException(path + ": expected lowercase identifier up to 64 characters");
        }
        return value;
    }

    static void fields(Map<String, Object> value, String path, String... supported) throws IOException {
        List<String> allowed = Arrays.asList(supported);
        for (String field : value.keySet()) {
            if (!allowed.contains(field)) {
                throw new IOException(path + "." + field + ": unsupported field");
            }
        }
    }
}
