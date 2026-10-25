package betamoon.worldgen.terrain;

import betamoon.worldgen.BiomeGenRegistry;
import betamoon.worldgen.WorldGenKey;
import betamoon.worldgen.WorldGenKind;
import betamoon.worldgen.biome.BiomeDefinition;
import betamoon.worldgen.creation.GenerationBiomes;
import betamoon.worldgen.creation.GenerationCatalog;
import betamoon.worldgen.spatial.FieldContext;
import betamoon.worldgen.spatial.SpatialRegistry;
import betamoon.worldgen.spatial.SpatialSelector;
import betamoon.worldgen.surface.SurfaceRuleSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import net.minecraft.src.BiomeGenBase;

import static betamoon.worldgen.spatial.GenerationValues.canonical;
import static betamoon.worldgen.spatial.GenerationValues.error;
import static betamoon.worldgen.spatial.GenerationValues.fields;
import static betamoon.worldgen.spatial.GenerationValues.integer;
import static betamoon.worldgen.spatial.GenerationValues.list;
import static betamoon.worldgen.spatial.GenerationValues.object;
import static betamoon.worldgen.spatial.GenerationValues.required;
import static betamoon.worldgen.spatial.GenerationValues.string;

/** One immutable player-selectable generation package, compiled before publication. */
public final class BiomeRegionOverride {
    public final String key;
    public final String owner;
    public final String displayName;
    public final String signature;
    public final List<String> bases;
    public final int priority;
    public final List<String> before;
    public final List<String> after;
    public final String terrainMode;
    public final TerrainSource terrain;
    public final String carverMode;
    public final List<CompiledCarver> carvers;
    public final String materialMode;
    public final MaterialRule material;
    public final String surfaceMode;
    public final SurfaceRuleSet surface;
    public final Set<String> disabledNative;
    public final Set<String> disabledPlacements;
    public final int blendWidth;
    public final String blendSide;
    private final Set<String> include;
    private final Set<String> exclude;
    private final Set<String> includeTags;
    private final Set<String> excludeTags;
    private final Map<String, Set<String>> biomeTags;
    private final SpatialSelector selector;
    private final int minX;
    private final int maxX;
    private final int minZ;
    private final int maxZ;

