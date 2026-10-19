package betamoon.worldgen.terrain;

import betamoon.worldgen.BiomeGenRegistry;
import betamoon.worldgen.WorldGenKey;
import betamoon.worldgen.WorldGenKind;
import betamoon.worldgen.biome.BiomeSourceDefinition;
import betamoon.worldgen.spatial.SpatialRegistry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Immutable compiled generation settings and dependency signatures pinned by a
 * world binding.
 */
public final class WorldGeneratorProfile {
    public final String key;
    public final String owner;
    public final String displayName;
    public final String signature;
    public final String creationDefinition;
    public final String nativeBase;
    public final Map<String, String> dependencies;
    public final TerrainSource terrain;
    public final PopulationPolicy population;
    public final BiomeSourceDefinition biomeSource;
    public final SpatialRegistry.Snapshot spatial;
    public final boolean applySurfaces;
    public final boolean requireSafeSpawn;
    public final ProfileSurfaceRules surfaces;
    public final List<CompiledCarver> carvers;
    public final GeneratorSettings settings;

    public WorldGeneratorProfile(String key, String owner, Map<String, String> dependencies) {
        this(key, owner, dependencies, null, null, null, null, true, true);
    }

    public WorldGeneratorProfile(String key, String owner, Map<String, String> dependencies, TerrainSource terrain,
            PopulationPolicy population, BiomeSourceDefinition biomeSource, SpatialRegistry.Snapshot spatial,
            boolean applySurfaces, boolean requireSafeSpawn) {
        this(key, owner, dependencies, terrain, population, biomeSource, spatial, applySurfaces, requireSafeSpawn,
                Collections.<CompiledCarver>emptyList());
    }

    public WorldGeneratorProfile(String key, String owner, Map<String, String> dependencies, TerrainSource terrain,
            PopulationPolicy population, BiomeSourceDefinition biomeSource, SpatialRegistry.Snapshot spatial,
            boolean applySurfaces, boolean requireSafeSpawn, List<CompiledCarver> carvers) {
        this(key, owner, dependencies, terrain, population, biomeSource, spatial, applySurfaces, requireSafeSpawn,
                carvers, null);
    }

    public WorldGeneratorProfile(String key, String owner, Map<String, String> dependencies, TerrainSource terrain,
            PopulationPolicy population, BiomeSourceDefinition biomeSource, SpatialRegistry.Snapshot spatial,
            boolean applySurfaces, boolean requireSafeSpawn, List<CompiledCarver> carvers, GeneratorSettings settings) {
        this(key, owner, dependencies, terrain, population, biomeSource, spatial, applySurfaces, requireSafeSpawn,
                carvers, settings, key);
    }

    public WorldGeneratorProfile(String key, String owner, Map<String, String> dependencies, TerrainSource terrain,
            PopulationPolicy population, BiomeSourceDefinition biomeSource, SpatialRegistry.Snapshot spatial,
            boolean applySurfaces, boolean requireSafeSpawn, List<CompiledCarver> carvers, GeneratorSettings settings,
            String displayName) {
        this(key, owner, dependencies, terrain, population, biomeSource, spatial, applySurfaces, requireSafeSpawn,
                carvers, settings, displayName, dependencies.get("profile_definition"));
    }

