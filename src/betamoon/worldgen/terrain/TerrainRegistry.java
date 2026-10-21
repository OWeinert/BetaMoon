package betamoon.worldgen.terrain;

import betamoon.worldgen.BiomeGenRegistry;
import betamoon.worldgen.GenerationStage;
import betamoon.worldgen.WorldGenKey;
import betamoon.worldgen.WorldGenKind;
import betamoon.worldgen.WorldGenRegistry;
import betamoon.worldgen.biome.BiomeSourceDefinition;
import betamoon.worldgen.biome.BiomeSourceReader;
import betamoon.worldgen.spatial.GenerationDocuments;
import betamoon.worldgen.spatial.SpatialRegistry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static betamoon.worldgen.spatial.GenerationValues.bool;
import static betamoon.worldgen.spatial.GenerationValues.canonical;
import static betamoon.worldgen.spatial.GenerationValues.error;
import static betamoon.worldgen.spatial.GenerationValues.fields;
import static betamoon.worldgen.spatial.GenerationValues.freeze;
import static betamoon.worldgen.spatial.GenerationValues.integer;
import static betamoon.worldgen.spatial.GenerationValues.list;
import static betamoon.worldgen.spatial.GenerationValues.object;
import static betamoon.worldgen.spatial.GenerationValues.required;
import static betamoon.worldgen.spatial.GenerationValues.string;

/** Structural terrain declarations compile before atomic owner publication. */
public final class TerrainRegistry {
    private static final ThreadLocal<Batch> CURRENT = new ThreadLocal<Batch>();
    private static volatile Snapshot active = new Snapshot(Collections.<String, Declaration>emptyMap(),
            SpatialRegistry.snapshot());

    private TerrainRegistry() {
    }

    public static Batch begin(String owner) {
        if (CURRENT.get() != null) {
            throw new IllegalStateException("Nested terrain publication is unsupported");
        }
        Batch batch = new Batch(owner);
        CURRENT.set(batch);
        return batch;
    }

