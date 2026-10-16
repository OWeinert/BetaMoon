package betamoon.worldgen.terrain;

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
    public final boolean defaultForNewWorlds;
    public final int priority;
    public final String signature;
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

    public WorldGeneratorProfile(String key, String owner, boolean defaultForNewWorlds, int priority,
            Map<String, String> dependencies) {
        this(key, owner, defaultForNewWorlds, priority, dependencies, null, null, null, null, true, true);
    }

    public WorldGeneratorProfile(String key, String owner, boolean defaultForNewWorlds, int priority,
            Map<String, String> dependencies, TerrainSource terrain, PopulationPolicy population,
            BiomeSourceDefinition biomeSource, SpatialRegistry.Snapshot spatial, boolean applySurfaces,
            boolean requireSafeSpawn) {
        this(key, owner, defaultForNewWorlds, priority, dependencies, terrain, population, biomeSource, spatial,
                applySurfaces, requireSafeSpawn, Collections.<CompiledCarver>emptyList());
    }

    public WorldGeneratorProfile(String key, String owner, boolean defaultForNewWorlds, int priority,
            Map<String, String> dependencies, TerrainSource terrain, PopulationPolicy population,
            BiomeSourceDefinition biomeSource, SpatialRegistry.Snapshot spatial, boolean applySurfaces,
            boolean requireSafeSpawn, List<CompiledCarver> carvers) {
        this(key, owner, defaultForNewWorlds, priority, dependencies, terrain, population, biomeSource, spatial,
                applySurfaces, requireSafeSpawn, carvers, null);
    }

    public WorldGeneratorProfile(String key, String owner, boolean defaultForNewWorlds, int priority,
            Map<String, String> dependencies, TerrainSource terrain, PopulationPolicy population,
            BiomeSourceDefinition biomeSource, SpatialRegistry.Snapshot spatial, boolean applySurfaces,
            boolean requireSafeSpawn, List<CompiledCarver> carvers, GeneratorSettings settings) {
        this.settings = settings;
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
        this.defaultForNewWorlds = defaultForNewWorlds;
        this.priority = priority;
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

    private WorldGeneratorProfile(WorldGeneratorProfile source, String populationSignature) {
        key = source.key;
        owner = source.owner;
        defaultForNewWorlds = source.defaultForNewWorlds;
        priority = source.priority;
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