    BiomeRegionOverride(String key, String owner, Map<String, Object> value, SpatialRegistry.Snapshot spatial,
            TerrainRegistry.Snapshot definitions) {
        fields(value, key, "key", "displayName", "bases", "biomes", "region", "blend", "priority", "before",
                "after", "terrain", "carvers", "materials", "surfaces", "population");
        this.key = key;
        this.owner = owner;
        displayName = string(value, "displayName", key, key);
        if (displayName.isEmpty() || displayName.length() > 256 || displayName.matches(".*[\\p{Cntrl}\u00a7].*")) {
            throw error(key + ".displayName", "expected 1..256 plain-text characters");
        }
        List<String> selectedBases = new ArrayList<String>();
        for (Object base : list(required(value, "bases", key), key + ".bases", 1, 32)) {
            String name = string(base, key + ".bases");
            if (!name.equals("minecraft")) {
                name = WorldGenKey.parse(name, WorldGenKind.GENERATOR).toString();
            }
            if (selectedBases.contains(name)) {
                throw error(key + ".bases", "duplicate base " + name);
            }
            selectedBases.add(name);
        }
        bases = Collections.unmodifiableList(selectedBases);
        priority = integer(value.getOrDefault("priority", 0.0), key + ".priority", -1000, 1000);
        before = ordering(value.get("before"), key + ".before", key);
        after = ordering(value.get("after"), key + ".after", key);

        Map<String, Object> biomes = value.containsKey("biomes")
                ? object(value.get("biomes"), key + ".biomes") : Collections.emptyMap();
        fields(biomes, key + ".biomes", "include", "exclude", "includeTags", "excludeTags");
        include = selectors(biomes.get("include"), key + ".biomes.include", false);
        exclude = selectors(biomes.get("exclude"), key + ".biomes.exclude", false);
        includeTags = selectors(biomes.get("includeTags"), key + ".biomes.includeTags", true);
        excludeTags = selectors(biomes.get("excludeTags"), key + ".biomes.excludeTags", true);
        Map<String, Set<String>> pinnedTags = new TreeMap<String, Set<String>>();
        for (BiomeDefinition biome : BiomeGenRegistry.definitions()) {
            pinnedTags.put(biome.key.toString(), biome.tags);
        }
        biomeTags = Collections.unmodifiableMap(pinnedTags);

        Map<String, Object> region = value.containsKey("region")
                ? object(value.get("region"), key + ".region") : Collections.emptyMap();
        fields(region, key + ".region", "minX", "maxX", "minZ", "maxZ", "selector");
        minX = integer(region.getOrDefault("minX", -30000000.0), key + ".region.minX", -30000000, 30000000);
        maxX = integer(region.getOrDefault("maxX", 30000000.0), key + ".region.maxX", minX, 30000000);
        minZ = integer(region.getOrDefault("minZ", -30000000.0), key + ".region.minZ", -30000000, 30000000);
        maxZ = integer(region.getOrDefault("maxZ", 30000000.0), key + ".region.maxZ", minZ, 30000000);
        selector = region.containsKey("selector")
                ? new SpatialSelector(region.get("selector"), key + ".region.selector", spatial) : null;
        if (selector != null && selector.requiresOriginalSpawn()) {
            throw error(key + ".region.selector", "terrain overrides cannot depend on original spawn");
        }

        Map<String, Object> blend = value.containsKey("blend")
                ? object(value.get("blend"), key + ".blend") : Collections.emptyMap();
        fields(blend, key + ".blend", "width", "side");
        blendWidth = integer(blend.getOrDefault("width", 0.0), key + ".blend.width", 0, 256);
        blendSide = string(blend, "side", "inside", key + ".blend");
        if (!blendSide.equals("inside") && !blendSide.equals("both")) {
            throw error(key + ".blend.side", "expected inside or both");
        }

        Map<String, Object> terrainChoice = value.containsKey("terrain")
                ? object(value.get("terrain"), key + ".terrain") : Collections.emptyMap();
        fields(terrainChoice, key + ".terrain", "mode", "source");
        terrainMode = string(terrainChoice, "mode", "inherit", key + ".terrain");
        if (!terrainMode.equals("inherit") && !terrainMode.equals("replace")) {
            throw error(key + ".terrain.mode", "expected inherit or replace");
        }
        terrain = terrainMode.equals("replace")
                ? definitions.source(string(required(terrainChoice, "source", key), key + ".terrain.source"))
                : null;
        if (terrain != null && terrain.isNative()) {
            throw error(key + ".terrain.source", "replacement requires an owned terrain source");
        }
        if (terrainMode.equals("inherit") && terrainChoice.containsKey("source")) {
            throw error(key + ".terrain", "inherited terrain cannot name a replacement source");
        }

        Map<String, Object> carverChoice = value.containsKey("carvers")
                ? object(value.get("carvers"), key + ".carvers") : Collections.emptyMap();
        fields(carverChoice, key + ".carvers", "mode", "entries");
        carverMode = string(carverChoice, "mode", "inherit", key + ".carvers");
        if (!carverMode.equals("inherit") && !carverMode.equals("add") && !carverMode.equals("replace")
                && !carverMode.equals("none")) {
            throw error(key + ".carvers.mode", "expected inherit, add, replace or none");
        }
        List<CompiledCarver> selectedCarvers = new ArrayList<CompiledCarver>();
        if (carverChoice.containsKey("entries")) {
            for (Object entry : list(carverChoice.get("entries"), key + ".carvers.entries", 0, 32)) {
                selectedCarvers.add(definitions.carver(string(entry, key + ".carvers.entries")));
            }
        }
        if ((carverMode.equals("inherit") || carverMode.equals("none")) && !selectedCarvers.isEmpty()
                || (carverMode.equals("add") || carverMode.equals("replace")) && selectedCarvers.isEmpty()) {
            throw error(key + ".carvers", "entries must match the selected carver mode");
        }
        long work = 0;
        for (CompiledCarver carver : selectedCarvers) {
            work += carver.maximumMaskWork();
        }
        if (work > 64000000) {
            throw error(key + ".carvers", "combined carvers exceed the work budget");
        }
        carvers = Collections.unmodifiableList(selectedCarvers);

        Map<String, Object> materialChoice = value.containsKey("materials")
                ? object(value.get("materials"), key + ".materials") : Collections.emptyMap();
        fields(materialChoice, key + ".materials", "mode", "rule");
        materialMode = string(materialChoice, "mode", "inherit", key + ".materials");
        if (!materialMode.equals("inherit") && !materialMode.equals("add") && !materialMode.equals("replace")) {
            throw error(key + ".materials.mode", "expected inherit, add or replace");
        }
        material = materialMode.equals("inherit") ? null
                : MaterialRule.compile(required(materialChoice, "rule", key + ".materials"),
                        key + ".materials.rule", definitions, spatial, 0);
        if (material == null && materialChoice.containsKey("rule")) {
            throw error(key + ".materials", "inherited materials cannot name a rule");
        }
        if (material != null && (material.requiresEnvelope || material.sampleWork > 256)) {
            throw error(key + ".materials", "override material rule requires an unavailable envelope or exceeds work");
        }

        Map<String, Object> surfaceChoice = value.containsKey("surfaces")
                ? object(value.get("surfaces"), key + ".surfaces") : Collections.emptyMap();
        fields(surfaceChoice, key + ".surfaces", "mode", "surface");
        surfaceMode = string(surfaceChoice, "mode", "inherit", key + ".surfaces");
        if (!surfaceMode.equals("inherit") && !surfaceMode.equals("replace") && !surfaceMode.equals("none")) {
            throw error(key + ".surfaces.mode", "expected inherit, replace or none");
        }
        surface = surfaceMode.equals("replace")
                ? BiomeGenRegistry.surfaceFor(WorldGenKey.parse(
                        string(required(surfaceChoice, "surface", key + ".surfaces"), key + ".surfaces.surface"),
                        WorldGenKind.SURFACE))
                : null;
        if (surfaceMode.equals("replace") && surface == null) {
            throw error(key + ".surfaces.surface", "unknown surface rule set");
        }
        if (!surfaceMode.equals("replace") && surfaceChoice.containsKey("surface")) {
            throw error(key + ".surfaces", "only replacement surfaces can name a rule set");
        }
        if (surface != null) {
            for (SurfaceRuleSet.Layer layer : surface.layers) {
                ChunkBlockBuffer.validateState(layer.blockId, 0);
            }
            if (surface.underwaterBlock != null) {
                ChunkBlockBuffer.validateState(surface.underwaterBlock, 0);
            }
        }
        Map<String, Object> population = value.containsKey("population")
                ? object(value.get("population"), key + ".population") : Collections.emptyMap();
        fields(population, key + ".population", "disableNative", "disablePlacements");
        Set<String> nativeNames = new TreeSet<String>();
        if (population.containsKey("disableNative")) {
            for (Object entry : list(population.get("disableNative"), key + ".population.disableNative", 0, 9)) {
                String name = string(entry, key + ".population.disableNative");
                if (!GenerationCatalog.NATIVE.contains(name) || name.equals("animals")
                        || !nativeNames.add(name)) {
                    throw error(key + ".population.disableNative", "unknown, duplicate or unscoped native category");
                }
            }
        }
        disabledNative = Collections.unmodifiableSet(nativeNames);
        Set<String> placementNames = new TreeSet<String>();
        if (population.containsKey("disablePlacements")) {
            for (Object entry : list(population.get("disablePlacements"), key + ".population.disablePlacements",
                    0, 256)) {
                String name = string(entry, key + ".population.disablePlacements");
                if (!name.matches("[a-z0-9_.-]+:(placement|structure)/[a-z0-9_./-]+")
                        || !placementNames.add(name)) {
                    throw error(key + ".population.disablePlacements", "invalid or duplicate placement key");
                }
            }
        }
        disabledPlacements = Collections.unmodifiableSet(placementNames);
        if (terrain == null && carverMode.equals("inherit") && material == null
                && surfaceMode.equals("inherit") && disabledNative.isEmpty() && disabledPlacements.isEmpty()) {
            throw error(key, "override requires a generation channel");
        }
        Map<String, String> dependencies = new TreeMap<String, String>();
        Map<String, Object> generationValue = new TreeMap<String, Object>(value);
        generationValue.remove("displayName");
        dependencies.put("declaration", canonical(generationValue));
        dependencies.put("blend_algorithm", "cardinal_distance_v1");
        dependencies.put("transition_algorithm", "four_by_four_column_cells_v1");
        dependencies.putAll(definitions.dependenciesFor(value));
        if (selector != null) {
            dependencies.put("region_selector", selector.signature);
        }
        if (terrain != null) {
            dependencies.put("terrain", terrain.signature());
        }
        for (int index = 0; index < carvers.size(); index++) {
            dependencies.put("carver_" + index, carvers.get(index).signature);
        }
        if (surface != null) {
            dependencies.put("surface", surface.key + ":" + surface.source + ":" + surface.seaLevel
                    + ":" + Arrays.toString(surface.replace.values()) + ":" + surface.underwaterBlock);
            for (int index = 0; index < surface.layers.size(); index++) {
                SurfaceRuleSet.Layer layer = surface.layers.get(index);
                dependencies.put("surface_layer_" + index,
                        layer.blockId + ":" + layer.depth.min + ":" + layer.depth.max);
            }
        }
        signature = GenerationSignature.of(dependencies);
    }