    public WorldGeneratorProfile(String key, String owner, Map<String, String> dependencies, TerrainSource terrain,
            PopulationPolicy population, BiomeSourceDefinition biomeSource, SpatialRegistry.Snapshot spatial,
            boolean applySurfaces, boolean requireSafeSpawn, List<CompiledCarver> carvers, GeneratorSettings settings,
            String displayName, String creationDefinition) {
        this.creationDefinition = creationDefinition;
        nativeBase = dependencies.getOrDefault("native_base", "overworld");
        if (!nativeBase.equals("overworld") && population != null && !population.nativeCategories.isEmpty()
                && population.nativeCategories.size() != 9) {
            throw new IllegalArgumentException("Native Hell/Sky generation requires population all or none");
        }
        this.settings = settings;
        if (displayName == null || displayName.isEmpty() || displayName.length() > 256
                || displayName.matches(".*[\\p{Cntrl}\u00a7].*")) {
            throw new IllegalArgumentException("Generator displayName requires 1..256 plain-text characters");
        }
        if (key == null || key.length() > 256) {
            throw new IllegalArgumentException("Invalid generator profile key: " + key);
        }
        if (owner == null || owner.isEmpty() || dependencies == null || dependencies.size() > 16384) {
            throw new IllegalArgumentException("Generator profile requires an owner and bounded dependencies");
        }
        key = WorldGenKey.parse(key, WorldGenKind.GENERATOR).toString();
        TreeMap<String, String> resolved = new TreeMap<String, String>(dependencies);
        resolved.put("profile", key);
        resolved.put("terrain_engine", "1");
        resolved.put("terrain", terrain == null ? "native_beta_1.7.3" : terrain.signature());
        resolved.put("population", population == null ? "native_all" : population.signature);
        this.key = key;
        this.owner = owner;
        this.displayName = displayName;
        this.dependencies = Collections.unmodifiableMap(resolved);
        this.signature = GenerationSignature.of(resolved);
        this.terrain = terrain;
        this.population = population;
        this.biomeSource = biomeSource;
        this.spatial = spatial;
        this.applySurfaces = applySurfaces;
        this.requireSafeSpawn = requireSafeSpawn;
        surfaces = new ProfileSurfaceRules();
        this.carvers = Collections.unmodifiableList(new ArrayList<CompiledCarver>(carvers));
    }

    public WorldGeneratorProfile withPopulationCatalog(Map<String, String> catalog) {
        if (terrain == null) {
            return this;
        }
        String populationSignature = GenerationSignature.of(catalog);
        if (populationSignature.equals(dependencies.get("population_catalog"))) {
            return this;
        }
        return new WorldGeneratorProfile(this, populationSignature);
    }

    public WorldGeneratorProfile forDimension(Map<String, String> populationCatalog, Map<String, String> biomes,
            BiomeGenRegistry.WorldCatalog catalog) {
        Map<String, String> relevant = new TreeMap<String, String>(dependencies);
        relevant.keySet().removeIf(key -> key.contains(":biome/") || key.contains(":surface/"));
        relevant.putAll(biomes);
        relevant.put("population_catalog", GenerationSignature.of(populationCatalog));
        return new WorldGeneratorProfile(this, population, relevant, catalog);
    }

    public WorldGeneratorProfile withCreationSettings(PopulationPolicy policy, Map<String, String> pinnedDependencies) {
        return new WorldGeneratorProfile(this, policy, pinnedDependencies);
    }

    private WorldGeneratorProfile(WorldGeneratorProfile source, PopulationPolicy policy, Map<String, String> pinned) {
        this(source, policy, pinned, null);
    }

    private WorldGeneratorProfile(WorldGeneratorProfile source, PopulationPolicy policy, Map<String, String> pinned,
            BiomeGenRegistry.WorldCatalog catalog) {
        nativeBase = source.nativeBase;
        key = source.key;
        owner = source.owner;
        displayName = source.displayName;
        creationDefinition = source.creationDefinition;
        dependencies = Collections.unmodifiableMap(new TreeMap<String, String>(pinned));
        signature = GenerationSignature.of(dependencies);
        terrain = source.terrain;
        population = policy;
        biomeSource = source.biomeSource;
        spatial = source.spatial;
        applySurfaces = source.applySurfaces;
        requireSafeSpawn = source.requireSafeSpawn;
        surfaces = catalog == null ? source.surfaces : source.surfaces.withCatalog(catalog);
        carvers = source.carvers;
        settings = source.settings;
    }

    private WorldGeneratorProfile(WorldGeneratorProfile source, String populationSignature) {
        nativeBase = source.nativeBase;
        key = source.key;
        owner = source.owner;
        displayName = source.displayName;
        creationDefinition = source.creationDefinition;
        TreeMap<String, String> resolved = new TreeMap<String, String>(source.dependencies);
        resolved.put("population_catalog", populationSignature);
        dependencies = Collections.unmodifiableMap(resolved);
        signature = GenerationSignature.of(resolved);
        terrain = source.terrain;
        population = source.population;
        biomeSource = source.biomeSource;
        spatial = source.spatial;
        applySurfaces = source.applySurfaces;
        requireSafeSpawn = source.requireSafeSpawn;
        surfaces = source.surfaces;
        carvers = source.carvers;
        settings = source.settings;
    }

}
