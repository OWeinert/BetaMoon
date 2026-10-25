package betamoon.worldgen.creation;

import betamoon.worldgen.BiomeGenRegistry;
import betamoon.worldgen.GenerationStage;
import betamoon.worldgen.WorldGenRegistry;
import betamoon.worldgen.biome.BiomeDefinition;
import betamoon.worldgen.spatial.SpatialRegistry;
import betamoon.worldgen.terrain.TerrainRegistry;
import betamoon.worldgen.terrain.BiomeRegionOverride;
import betamoon.worldgen.terrain.GenerationSignature;
import betamoon.worldgen.terrain.GeneratorProfileRegistry;
import betamoon.worldgen.terrain.PopulationPolicy;
import betamoon.worldgen.terrain.WorldGeneratorProfile;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import net.minecraft.src.BiomeGenBase;

/** Resolved per-world choices. Unknown new entry points remain disabled. */
public final class GenerationSelection {
    public final long seed;
    public final String preset;
    public final Map<String, Boolean> choices;
    public final Map<String, List<String>> parents;
    public final Set<String> enabled;
    public final Map<String, String> dependencies;
    public final Map<String, String> profileDependencies;
    public final List<String> climate;
    public final List<String> overrideOrder;

    public GenerationSelection(long seed, String preset, Map<String, Boolean> choices,
            Map<String, List<String>> parents, Map<String, String> dependencies,
            Map<String, String> profileDependencies, List<String> climate) {
        this.seed = seed;
        this.preset = preset;
        this.choices = Collections.unmodifiableMap(new TreeMap<String, Boolean>(choices));
        Map<String, List<String>> copied = new TreeMap<String, List<String>>();
        for (Map.Entry<String, List<String>> entry : parents.entrySet()) {
            copied.put(entry.getKey(), Collections.unmodifiableList(new ArrayList<String>(entry.getValue())));
        }
        this.parents = Collections.unmodifiableMap(copied);
        this.dependencies = Collections.unmodifiableMap(new TreeMap<String, String>(dependencies));
        this.profileDependencies = Collections.unmodifiableMap(new TreeMap<String, String>(profileDependencies));
        this.climate = Collections.unmodifiableList(new ArrayList<String>(climate));
        if (choices.size() > 8192 || dependencies.size() > 16384 || profileDependencies.size() > 16384
                || !choices.keySet().equals(parents.keySet()) || climate.size() != 4096) {
            throw new IllegalArgumentException("Invalid generation settings catalog");
        }
        Set<String> resolved = new LinkedHashSet<String>();
        Map<String, Boolean> memo = new LinkedHashMap<String, Boolean>();
        for (String key : choices.keySet()) {
            if (resolve(key, new LinkedHashSet<String>(), memo)) {
                resolved.add(key);
            }
        }
        enabled = Collections.unmodifiableSet(resolved);
        List<String> order = new ArrayList<String>();
        for (String entry : enabled) {
            if (entry.startsWith("override/")) {
                order.add(entry.substring("override/".length()));
            }
        }
        overrideOrder = Collections.unmodifiableList(sortOverrides(order));
        validateBiomes();
    }

    private static List<String> sortOverrides(List<String> keys) {
        Map<String, Set<String>> following = new TreeMap<String, Set<String>>();
        for (String key : keys) {
            following.put(key, new TreeSet<String>());
        }
        for (String key : keys) {
            BiomeRegionOverride override = TerrainRegistry.snapshot().overrideOrNull(key);
            if (override == null) {
                continue;
            }
            for (String later : override.before) {
                if (following.containsKey(later)) {
                    following.get(key).add(later);
                }
            }
            for (String earlier : override.after) {
                if (following.containsKey(earlier)) {
                    following.get(earlier).add(key);
                }
            }
        }
        for (int first = 0; first < keys.size(); first++) {
            BiomeRegionOverride left = TerrainRegistry.snapshot().overrideOrNull(keys.get(first));
            if (left == null) {
                continue;
            }
            for (int second = first + 1; second < keys.size(); second++) {
                BiomeRegionOverride right = TerrainRegistry.snapshot().overrideOrNull(keys.get(second));
                if (right == null || left.priority != right.priority || !left.mayOverlap(right)) {
                    continue;
                }
                boolean terrainConflict = left.terrain != null && right.terrain != null;
                boolean carverConflict = !left.carverMode.equals("inherit")
                        && !right.carverMode.equals("inherit")
                        && (!left.carverMode.equals("add") || !right.carverMode.equals("add"));
                boolean materialConflict = left.material != null && right.material != null;
                boolean terrainMaterialConflict = left.terrain != null && right.material != null
                        || right.terrain != null && left.material != null;
                boolean surfaceConflict = !left.surfaceMode.equals("inherit") && !right.surfaceMode.equals("inherit");
                if ((terrainConflict || carverConflict || materialConflict || terrainMaterialConflict
                        || surfaceConflict)
                        && !reachable(following, left.key, right.key, new TreeSet<String>())
                        && !reachable(following, right.key, left.key, new TreeSet<String>())) {
                    throw new IllegalArgumentException("Overlapping replacement overrides require distinct priority "
                            + "or before/after ordering: " + left.key + " and " + right.key);
                }
            }
        }
        List<String> sorted = new ArrayList<String>();
        Set<String> remaining = new TreeSet<String>(keys);
        while (!remaining.isEmpty()) {
            String next = null;
            for (String candidate : remaining) {
                boolean incoming = false;
                for (String other : remaining) {
                    incoming |= following.get(other).contains(candidate);
                }
                if (!incoming && (next == null || compareOverrides(candidate, next) < 0)) {
                    next = candidate;
                }
            }
            if (next == null) {
                throw new IllegalArgumentException("Cyclic terrain override ordering: " + remaining);
            }
            sorted.add(next);
            remaining.remove(next);
        }
        return sorted;
    }