    private static Set<String> selectors(Object value, String path, boolean tags) {
        Set<String> selected = new TreeSet<String>();
        if (value == null) {
            return Collections.unmodifiableSet(selected);
        }
        for (Object entry : list(value, path, 0, 256)) {
            String name = string(entry, path);
            if (tags) {
                if (name.isEmpty() || name.length() > 128) {
                    throw error(path, "invalid biome tag");
                }
            } else if (!name.contains(":")) {
                String matched = null;
                for (Map.Entry<String, BiomeGenBase> biome : GenerationBiomes.nativeBiomes().entrySet()) {
                    if (biome.getValue().biomeName.equalsIgnoreCase(name)
                            || biome.getKey().substring(biome.getKey().lastIndexOf('/') + 1)
                                    .equalsIgnoreCase(name.replace(' ', '_'))) {
                        matched = biome.getKey();
                        break;
                    }
                }
                for (BiomeDefinition biome : BiomeGenRegistry.definitions()) {
                    if (biome.biome.biomeName.equalsIgnoreCase(name)) {
                        if (matched != null) {
                            throw error(path, "ambiguous biome display name " + name + "; use a canonical key");
                        }
                        matched = biome.key.toString();
                    }
                }
                if (matched == null) {
                    throw error(path, "unknown biome " + name);
                }
                name = matched;
            } else {
                name = WorldGenKey.parse(name, WorldGenKind.BIOME).toString();
                if (GenerationBiomes.resolve(name) == null) {
                    throw error(path, "unknown biome " + name);
                }
            }
            if (!selected.add(name)) {
                throw error(path, "duplicate biome selector " + name);
            }
        }
        return Collections.unmodifiableSet(selected);
    }

