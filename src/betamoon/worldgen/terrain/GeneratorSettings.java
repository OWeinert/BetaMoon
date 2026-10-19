package betamoon.worldgen.terrain;

import betamoon.worldgen.TerrainSurface;
import betamoon.worldgen.spatial.ScalarField;
import betamoon.worldgen.spatial.SpatialRegistry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static betamoon.worldgen.spatial.GenerationValues.error;
import static betamoon.worldgen.spatial.GenerationValues.fields;
import static betamoon.worldgen.spatial.GenerationValues.list;
import static betamoon.worldgen.spatial.GenerationValues.object;
import static betamoon.worldgen.spatial.GenerationValues.string;

/** Authored height outputs and independent owned-content selection. */
public final class GeneratorSettings {
    public final Map<String, ScalarField> heightOutputs;
    public final TerrainSurface placementSurface;
    public final Selection features;
    public final Selection structures;

    public GeneratorSettings(Map<String, Object> dimension, SpatialRegistry.Snapshot spatial, String path) {
        Map<String, ScalarField> outputs = new LinkedHashMap<String, ScalarField>();
        if (dimension.containsKey("heightOutputs")) {
            Map<String, Object> declarations = object(dimension.get("heightOutputs"), path + ".heightOutputs");
            if (declarations.size() > 32) {
                throw error(path, "at most 32 authored height outputs are supported");
            }
            for (Map.Entry<String, Object> entry : declarations.entrySet()) {
                if (!entry.getKey().matches("[a-zA-Z][a-zA-Z0-9_]{0,63}")) {
                    throw error(path, "invalid authored height output name");
                }
                ScalarField field = spatial.field(string(entry.getValue(), path + ".heightOutputs"));
                if ((field.dependencies & 2) != 0) {
                    throw error(path, "authored height outputs must not depend on Y");
                }
                outputs.put(entry.getKey(), field);
            }
        }
        heightOutputs = Collections.unmodifiableMap(outputs);
        placementSurface = TerrainSurface.parse(string(dimension, "placementHeightmap", "world_surface", path));
        if (placementSurface == TerrainSurface.EXACT) {
            throw error(path + ".placementHeightmap", "expected a derived surface map");
        }
        features = new Selection(dimension.get("features"), path + ".features");
        structures = new Selection(dimension.get("structures"), path + ".structures");
    }

    public static final class Selection {
        private final Set<String> include;
        private final Set<String> exclude;
        private final Set<String> includeTags;
        private final Set<String> excludeTags;

        private Selection(Object declaration, String path) {
            Map<String, Object> value = declaration == null
                    ? Collections.<String, Object>emptyMap()
                    : object(declaration, path);
            fields(value, path, "include", "exclude", "includeTags", "excludeTags");
            include = names(value.get("include"), path);
            exclude = names(value.get("exclude"), path);
            includeTags = names(value.get("includeTags"), path);
            excludeTags = names(value.get("excludeTags"), path);
        }

        public boolean matches(String key, String secondaryKey, Set<String> tags) {
            if (exclude.contains(key) || exclude.contains(secondaryKey) || intersects(tags, excludeTags)) {
                return false;
            }
            return include.isEmpty() && includeTags.isEmpty() || include.contains(key) || include.contains(secondaryKey)
                    || intersects(tags, includeTags);
        }

        public Map<String, Object> describe() {
            Map<String, Object> result = new LinkedHashMap<String, Object>();
            result.put("include", new ArrayList<String>(include));
            result.put("exclude", new ArrayList<String>(exclude));
            result.put("includeTags", new ArrayList<String>(includeTags));
            result.put("excludeTags", new ArrayList<String>(excludeTags));
            return Collections.unmodifiableMap(result);
        }

        private static boolean intersects(Set<String> first, Set<String> second) {
            for (String entry : first) {
                if (second.contains(entry)) {
                    return true;
                }
            }
            return false;
        }

        private static Set<String> names(Object value, String path) {
            Set<String> result = new TreeSet<String>();
            if (value != null) {
                List<Object> values = list(value, path, 0, 256);
                for (Object name : values) {
                    result.add(string(name, path));
                }
            }
            return Collections.unmodifiableSet(result);
        }
    }
}
