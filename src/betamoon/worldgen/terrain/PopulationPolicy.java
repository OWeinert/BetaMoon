package betamoon.worldgen.terrain;

import betamoon.worldgen.GenerationStage;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static betamoon.worldgen.spatial.GenerationValues.bool;
import static betamoon.worldgen.spatial.GenerationValues.canonical;
import static betamoon.worldgen.spatial.GenerationValues.error;
import static betamoon.worldgen.spatial.GenerationValues.fields;
import static betamoon.worldgen.spatial.GenerationValues.object;
import static betamoon.worldgen.spatial.GenerationValues.string;

/**
 * Explicit ownership of native population and independently selected BetaMoon
 * stages.
 */
public final class PopulationPolicy {
    public final boolean nativePopulation;
    public final Set<String> nativeCategories;
    public final Set<GenerationStage> stages;
    public final boolean undergroundStructures;
    public final boolean surfaceStructures;
    public final boolean vegetation;
    public final String signature;

    public PopulationPolicy(String key, Map<String, Object> value) {
        fields(value, key, "key", "native", "betamoon");
        Set<String> categories = new LinkedHashSet<String>();
        String[] names = {"lakes", "dungeons", "clay", "ores", "trees", "vegetation", "springs", "snow", "animals"};
        Object nativeValue = value.getOrDefault("native", "all");
        if (nativeValue instanceof Map) {
            Map<String, Object> nativePolicy = object(nativeValue, key + ".native");
            fields(nativePolicy, key + ".native", names);
            for (String name : names) {
                if (bool(nativePolicy, name, true, key + ".native")) {
                    categories.add(name);
                }
            }
        } else {
            String nativeMode = string(nativeValue, key + ".native");
            if (!nativeMode.equals("all") && !nativeMode.equals("none")) {
                throw error(key + ".native", "expected all, none or category flags");
            }
            if (nativeMode.equals("all")) {
                Collections.addAll(categories, names);
            }
        }
        nativePopulation = !categories.isEmpty();
        nativeCategories = Collections.unmodifiableSet(categories);
        Map<String, Object> owned = value.containsKey("betamoon")
                ? object(value.get("betamoon"), key + ".betamoon")
                : Collections.<String, Object>emptyMap();
        fields(owned, key, "undergroundStructures", "undergroundFeatures", "surfaceStructures", "surfaceFeatures",
                "vegetation", "entities", "postProcess");
        EnumSet<GenerationStage> enabled = EnumSet.noneOf(GenerationStage.class);
        if (bool(owned, "undergroundFeatures", true, key)) {
            enabled.add(GenerationStage.UNDERGROUND_FEATURES);
        }
        if (bool(owned, "surfaceFeatures", true, key)) {
            enabled.add(GenerationStage.SURFACE_FEATURES);
        }
        if (bool(owned, "entities", true, key)) {
            enabled.add(GenerationStage.ENTITIES);
        }
        if (bool(owned, "postProcess", true, key)) {
            enabled.add(GenerationStage.POST_PROCESS);
        }
        undergroundStructures = bool(owned, "undergroundStructures", true, key);
        surfaceStructures = bool(owned, "surfaceStructures", true, key);
        vegetation = bool(owned, "vegetation", true, key);
        if (undergroundStructures) {
            enabled.add(GenerationStage.UNDERGROUND_STRUCTURES);
        }
        if (surfaceStructures) {
            enabled.add(GenerationStage.SURFACE_STRUCTURES);
        }
        if (vegetation) {
            enabled.add(GenerationStage.VEGETATION);
        }
        stages = Collections.unmodifiableSet(enabled);
        signature = canonical(value);
    }
}