    private static boolean reachable(Map<String, Set<String>> graph, String from, String to, Set<String> visited) {
        if (!visited.add(from)) {
            return false;
        }
        for (String next : graph.get(from)) {
            if (next.equals(to) || reachable(graph, next, to, visited)) {
                return true;
            }
        }
        return false;
    }

    private static int compareOverrides(String left, String right) {
        BiomeRegionOverride first = TerrainRegistry.snapshot().overrideOrNull(left);
        BiomeRegionOverride second = TerrainRegistry.snapshot().overrideOrNull(right);
        int leftPriority = first == null ? 0 : first.priority;
        int rightPriority = second == null ? 0 : second.priority;
        int compared = Integer.compare(leftPriority, rightPriority);
        return compared == 0 ? left.compareTo(right) : compared;
    }

    private boolean resolve(String key, Set<String> visiting, Map<String, Boolean> memo) {
        if (memo.containsKey(key)) {
            return memo.get(key);
        }
        if (!choices.containsKey(key) || !visiting.add(key) || visiting.size() > 8) {
            throw new IllegalArgumentException("Missing or cyclic generation setting parent: " + key);
        }
        boolean selected = choices.get(key);
        for (String parent : parents.get(key)) {
            selected &= resolve(parent, visiting, memo);
        }
        visiting.remove(key);
        memo.put(key, selected);
        return selected;
    }

    public static GenerationSelection create(GenerationDraft draft, long seed) {
        Map<String, List<String>> parents = new TreeMap<String, List<String>>();
        for (GenerationOption option : draft.catalog.options.values()) {
            parents.put(option.key, option.parents);
        }
        List<String> climate = draft.catalog.climate;
        Map<String, String> profile = new TreeMap<String, String>();
        if (draft.catalog.profile != null) {
            profile.putAll(draft.catalog.profile.dependencies);
            profile.remove("population");
            if (draft.catalog.profile.creationDefinition != null) {
                profile.put("profile_definition", draft.catalog.profile.creationDefinition);
            }
            profile.remove("population_catalog");
            for (String biomeDependency : BiomeGenRegistry.generationDependencies().keySet()) {
                profile.remove(biomeDependency);
            }
        }
        GenerationSelection initial = new GenerationSelection(seed, draft.catalog.key, draft.choices(), parents,
                Collections.<String, String>emptyMap(), profile, climate);
        return new GenerationSelection(seed, initial.preset, initial.choices, parents, initial.currentDependencies(),
                profile, climate);
    }

    public boolean enabled(String key) {
        return enabled.contains(key);
    }

    public boolean contentEnabled(String key) {
        return enabled("placement/" + key) || enabled("regional/" + key);
    }

    public boolean biomeEnabled(BiomeGenBase biome) {
        return enabled("biome/" + GenerationBiomes.key(biome));
    }

    private void validateBiomes() {
        boolean any = false;
        for (String key : enabled) {
            any |= key.startsWith("biome/");
        }
        if (!any) {
            throw new IllegalArgumentException("Enable at least one biome before creating this world");
        }
    }

