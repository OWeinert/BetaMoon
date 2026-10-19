package betamoon.dimension;

import betamoon.worldgen.spatial.GenerationValues;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Canonical scopes. Historical all/both/empty scopes cover only the two native
 * worlds.
 */
public final class DimensionSelector {
    public static final String ANY = "betamoon:any_dimension";

    private DimensionSelector() {
    }

    public static Set<String> parse(Object value, String path) {
        Set<String> result = new LinkedHashSet<String>();
        if (value == null) {
            result.add(DimensionKey.OVERWORLD.toString());
        } else if (value instanceof List) {
            List<Object> entries = GenerationValues.list(value, path, 1, 256);
            for (Object entry : entries) {
                add(result, entry, path);
            }
        } else {
            add(result, value, path);
        }
        return Collections.unmodifiableSet(result);
    }

    private static void add(Set<String> result, Object value, String path) {
        if (value instanceof Map) {
            Map<String, Object> selector = GenerationValues.object(value, path);
            GenerationValues.fields(selector, path, "type");
            if (!"any_dimension".equals(GenerationValues.string(selector.get("type"), path + ".type"))) {
                throw GenerationValues.error(path, "expected type=any_dimension or a dimension key");
            }
            result.add(ANY);
        } else if (value instanceof Number) {
            int nativeId = GenerationValues.integer(value, path, -1, 0);
            result.add(DimensionKey.nativeKey(nativeId).toString());
        } else if ("all".equals(value) || "both".equals(value)) {
            result.add(DimensionKey.OVERWORLD.toString());
            result.add(DimensionKey.NETHER.toString());
        } else {
            result.add(DimensionKey.parse(GenerationValues.string(value, path)).toString());
        }
    }

    public static boolean matches(Set<String> keys, String key) {
        return keys.contains(ANY) || keys.contains(key) || keys.isEmpty()
                && (key.equals(DimensionKey.OVERWORLD.toString()) || key.equals(DimensionKey.NETHER.toString()));
    }
}
