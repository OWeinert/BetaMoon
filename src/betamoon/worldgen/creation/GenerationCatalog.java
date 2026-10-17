package betamoon.worldgen.creation;

import betamoon.worldgen.BiomeGenRegistry;
import betamoon.worldgen.GenerationStage;
import betamoon.worldgen.WorldGenRegistry;
import betamoon.worldgen.biome.BiomeDefinition;
import betamoon.worldgen.biome.BiomeSourceDefinition;
import betamoon.worldgen.terrain.PopulationPolicy;
import betamoon.worldgen.terrain.WorldGeneratorProfile;
import java.util.Collections;
import java.util.Arrays;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import net.minecraft.src.BiomeGenBase;

/** Snapshot of selectable generation entry points, never reusable templates. */
public final class GenerationCatalog {
    public static final String MINECRAFT = "minecraft";
    public static final List<String> NATIVE = Collections.unmodifiableList(
            Arrays.asList("lakes", "dungeons", "clay", "ores", "trees", "vegetation", "springs", "snow", "animals"));
    public final WorldGeneratorProfile profile;
    public final String key;
    public final String label;
    public final Map<String, GenerationOption> options;

    public GenerationCatalog(WorldGeneratorProfile profile) {
        this.profile = profile;
        key = profile == null ? MINECRAFT : profile.key;
        label = profile == null ? "Minecraft" : profile.displayName;
        Map<String, GenerationOption> values = new LinkedHashMap<String, GenerationOption>();
        add(values, "group/ores", "Ore generation", "General", true, null);
        add(values, "group/structures", "Structures", "General", true, null);
        PopulationPolicy policy = profile == null ? null : profile.population;
        for (String name : NATIVE) {
            add(values, "native/" + name, "Minecraft " + name, "Minecraft",
                    policy == null || policy.nativeCategories.contains(name), null,
                    name.equals("ores")
                            ? new String[]{"group/ores"}
                            : name.equals("dungeons") ? new String[]{"group/structures"} : new String[0]);
        }
        for (GenerationStage stage : GenerationStage.values()) {
            add(values, "stage/" + stage.getName(), title(stage.getName()), "Stages",
                    policy == null || policy.stages.contains(stage), null);
        }
        Map<String, WorldGenRegistry.FeatureDescription> features = new LinkedHashMap<String, WorldGenRegistry.FeatureDescription>();
        for (WorldGenRegistry.FeatureDescription feature : WorldGenRegistry.featureSnapshot()) {
            features.put(feature.key, feature);
        }
        for (WorldGenRegistry.PlacementDescription placement : WorldGenRegistry.placementSnapshot()) {
            if (placement.template || !placement.dimensions.contains("minecraft:overworld")) {
                continue;
            }
            boolean ore = containsOre(placement.feature, features, new HashSet<String>());
            boolean structure = placement.feature.contains(":structure/");
            boolean allowed = WorldGenRegistry.creationContentAllowed(profile, structure, placement.key,
                    placement.feature);
            String group = ore ? "Ores" : structure ? "Structures" : "Features";
            String[] parents = ore
                    ? new String[]{"stage/" + placement.stage, "group/ores"}
                    : structure
                            ? new String[]{"stage/" + placement.stage, "group/structures"}
                            : new String[]{"stage/" + placement.stage};
            add(values, "placement/" + placement.key, WorldGenRegistry.displayName(placement.key), group,
                    !placement.disabled && allowed,
                    !allowed ? "Excluded by this preset" : placement.disabled ? "Disabled by its script" : null,
                    parents);
        }
        for (WorldGenRegistry.RegionalStructureDescription structure : WorldGenRegistry.regionalStructureSnapshot()) {
            if (!structure.dimensions.contains("minecraft:overworld")) {
                continue;
            }
            boolean allowed = WorldGenRegistry.creationContentAllowed(profile, true, structure.key, structure.start);
            add(values, "regional/" + structure.key, WorldGenRegistry.displayName(structure.key), "Structures",
                    allowed && !structure.disabled,
                    !allowed ? "Excluded by this preset" : structure.disabled ? "Disabled by its script" : null,
                    "group/structures", "stage/" + structure.stage);
        }
        BiomeSourceDefinition source = profile == null ? null : profile.biomeSource;
        Set<String> eligibleBiomes = new HashSet<String>();
        if (source == null) {
            for (BiomeGenBase biome : BiomeGenRegistry.copyClimateLookup()) {
                eligibleBiomes.add(GenerationBiomes.key(biome));
            }
        } else {
            for (int t = 0; t < 64; t++) {
                for (int h = 0; h < 64; h++) {
                    eligibleBiomes.add(GenerationBiomes.key(BiomeGenBase.getBiome(t / 63.0f, h / 63.0f)));
                }
            }
        }
        if (source == null || source.fixedBiome == null && source.fallbackBiome == null) {
            for (Map.Entry<String, BiomeGenBase> entry : GenerationBiomes.nativeBiomes().entrySet()) {
                if (eligibleBiomes.contains(entry.getKey())) {
                    add(values, "biome/" + entry.getKey(), entry.getValue().biomeName, "Biomes", true, null);
                }
            }
        }
        for (BiomeDefinition biome : BiomeGenRegistry.definitions()) {
            boolean used = source == null && eligibleBiomes.contains(biome.key.toString());
            boolean required = source != null
                    && (biome.key.equals(source.fixedBiome) || biome.key.equals(source.fallbackBiome));
            if (source != null) {
                for (BiomeSourceDefinition.ClimateEntry entry : source.entries) {
                    used |= biome.key.equals(entry.biome);
                }
            }
            if (used || required) {
                add(values, "biome/" + biome.key, biome.biome.biomeName, "Biomes", true,
                        required ? "Required biome for this preset" : null);
            }
        }
        if (values.size() > 8192) {
            throw new IllegalStateException("Generation settings exceed the 8192-entry budget");
        }
        options = Collections.unmodifiableMap(values);
    }

    private static boolean containsOre(String key, Map<String, WorldGenRegistry.FeatureDescription> features,
            Set<String> visited) {
        if (!visited.add(key)) {
            return false;
        }
        WorldGenRegistry.FeatureDescription feature = features.get(key);
        if (feature == null) {
            return false;
        }
        if (feature.type.equals("ore_vein")) {
            return true;
        }
        for (String dependency : feature.dependencies) {
            if (containsOre(dependency, features, visited)) {
                return true;
            }
        }
        return false;
    }

    private static void add(Map<String, GenerationOption> values, String key, String label, String category,
            boolean baseline, String lockedReason, String... parents) {
        values.put(key, new GenerationOption(key, label, category, baseline, lockedReason, parents));
    }

    public static String title(String value) {
        String text = value.replace('_', ' ');
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }
}