    public List<String> missingRequirements() {
        Set<String> missing = new TreeSet<String>();
        if (!preset.equals(GenerationCatalog.MINECRAFT) && GeneratorProfileRegistry.snapshot().get(preset) == null) {
            missing.add(preset);
        }
        Map<String, String> current = currentDependencies();
        Set<String> available = new LinkedHashSet<String>();
        for (Map<String, Object> definition : SpatialRegistry.describeDefinitions()) {
            available.add((String) definition.get("key"));
        }
        for (Map<String, Object> definition : TerrainRegistry.describeDefinitions()) {
            available.add((String) definition.get("key"));
        }
        for (String key : profileDependencies.keySet()) {
            if (!key.startsWith("minecraft:")
                    && key.matches("[^:]+:(noise|field|anchor|terrain|terrain_source|material|carver|population)/.+")
                    && !available.contains(key)) {
                missing.add(key);
            }
        }
        for (String key : dependencies.keySet()) {
            if (!current.containsKey(key)) {
                String name = key;
                for (String prefix : new String[]{"entry:", "ore:", "regional:", "decorator_source:",
                        "placement_spatial:", "structure_content:"}) {
                    if (name.startsWith(prefix)) {
                        name = name.substring(prefix.length());
                        break;
                    }
                }
                missing.add(name);
            }
        }
        for (String key : enabled) {
            if (key.startsWith("biome/") && GenerationBiomes.resolve(key.substring(6)) == null) {
                missing.add(key.substring(6));
            } else if (key.startsWith("override/") && !TerrainRegistry.snapshot().hasOverride(key.substring(9))) {
                missing.add(key.substring(9));
            }
        }
        return new ArrayList<String>(missing);
    }

    public void validate(long worldSeed) {
        if (seed != worldSeed) {
            throw new IllegalStateException("Generation settings belong to a different world seed");
        }
        WorldGeneratorProfile profile = GeneratorProfileRegistry.snapshot().get(preset);
        if (!preset.equals(GenerationCatalog.MINECRAFT) && profile == null) {
            throw new IllegalStateException("This world requires missing generation preset " + preset);
        }
        if (profile != null) {
            int nativeCategories = population().nativeCategories.size();
            if (!profile.nativeBase.equals("overworld") && nativeCategories != 0
                    && nativeCategories != GenerationCatalog.NATIVE.size()) {
                throw new IllegalStateException("Saved settings select unsupported granular Minecraft population for "
                        + profile.nativeBase + "; restore a compatible settings backup");
            }
            Map<String, String> currentProfile = new TreeMap<String, String>(profile.dependencies);
            if (profile.creationDefinition != null) {
                currentProfile.put("profile_definition", profile.creationDefinition);
            }
            requireDependencies(profileDependencies, currentProfile);
        }
        requireDependencies(dependencies, currentDependencies());
        for (String key : enabled) {
            if (key.startsWith("biome/") && GenerationBiomes.resolve(key.substring(6)) == null) {
                throw new IllegalStateException("This world requires missing biome " + key.substring(6));
            } else if (key.startsWith("override/")) {
                BiomeRegionOverride override = TerrainRegistry.snapshot().overrideOrNull(key.substring(9));
                if (override == null || !override.compatible(preset)) {
                    throw new IllegalStateException("This world requires missing or incompatible terrain override "
                            + key.substring(9));
                }
            }
        }
    }

    private static void requireDependencies(Map<String, String> saved, Map<String, String> current) {
        for (Map.Entry<String, String> entry : saved.entrySet()) {
            if (!entry.getValue().equals(current.get(entry.getKey()))) {
                throw new IllegalStateException("World generation dependency changed or is missing: " + entry.getKey());
            }
        }
    }