    public static String add(WorldGenKind kind, Map<String, Object> document) {
        Batch batch = CURRENT.get();
        if (batch == null) {
            throw error("worldgen", "terrain definitions belong in modInit/modReload");
        }
        document = GenerationDocuments.resolve(batch.owner, document);
        String key = WorldGenKey.parse(string(required(document, "key", "terrain"), "terrain.key"), kind).toString();
        if (key.startsWith("minecraft:") || key.startsWith("betamoon:")) {
            throw error(key, "reserved namespace");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> frozen = (Map<String, Object>) freeze(document);
        if (batch.values.put(key, new Declaration(key, batch.owner, kind, frozen)) != null
                || batch.values.size() > 256) {
            throw error(key, "duplicate definition or owner declaration budget exceeded");
        }
        batch.resolved = null;
        return key;
    }

    public static boolean exists(WorldGenKind kind, String key) {
        String typed = WorldGenKey.parse(key, kind).toString();
        Batch batch = CURRENT.get();
        return batch != null && batch.values.containsKey(typed) || active.values.containsKey(typed);
    }

    public static Snapshot snapshot() {
        return active;
    }

    public static Snapshot pendingSnapshot() {
        Batch pending = CURRENT.get();
        if (pending == null) {
            return active;
        }
        if (pending.resolved == null) {
            pending.validate();
        }
        return pending.resolved;
    }

    public static List<Map<String, Object>> describeDefinitions() {
        Snapshot snapshot = active;
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        for (Declaration declaration : snapshot.values.values()) {
            Map<String, Object> row = new TreeMap<String, Object>();
            row.put("key", declaration.key);
            row.put("owner", declaration.owner);
            row.put("kind", declaration.kind.getPath());
            row.put("type", declaration.value.getOrDefault("type", declaration.kind.getPath()));
            if (declaration.kind == WorldGenKind.GENERATOR) {
                WorldGeneratorProfile profile = snapshot.generator(declaration.key);
                row.put("signature", profile.signature);
                row.put("displayName", profile.displayName);
                List<String> stages = new ArrayList<String>();
                if (profile.population != null) {
                    for (GenerationStage stage : profile.population.stages) {
                        stages.add(stage.getName());
                    }
                }
                row.put("populationStages", stages);
            } else if (declaration.kind == WorldGenKind.TERRAIN_SOURCE) {
                row.put("signature", snapshot.source(declaration.key).signature());
            } else if (declaration.kind == WorldGenKind.CARVER) {
                row.put("signature", snapshot.carver(declaration.key).signature);
                row.put("maximumMaskWork", snapshot.carver(declaration.key).maximumMaskWork());
            } else if (declaration.kind == WorldGenKind.TERRAIN) {
                row.put("signature", snapshot.profile(declaration.key).signature);
            } else if (declaration.kind == WorldGenKind.TERRAIN_OVERRIDE) {
                BiomeRegionOverride override = snapshot.override(declaration.key);
                row.put("signature", override.signature);
                row.put("displayName", override.displayName);
                row.put("bases", override.bases);
                row.put("channels", override.channels());
            }
            result.add(Collections.unmodifiableMap(row));
        }
        return Collections.unmodifiableList(result);
    }

    public static synchronized void retainOwners(Set<String> owners) {
        Map<String, Declaration> retained = new TreeMap<String, Declaration>();
        Set<String> removed = new LinkedHashSet<String>();
        for (Declaration declaration : active.values.values()) {
            if (owners.contains(declaration.owner)) {
                retained.put(declaration.key, declaration);
            } else {
                removed.add(declaration.owner);
            }
        }
        Snapshot next;
        while (true) {
            try {
                next = new Snapshot(retained, SpatialRegistry.snapshot());
                break;
            } catch (CompilationFailure failure) {
                removed.add(failure.owner);
                retained.values().removeIf(declaration -> declaration.owner.equals(failure.owner));
            }
        }
        Set<String> replaced = profileOwners(active, next, null);
        replaced.addAll(removed);
        GeneratorProfileRegistry.replaceOwners(replaced, next.profiles.values());
        active = next;
    }

    private static Set<String> profileOwners(Snapshot first, Snapshot second, String owner) {
        Set<String> result = new LinkedHashSet<String>();
        if (owner != null) {
            result.add(owner);
        }
        for (WorldGeneratorProfile profile : first.profiles.values()) {
            result.add(profile.owner);
        }
        for (WorldGeneratorProfile profile : second.profiles.values()) {
            result.add(profile.owner);
        }
        return result;
    }

    public static final class Batch implements AutoCloseable {
        private final String owner;
        private final Map<String, Declaration> values = new LinkedHashMap<String, Declaration>();
        private Snapshot resolved;

        private Batch(String owner) {
            this.owner = owner;
        }

        public void validate() {
            Map<String, Declaration> combined = new TreeMap<String, Declaration>();
            for (Declaration declaration : active.values.values()) {
                if (!declaration.owner.equals(owner)) {
                    combined.put(declaration.key, declaration);
                }
            }
            for (Declaration declaration : values.values()) {
                if (combined.put(declaration.key, declaration) != null) {
                    throw error(declaration.key, "terrain key belongs to another script");
                }
            }
            resolved = new Snapshot(combined, SpatialRegistry.pendingSnapshot());
            GeneratorProfileRegistry.validateOwners(profileOwners(active, resolved, owner), resolved.profiles.values());
        }

        public void publish() {
            if (resolved == null) {
                validate();
            }
            GeneratorProfileRegistry.replaceOwners(profileOwners(active, resolved, owner), resolved.profiles.values());
            active = resolved;
        }

        @Override
        public void close() {
            CURRENT.remove();
        }
    }

    public static final class Snapshot
            implements
                MaterialRule.Resolver,
                CompiledCarver.Resolver,
                ProfileMapTerrainSource.Resolver,
                ComposedTerrainSource.Resolver {
        private final Map<String, Declaration> values;
        private final Map<String, HeightEnvelope> heightProfiles = new LinkedHashMap<String, HeightEnvelope>();
        private final Map<String, TerrainSource> sources = new LinkedHashMap<String, TerrainSource>();
        private final Map<String, MaterialRule> materials = new LinkedHashMap<String, MaterialRule>();
        private int inlineDepth;
        private final Map<String, CompiledCarver> carvers = new LinkedHashMap<String, CompiledCarver>();
        private final Map<String, PopulationPolicy> populations = new LinkedHashMap<String, PopulationPolicy>();
        private final Map<String, WorldGeneratorProfile> profiles = new LinkedHashMap<String, WorldGeneratorProfile>();
        private final Map<String, BiomeRegionOverride> overrides = new LinkedHashMap<String, BiomeRegionOverride>();
        private final Set<String> resolving = new LinkedHashSet<String>();
        private final SpatialRegistry.Snapshot spatial;

        private Snapshot(Map<String, Declaration> values, SpatialRegistry.Snapshot spatial) {
            this.values = Collections.unmodifiableMap(new TreeMap<String, Declaration>(values));
            this.spatial = spatial;
            if (values.size() > 4096) {
                throw error("worldgen", "terrain declaration limit exceeded");
            }
            for (Declaration declaration : values.values()) {
                try {
                    switch (declaration.kind) {
                        case TERRAIN:
                            profile(declaration.key);
                            break;
                        case MATERIAL:
                            material(declaration.key);
                            break;
                        case TERRAIN_SOURCE:
                            source(declaration.key);
                            break;
                        case POPULATION:
                            population(declaration.key);
                            break;
                        case GENERATOR:
                            profile(declaration);
                            break;
                        case CARVER:
                            carver(declaration.key);
                            break;
                        case TERRAIN_OVERRIDE:
                            override(declaration.key);
                            break;
                        default:
                            throw error(declaration.key, "unsupported terrain declaration kind");
                    }
                } catch (IllegalArgumentException exception) {
                    throw new CompilationFailure(declaration.owner, exception);
                }
            }
            for (BiomeRegionOverride override : overrides.values()) {
                for (String base : override.bases) {
                    if (!base.equals("minecraft") && !profiles.containsKey(base)) {
                        throw new CompilationFailure(override.owner,
                                error(override.key + ".bases", "unknown generator profile " + base));
                    }
                    if (!base.equals("minecraft") && !profiles.get(base).nativeBase.equals("overworld")) {
                        throw new CompilationFailure(override.owner,
                                error(override.key + ".bases", "override requires an Overworld generator base"));
                    }
                }
                for (String reference : override.before) {
                    if (!overrides.containsKey(reference)) {
                        throw new CompilationFailure(override.owner,
                                error(override.key + ".before", "unknown terrain override " + reference));
                    }
                }
                for (String reference : override.after) {
                    if (!overrides.containsKey(reference)) {
                        throw new CompilationFailure(override.owner,
                                error(override.key + ".after", "unknown terrain override " + reference));
                    }
                }
            }
            if (overrides.size() > 256) {
                throw error("worldgen.terrain.overrides", "override package limit is 256");
            }
        }

        @Override
        public HeightEnvelope profile(String key) {
            key = WorldGenKey.parse(key, WorldGenKind.TERRAIN).toString();
            HeightEnvelope existing = heightProfiles.get(key);
            if (existing != null) {
                return existing;
            }
            Declaration declaration = enter(key, WorldGenKind.TERRAIN);
            try {
                HeightEnvelope result = new HeightEnvelope(key, declaration.value, spatial);
                heightProfiles.put(key, result);
                return result;
            } finally {
                resolving.remove(key);
            }
        }

        @Override
        public CompiledCarver carver(String key) {
            key = WorldGenKey.parse(key, WorldGenKind.CARVER).toString();
            CompiledCarver existing = carvers.get(key);
            if (existing != null) {
                return existing;
            }
            Declaration declaration = enter(key, WorldGenKind.CARVER);
            try {
                CompiledCarver result = new CompiledCarver(key, declaration.value, spatial, this,
                        dependencies(declaration.value, new LinkedHashSet<String>()));
                carvers.put(key, result);
                return result;
            } finally {
                resolving.remove(key);
            }
        }

        @Override
        public CompiledCarver inlineCarver(String path, Map<String, Object> value) {
            enterInline(path);
            try {
                return new CompiledCarver(path, value, spatial, this, dependencies(value, new LinkedHashSet<String>()));
            } finally {
                inlineDepth--;
            }
        }

        @Override
        public TerrainSource inlineSource(String path, Map<String, Object> value) {
            enterInline(path);
            try {
                return compileSource(path, value);
            } finally {
                inlineDepth--;
            }
        }

        @Override
        public HeightEnvelope inlineProfile(String path, Map<String, Object> value) {
            return new HeightEnvelope(path, value, spatial);
        }

        private void enterInline(String path) {
            if (inlineDepth >= 16) {
                throw error(path, "inline generation composition exceeds depth 16");
            }
            inlineDepth++;
        }

        private Declaration enter(String key, WorldGenKind kind) {
            Declaration declaration = values.get(key);
            if (declaration == null || declaration.kind != kind) {
                throw error(key, "missing generation dependency");
            }
            if (!resolving.add(key) || resolving.size() > 32) {
                throw error(key, "cyclic or excessively deep generation references: " + resolving);
            }
            return declaration;
        }

        @Override
        public MaterialRule material(String key) {
            key = WorldGenKey.parse(key, WorldGenKind.MATERIAL).toString();
            MaterialRule existing = materials.get(key);
            if (existing != null) {
                return existing;
            }
            Declaration declaration = enter(key, WorldGenKind.MATERIAL);
            try {
                fields(declaration.value, key, "key", "rule");
                MaterialRule result = MaterialRule.compile(required(declaration.value, "rule", key), key + ".rule",
                        this, spatial, 0);
                materials.put(key, result);
                return result;
            } finally {
                resolving.remove(key);
            }
        }

        public TerrainSource source(String key) {
            key = WorldGenKey.parse(key, WorldGenKind.TERRAIN_SOURCE).toString();
            TerrainSource existing = sources.get(key);
            if (existing != null) {
                return existing;
            }
            Declaration declaration = enter(key, WorldGenKind.TERRAIN_SOURCE);
            try {
                TerrainSource source = compileSource(key, declaration.value);
                sources.put(key, source);
                return source;
            } finally {
                resolving.remove(key);
            }
        }

        public WorldGeneratorProfile generator(String key) {
            return profiles.get(WorldGenKey.parse(key, WorldGenKind.GENERATOR).toString());
        }

        public BiomeRegionOverride override(String key) {
            key = WorldGenKey.parse(key, WorldGenKind.TERRAIN_OVERRIDE).toString();
            BiomeRegionOverride existing = overrides.get(key);
            if (existing != null) {
                return existing;
            }
            Declaration declaration = enter(key, WorldGenKind.TERRAIN_OVERRIDE);
            try {
                BiomeRegionOverride compiled = new BiomeRegionOverride(key, declaration.owner, declaration.value,
                        spatial, this);
                overrides.put(key, compiled);
                return compiled;
            } finally {
                resolving.remove(key);
            }
        }

        public BiomeRegionOverride overrideOrNull(String key) {
            return overrides.get(key);
        }

        public boolean hasOverride(String key) {
            return overrides.containsKey(key);
        }

        public List<BiomeRegionOverride> overrides() {
            return Collections.unmodifiableList(new ArrayList<BiomeRegionOverride>(overrides.values()));
        }

        Map<String, String> dependenciesFor(Object value) {
            return dependencies(value, new LinkedHashSet<String>());
        }

        public PopulationPolicy population(String key) {
            key = WorldGenKey.parse(key, WorldGenKind.POPULATION).toString();
            PopulationPolicy existing = populations.get(key);
            if (existing != null) {
                return existing;
            }
            Declaration declaration = enter(key, WorldGenKind.POPULATION);
            try {
                PopulationPolicy result = new PopulationPolicy(key, declaration.value);
                populations.put(key, result);
                return result;
            } finally {
                resolving.remove(key);
            }
        }

        private void profile(Declaration declaration) {
            String key = declaration.key;
            Map<String, Object> value = declaration.value;
            fields(value, key, "key", "displayName", "nativeBase", "dimensions");
            String nativeBase = string(value, "nativeBase", "overworld", key);
            if (!nativeBase.equals("overworld") && !nativeBase.equals("nether") && !nativeBase.equals("sky")) {
                throw error(key + ".nativeBase", "expected overworld, nether or sky");
            }
            Map<String, Object> dimensions = object(required(value, "dimensions", key), key + ".dimensions");
            fields(dimensions, key + ".dimensions", "overworld");
            Map<String, Object> dimension = object(required(dimensions, "overworld", key),
                    key + ".dimensions.overworld");
            fields(dimension, key, "terrain", "biomeSource", "surfaces", "population", "spawn", "carvers",
                    "heightOutputs", "placementHeightmap", "features", "structures");
            TerrainSource terrain = terrain(required(dimension, "terrain", key), key + ".terrain");
            PopulationPolicy population = dimension.containsKey("population")
                    ? policy(dimension.get("population"), key + ".population")
                    : new PopulationPolicy(key, Collections.<String, Object>emptyMap());
            if (!nativeBase.equals("overworld") && !population.nativeCategories.isEmpty()
                    && population.nativeCategories.size() != 9) {
                throw error(key + ".population", "Hell and Sky native population supports only all or none");
            }
            BiomeSourceDefinition biomes = null;
            if (dimension.containsKey("biomeSource")) {
                Object biomeValue = dimension.get("biomeSource");
                biomes = biomeValue instanceof String
                        ? BiomeGenRegistry.sourceFor(WorldGenKey.parse((String) biomeValue, WorldGenKind.BIOME_SOURCE))
                        : BiomeSourceReader.read(
                                WorldGenKey.parse(key.replace(":generator/", ":biome_source/") + "/inline",
                                        WorldGenKind.BIOME_SOURCE),
                                declaration.owner, declaration.owner, key + ".biomeSource",
                                object(biomeValue, key + ".biomeSource"), spatial, false);
                if (biomes == null) {
                    throw error(key + ".biomeSource", "unknown biome source");
                }
                if (terrain.isNative() && !nativeBase.equals("overworld") && biomes.type.equals("spatial_climate")) {
                    throw error(key + ".biomeSource", "native Hell/Sky terrain requires its fixed climate");
                }
                if (terrain.isNative()) {
                    for (BiomeSourceDefinition.ClimateEntry entry : biomes.entries) {
                        if (entry.spatial != null && entry.spatial.requiresOriginalSpawn(spatial)) {
                            throw error(key + ".biomeSource",
                                    "anchor-dependent biome sources require owned terrain " + "with spawn preview");
                        }
                    }
                }
            }
            String surface = string(dimension, "surfaces", "inherit", key);
            if (!surface.equals("inherit") && !surface.equals("none")) {
                throw error(key + ".surfaces", "expected inherit or none");
            }
            List<CompiledCarver> selectedCarvers = new ArrayList<CompiledCarver>();
            if (dimension.containsKey("carvers")) {
                for (Object reference : list(dimension.get("carvers"), key + ".carvers", 0, 32)) {
                    selectedCarvers.add(reference instanceof String
                            ? carver((String) reference)
                            : inlineCarver(key + "/carver_" + selectedCarvers.size(),
                                    object(reference, key + ".carvers")));
                }
            }
            long carverWork = 0;
            for (CompiledCarver carver : selectedCarvers) {
                carverWork += carver.maximumMaskWork();
            }
            if (carverWork > 64000000) {
                throw error(key + ".carvers", "combined carvers exceed the 64-million-operation work budget");
            }
            if (!selectedCarvers.isEmpty() && terrain.isNative()) {
                throw error(key + ".carvers", "raw carvers require an owned terrain source");
            }
            Map<String, Object> spawn = dimension.containsKey("spawn")
                    ? object(dimension.get("spawn"), key + ".spawn")
                    : Collections.<String, Object>emptyMap();
            fields(spawn, key + ".spawn", "mode", "requireSafeGround");
            if (!string(spawn, "mode", "surface", key).equals("surface")) {
                throw error(key + ".spawn.mode", "expected surface");
            }
            Map<String, String> signature = new TreeMap<String, String>(BiomeGenRegistry.generationDependencies());
            Map<String, Object> generationDefinition = new LinkedHashMap<String, Object>(value);
            generationDefinition.remove("displayName");
            if (nativeBase.equals("overworld")) {
                generationDefinition.remove("nativeBase");
            } else {
                signature.put("native_base", nativeBase);
                signature.put("native_base_algorithm", "beta_1.7.3_v1");
            }
            signature.put("profile_definition", canonical(generationDefinition));
            signature.put("population_catalog", GenerationSignature.of(WorldGenRegistry.generationDependencies()));
            signature.putAll(spatial.dependenciesIn(dimension));
            for (int i = 0; i < selectedCarvers.size(); i++) {
                signature.put("carver_" + i, selectedCarvers.get(i).signature);
            }
            if (biomes != null) {
                StringBuilder source = new StringBuilder().append(biomes.type).append(':').append(biomes.fixedBiome);
                for (BiomeSourceDefinition.ClimateEntry entry : biomes.entries) {
                    source.append(':').append(entry.biome).append(':').append(entry.minTemperature).append(':')
                            .append(entry.maxTemperature).append(':').append(entry.minHumidity).append(':')
                            .append(entry.maxHumidity).append(':').append(entry.priority);
                    if (entry.spatial != null) {
                        source.append(':').append(entry.spatial.signatureForSnapshot(spatial));
                    }
                    source.append(':').append(entry.weight);
                }
                signature.put("biome_source", source.toString());
                signature.put("biome_fallback", String.valueOf(biomes.fallbackBiome));
            }
            Map<String, Object> creationDimension = new LinkedHashMap<String, Object>(dimension);
            creationDimension.remove("population");
            Map<String, Object> creationDimensions = new LinkedHashMap<String, Object>();
            creationDimensions.put("overworld", creationDimension);
            Map<String, Object> creationDefinition = new LinkedHashMap<String, Object>(generationDefinition);
            creationDefinition.put("dimensions", creationDimensions);
            WorldGeneratorProfile compiled = new WorldGeneratorProfile(key, declaration.owner, signature, terrain,
                    population, biomes, spatial, surface.equals("inherit"), bool(spawn, "requireSafeGround", true, key),
                    selectedCarvers, new GeneratorSettings(dimension, spatial, key),
                    string(value, "displayName", key, key), canonical(creationDefinition));
            profiles.put(key, compiled);
        }

        private TerrainSource terrain(Object value, String path) {
            return value instanceof String ? source((String) value) : compileSource(path, object(value, path));
        }

        private TerrainSource compileSource(String path, Map<String, Object> value) {
            Map<String, String> dependencies = dependencies(value, new LinkedHashSet<String>());
            if (string(value, "type", "heightmap", path).equals("compose")) {
                return new ComposedTerrainSource(path, value, spatial, this, dependencies);
            }
            if (string(value, "type", "heightmap", path).equals("profile_map")) {
                return new ProfileMapTerrainSource(path, value, spatial, this, this, dependencies);
            }
            return new CompiledTerrainSource(path, value, spatial, this, dependencies);
        }

        private PopulationPolicy policy(Object value, String path) {
            return value instanceof String
                    ? population((String) value)
                    : new PopulationPolicy(path, object(value, path));
        }

        private Map<String, String> dependencies(Object value, Set<String> visited) {
            Map<String, String> result = new TreeMap<String, String>(spatial.dependenciesIn(value));
            if (value instanceof Map) {
                for (Object child : ((Map<?, ?>) value).values()) {
                    result.putAll(dependencies(child, visited));
                }
            } else if (value instanceof List) {
                for (Object child : (List<?>) value) {
                    result.putAll(dependencies(child, visited));
                }
            } else if (value instanceof String && ((String) value).contains(":")) {
                for (WorldGenKind kind : new WorldGenKind[]{WorldGenKind.MATERIAL, WorldGenKind.TERRAIN_SOURCE}) {
                    try {
                        String key = WorldGenKey.parse((String) value, kind).toString();
                        Declaration declaration = values.get(key);
                        if (declaration != null && visited.add(key)) {
                            result.put(key, canonical(declaration.value));
                            result.putAll(dependencies(declaration.value, visited));
                        }
                    } catch (IllegalArgumentException ignored) {
                        // Not a reference to this generation domain.
                    }
                }
            }
            return result;
        }

        private List<WorldGeneratorProfile> profilesOwnedBy(String owner) {
            List<WorldGeneratorProfile> result = new ArrayList<WorldGeneratorProfile>();
            for (WorldGeneratorProfile profile : profiles.values()) {
                if (profile.owner.equals(owner)) {
                    result.add(profile);
                }
            }
            return result;
        }
    }

    private static final class CompilationFailure extends IllegalArgumentException {
        private final String owner;

        private CompilationFailure(String owner, IllegalArgumentException cause) {
            super(cause.getMessage(), cause);
            this.owner = owner;
        }
    }

    private static final class Declaration {
        private final String key;
        private final String owner;
        private final WorldGenKind kind;
        private final Map<String, Object> value;

        private Declaration(String key, String owner, WorldGenKind kind, Map<String, Object> value) {
            this.key = key;
            this.owner = owner;
            this.kind = kind;
            this.value = value;
        }
    }
}