    private static List<String> ordering(Object value, String path, String self) {
        List<String> result = new ArrayList<String>();
        if (value == null) {
            return Collections.unmodifiableList(result);
        }
        for (Object entry : list(value, path, 0, 256)) {
            String key = WorldGenKey.parse(string(entry, path), WorldGenKind.TERRAIN_OVERRIDE).toString();
            if (key.equals(self) || result.contains(key)) {
                throw error(path, "self or duplicate ordering dependency");
            }
            result.add(key);
        }
        return Collections.unmodifiableList(result);
    }

    public boolean compatible(String base) {
        return bases.contains(base);
    }

    public List<String> channels() {
        List<String> result = new ArrayList<String>();
        if (terrain != null) {
            result.add("terrain");
        }
        if (material != null) {
            result.add("materials");
        }
        if (!carverMode.equals("inherit")) {
            result.add("carvers");
        }
        if (!surfaceMode.equals("inherit")) {
            result.add("surfaces");
        }
        if (!disabledNative.isEmpty() || !disabledPlacements.isEmpty()) {
            result.add("population");
        }
        return Collections.unmodifiableList(result);
    }

    public boolean mayOverlap(BiomeRegionOverride other) {
        int reach = blendSide.equals("both") ? blendWidth : 0;
        int otherReach = other.blendSide.equals("both") ? other.blendWidth : 0;
        if (maxX + (long) reach < other.minX - (long) otherReach
                || other.maxX + (long) otherReach < minX - (long) reach
                || maxZ + (long) reach < other.minZ - (long) otherReach
                || other.maxZ + (long) otherReach < minZ - (long) reach) {
            return false;
        }
        if (!include.isEmpty() && includeTags.isEmpty() && !other.include.isEmpty()
                && other.includeTags.isEmpty()) {
            for (String biome : include) {
                if (other.include.contains(biome) && !exclude.contains(biome) && !other.exclude.contains(biome)) {
                    return true;
                }
            }
            return false;
        }
        return true;
    }