    private Map<String, String> currentDependencies() {
        Map<String, String> current = new TreeMap<String, String>(WorldGenRegistry.generationDependencies());
        current.putAll(BiomeGenRegistry.generationDependencies());
        Map<String, WorldGenRegistry.PlacementDescription> placements = new LinkedHashMap<String, WorldGenRegistry.PlacementDescription>();
        for (WorldGenRegistry.PlacementDescription entry : WorldGenRegistry.placementSnapshot()) {
            placements.put(entry.key, entry);
        }
        Map<String, WorldGenRegistry.RegionalStructureDescription> structures = new LinkedHashMap<String, WorldGenRegistry.RegionalStructureDescription>();
        for (WorldGenRegistry.RegionalStructureDescription entry : WorldGenRegistry.regionalStructureSnapshot()) {
            structures.put(entry.key, entry);
        }
        Map<String, BiomeDefinition> biomes = new LinkedHashMap<String, BiomeDefinition>();
        for (BiomeDefinition biome : BiomeGenRegistry.definitions()) {
            biomes.put(biome.key.toString(), biome);
        }
        List<WorldGenRegistry.FeatureDescription> features = WorldGenRegistry.featureSnapshot();
        Set<String> keys = new LinkedHashSet<String>();
        for (String key : enabled) {
            if (key.startsWith("placement/")) {
                String placement = key.substring(10);
                keys.add(placement);
                keys.add("placement_spatial:" + placement);
                keys.add("ore:" + placement);
                WorldGenRegistry.PlacementDescription entry = placements.get(placement);
                if (entry != null) {
                    keys.add(entry.feature);
                }
            } else if (key.startsWith("regional/")) {
                keys.add(key.substring(9));
                keys.add("regional:" + key.substring(9));
                WorldGenRegistry.RegionalStructureDescription entry = structures.get(key.substring(9));
                if (entry != null) {
                    keys.addAll(entry.dependencies);
                }
            } else if (key.startsWith("biome/")) {
                String biomeKey = key.substring(6);
                keys.add(biomeKey);
                BiomeDefinition biome = biomes.get(biomeKey);
                if (biome != null && biome.surface != null) {
                    keys.add(biome.surface.toString());
                }
            } else if (key.startsWith("override/")) {
                BiomeRegionOverride override = TerrainRegistry.snapshot().overrideOrNull(key.substring(9));
                if (override != null) {
                    keys.add("terrain_override:" + override.key);
                }
            }
        }
        boolean expanded;
        do {
            expanded = false;
            for (Map.Entry<String, String> dependency : current.entrySet()) {
                if (dependency.getKey().startsWith("decorator_source:")
                        && keys.contains(dependency.getKey().substring(17))) {
                    expanded |= keys.add(dependency.getKey());
                    expanded |= keys.add(dependency.getValue());
                }
            }
            for (WorldGenRegistry.FeatureDescription feature : features) {
                if (keys.contains(feature.key)) {
                    expanded |= keys.addAll(feature.dependencies);
                }
            }
        } while (expanded);
        Map<String, String> result = new TreeMap<String, String>();
        for (Map.Entry<String, String> entry : current.entrySet()) {
            String key = entry.getKey();
            if (keys.contains(key) || key.startsWith("structure_content:") && keys.contains(key.substring(18))) {
                String signature = entry.getValue();
                if (key.startsWith("ore:")) {
                    signature = signature.substring(signature.indexOf(':') + 1);
                }
                result.put(key, signature);
            }
        }
        for (String key : overrideOrder) {
            BiomeRegionOverride override = TerrainRegistry.snapshot().overrideOrNull(key);
            if (override != null) {
                result.put("terrain_override:" + key, override.signature);
            }
        }
        result.put("terrain_override_order", GenerationSignature.of(overrideSignatures()));
        // Track existence even for declarations registered by legacy facades.
        for (WorldGenRegistry.PlacementDescription entry : WorldGenRegistry.placementSnapshot()) {
            if (enabled("placement/" + entry.key)) {
                result.put("entry:" + entry.key, entry.feature + ":" + entry.stage);
            }
        }
        for (WorldGenRegistry.RegionalStructureDescription entry : WorldGenRegistry.regionalStructureSnapshot()) {
            if (enabled("regional/" + entry.key)) {
                result.put("entry:" + entry.key, entry.start + ":" + entry.stage);
            }
        }
        return result;
    }

    private Map<String, String> overrideSignatures() {
        Map<String, String> signatures = new LinkedHashMap<String, String>();
        for (int index = 0; index < overrideOrder.size(); index++) {
            signatures.put(Integer.toString(index), overrideOrder.get(index));
        }
        return signatures;
    }

    public PopulationPolicy population() {
        Map<String, Object> policy = new LinkedHashMap<String, Object>();
        Map<String, Object> nativeFlags = new LinkedHashMap<String, Object>();
        for (String name : GenerationCatalog.NATIVE) {
            nativeFlags.put(name, enabled("native/" + name));
        }
        Object nativeSelection = nativeFlags;
        if (choices.containsKey("native/all")) {
            nativeSelection = enabled("native/all") ? "all" : "none";
        }
        policy.put("native", nativeSelection);
        Map<String, Object> owned = new LinkedHashMap<String, Object>();
        String[] fields = {"undergroundStructures", "undergroundFeatures", "surfaceStructures", "surfaceFeatures",
                "vegetation", "entities", "postProcess"};
        GenerationStage[] stages = GenerationStage.values();
        for (int i = 0; i < fields.length; i++) {
            owned.put(fields[i], enabled("stage/" + stages[i].getName()));
        }
        policy.put("betamoon", owned);
        return new PopulationPolicy(preset, policy);
    }

    public WorldGeneratorProfile profile() {
        WorldGeneratorProfile profile = GeneratorProfileRegistry.snapshot().get(preset);
        if (profile == null) {
            profile = new WorldGeneratorProfile("betamoon:generator/minecraft", "betamoon",
                    Collections.<String, String>emptyMap());
        }
        Map<String, String> pinned = new TreeMap<String, String>(profileDependencies);
        pinned.putAll(dependencies);
        pinned.put("creation_settings", GenerationSignature.of(stringChoices()));
        return profile.withCreationSettings(population(), pinned);
    }

    private Map<String, String> stringChoices() {
        Map<String, String> result = new TreeMap<String, String>();
        for (Map.Entry<String, Boolean> choice : choices.entrySet()) {
            result.put(choice.getKey(), choice.getValue().toString());
        }
        return result;
    }
}
