package betamoon.dimension;

import betamoon.assets.AssetKey;
import betamoon.loot.LootTableDefinition;
import betamoon.loot.LootTableRegistry;
import betamoon.worldgen.BiomeGenRegistry;
import betamoon.worldgen.WorldGenRegistry;
import betamoon.worldgen.spatial.SpatialRegistry;
import betamoon.worldgen.terrain.GenerationSignature;
import betamoon.worldgen.terrain.PopulationPolicy;
import betamoon.worldgen.terrain.TerrainRegistry;
import betamoon.worldgen.terrain.WorldGeneratorProfile;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;
import net.minecraft.src.World;

/**
 * Immutable declaration and transitive generation inputs pinned by one
 * publication/session.
 */
public final class DimensionGenerationBinding {
    public final DimensionDefinition definition;
    public final WorldGeneratorProfile profile;
    public final WorldGeneratorProfile runtimeProfile;
    public final PopulationPolicy population;
    public final String nativeBase;
    public final String signature;
    public final Map<String, String> dependencies;
    public final TerrainRegistry.Snapshot terrain;
    public final SpatialRegistry.Snapshot spatial;
    public final Map<AssetKey, LootTableDefinition> loot;
    public final BiomeGenRegistry.WorldCatalog biomes;
    private final Consumer<World> catalogs;

    public DimensionGenerationBinding(DimensionDefinition definition, TerrainRegistry.Snapshot terrain) {
        this.definition = definition;
        this.terrain = terrain;
        WorldGeneratorProfile declared = definition.resolveProfile(terrain);
        if (definition.profileKey != null && declared == null) {
            throw new IllegalArgumentException(definition.key + ": missing generator profile " + definition.profileKey);
        }
        population = declared == null ? definition.resolvePopulation(terrain) : declared.population;
        nativeBase = declared == null ? definition.nativeBase : declared.nativeBase;
        biomes = BiomeGenRegistry.captureDimensionCatalog();
        Map<String, String> content = WorldGenRegistry.dimensionGenerationDependencies(definition.key.toString(),
                population);
        Map<String, String> biomeDependencies = biomes.dependencies(declared == null ? null : declared.biomeSource,
                nativeBase);
        profile = declared == null ? null : declared.forDimension(content, biomeDependencies, biomes);
        Map<String, String> nativeDependencies = new TreeMap<String, String>();
        nativeDependencies.put("native_base", nativeBase);
        runtimeProfile = profile != null
                ? profile
                : population == null
                        ? null
                        : new WorldGeneratorProfile(
                                definition.key.toString().replace(":dimension/", ":generator/dimension/"),
                                definition.owner, nativeDependencies, null, population, null, null, false, true);
        if (!nativeBase.equals("overworld") && population != null && !population.nativeCategories.isEmpty()
                && population.nativeCategories.size() != 9) {
            throw new IllegalArgumentException(definition.key + ": native Hell/Sky population requires all or none");
        }
        spatial = profile == null || profile.spatial == null ? SpatialRegistry.pendingSnapshot() : profile.spatial;
        loot = LootTableRegistry.capturePendingDefinitions();
        catalogs = WorldGenRegistry.captureGenerationCatalogs();
        Map<String, String> inputs = new TreeMap<String, String>();
        inputs.put("dimension declaration " + definition.key, definition.signature(null, population));
        if (profile != null) {
            inputs.putAll(profile.dependencies);
        }
        inputs.putAll(biomeDependencies);
        inputs.putAll(content);
        if (inputs.size() > 4096) {
            throw new IllegalArgumentException(
                    definition.key + ": generation dependencies exceed their 4096-entry budget");
        }
        Map<String, String> hashes = new TreeMap<String, String>();
        for (Map.Entry<String, String> input : inputs.entrySet()) {
            hashes.put(input.getKey(), GenerationSignature.of(Collections.singletonMap("value", input.getValue())));
        }
        dependencies = Collections.unmodifiableMap(hashes);
        signature = GenerationSignature.of(dependencies);
    }

    public void bindCatalogs(World world) {
        catalogs.accept(world);
    }
}