    public boolean matches(FieldContext context, int x, int z) {
        if (x < minX || x > maxX || z < minZ || z > maxZ
                || selector != null && selector.weight(context, x, z) <= 0) {
            return false;
        }
        if (context.environment == null) {
            throw new IllegalStateException("Terrain override requires the saved biome environment");
        }
        BiomeGenBase biome = context.environment.biome(x, z);
        String biomeKey = GenerationBiomes.key(biome);
        Set<String> tags = biomeTags.getOrDefault(biomeKey, Collections.emptySet());
        boolean included = include.isEmpty() && includeTags.isEmpty() || include.contains(biomeKey);
        for (String tag : includeTags) {
            included |= tags.contains(tag);
        }
        if (!included || exclude.contains(biomeKey)) {
            return false;
        }
        for (String tag : excludeTags) {
            if (tags.contains(tag)) {
                return false;
            }
        }
        return true;
    }

    public double influence(FieldContext context, int x, int z) {
        boolean inside = matches(context, x, z);
        if (blendWidth == 0) {
            return inside ? 1 : 0;
        }
        if (!inside && blendSide.equals("inside")) {
            return 0;
        }
        int distance = blendWidth + 1;
        int[][] directions = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] direction : directions) {
            for (int step = 1; step <= blendWidth; step++) {
                long sampleX = (long) x + direction[0] * step;
                long sampleZ = (long) z + direction[1] * step;
                if (sampleX < -30000000 || sampleX > 30000000 || sampleZ < -30000000
                        || sampleZ > 30000000 || matches(context, (int) sampleX, (int) sampleZ) != inside) {
                    distance = Math.min(distance, step);
                    break;
                }
            }
        }
        if (distance > blendWidth) {
            return inside ? 1 : 0;
        }
        return blendSide.equals("inside") ? inside ? (double) distance / blendWidth : 0
                : inside ? 0.5 + 0.5 * distance / blendWidth : 0.5 - 0.5 * distance / blendWidth;
    }
}
