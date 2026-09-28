package betamoon.worldgen;

import betamoon.luamodloader.LuaScriptRegistry;
import betamoon.minecraft.MinecraftBuiltins;
import betamoon.worldgen.structure.StructureFeature;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import net.minecraft.src.BiomeGenBase;
import net.minecraft.src.Block;
import net.minecraft.src.Chunk;
import net.minecraft.src.World;
import org.luaj.vm2.LuaError;

/** Publishes and executes immutable Lua world-generation snapshots. */
public final class WorldGenRegistry {
    private static final String POPULATION_STAGE = "after_vanilla_population";
    private static final ThreadLocal<PublicationBatch> CURRENT_BATCH = new ThreadLocal<PublicationBatch>();
    private static volatile Snapshot active = Snapshot.empty();

    private static final class OreGenEntry {
        private final WorldGenKey key;
        private final WorldGenKey featureKey;
        private final int blockId;
        private final int veinsPerChunk;
        private final int veinSize;
        private final int minY;
        private final int maxY;
        private final GenerationDimension dimension;
        private final Integer targetBlockId;
        private final BiomeGenBase[] allowedBiomes;
        private final String resourceOwner;
        private final String owner;
        private final String sourceLocation;
        private final long salt;

        private OreGenEntry(WorldGenKey key, int blockId, int veinsPerChunk, int veinSize, int minY, int maxY,
                GenerationDimension dimension, Integer targetBlockId, BiomeGenBase[] allowedBiomes,
                String resourceOwner, String owner, String sourceLocation, long salt) {
            this.key = key;
            this.featureKey = WorldGenKey.parse(key.getNamespace() + ":feature/" + untypedPath(key),
                    WorldGenKind.FEATURE);
            this.blockId = blockId;
            this.veinsPerChunk = veinsPerChunk;
            this.veinSize = veinSize;
            this.minY = minY;
            this.maxY = maxY;
            this.dimension = dimension;
            this.targetBlockId = targetBlockId;
            this.allowedBiomes = allowedBiomes == null ? new BiomeGenBase[0] : allowedBiomes.clone();
            this.resourceOwner = resourceOwner;
            this.owner = owner;
            this.sourceLocation = sourceLocation;
            this.salt = salt;
        }

        private static String untypedPath(WorldGenKey key) {
            String prefix = WorldGenKind.PLACEMENT.getPath() + "/";
            return key.getPath().startsWith(prefix) ? key.getPath().substring(prefix.length()) : key.getPath();
        }
    }

    private static final class Snapshot {
        private final List<OreGenEntry> ores;
        private final Map<WorldGenKey, OreGenEntry> byKey;

        private Snapshot(List<OreGenEntry> entries) {
            List<OreGenEntry> sorted = new ArrayList<OreGenEntry>(entries);
            Collections.sort(sorted, new Comparator<OreGenEntry>() {
                @Override
                public int compare(OreGenEntry left, OreGenEntry right) {
                    return left.key.compareTo(right.key);
                }
            });
            ores = Collections.unmodifiableList(sorted);
            Map<WorldGenKey, OreGenEntry> keyed = new LinkedHashMap<WorldGenKey, OreGenEntry>();
            for (OreGenEntry entry : sorted) {
                keyed.put(entry.key, entry);
            }
            byKey = Collections.unmodifiableMap(keyed);
        }

        private static Snapshot empty() {
            return new Snapshot(Collections.<OreGenEntry>emptyList());
        }
    }

    /** Immutable object-free generator description for diagnostics. */
    public static final class Description {
        public final String key;
        public final String featureKey;
        public final String owner;
        public final String sourceLocation;
        public final String stage;
        public final int blockId;
        public final int veinsPerChunk;
        public final int veinSize;
        public final int minY;
        public final int maxY;
        public final String dimension;
        public final Integer targetBlockId;
        public final List<String> biomes;
        public final long salt;

        private Description(OreGenEntry entry) {
            key = entry.key.toString();
            featureKey = entry.featureKey.toString();
            owner = entry.owner;
            sourceLocation = entry.sourceLocation;
            stage = POPULATION_STAGE;
            blockId = entry.blockId;
            veinsPerChunk = entry.veinsPerChunk;
            veinSize = entry.veinSize;
            minY = entry.minY;
            maxY = entry.maxY;
            dimension = entry.dimension.getLuaName();
            targetBlockId = entry.targetBlockId;
            List<String> names = new ArrayList<String>();
            for (BiomeGenBase biome : entry.allowedBiomes) {
                names.add(biome == null ? "unknown" : biome.biomeName);
            }
            biomes = Collections.unmodifiableList(names);
            salt = entry.salt;
        }
    }

    /** Owner-local transaction used while one script initializes. */
    public static final class PublicationBatch implements AutoCloseable {
        private final String resourceOwner;
        private final String owner;
        private final List<OreGenEntry> ores = new ArrayList<OreGenEntry>();
        private final List<FeatureDefinition> features = new ArrayList<FeatureDefinition>();
        private final List<PlacementDefinition> placements = new ArrayList<PlacementDefinition>();
        private final List<RegionalStructureDefinition> regionalStructures =
                new ArrayList<RegionalStructureDefinition>();
        private final Set<WorldGenKey> biomeDecoratorTemplates = new HashSet<WorldGenKey>();
        private int declarationIndex;
        private boolean published;
        private boolean closed;

        private PublicationBatch(String resourceOwner, String owner) {
            this.resourceOwner = resourceOwner;
            this.owner = owner;
        }

        public void publish() {
            ensureOpen();
            validateBatchKeys(ores);
            FeaturePlacementRegistry.publishOwner(resourceOwner, features, placements, biomeDecoratorTemplates);
            RegionalStructureRegistry.publishOwner(resourceOwner, regionalStructures);
            publishBatch(this);
            published = true;
        }

        public void validate() {
            ensureOpen();
            validateBatchKeys(ores);
            FeaturePlacementRegistry.validateOwner(resourceOwner, features, placements, biomeDecoratorTemplates);
            RegionalStructureRegistry.validateOwner(resourceOwner, regionalStructures, features);
        }

        private void add(String key, int blockId, int veinsPerChunk, int veinSize, int minY, int maxY,
                GenerationDimension dimension, Integer targetBlockId, BiomeGenBase[] allowedBiomes, long salt) {
            ensureOpen();
            int index = declarationIndex++;
            WorldGenKey typedKey = key == null || key.trim().isEmpty()
                    ? WorldGenKey.privateKey(WorldGenKind.PLACEMENT, resourceOwner, index)
                    : parseKey(key, WorldGenKind.PLACEMENT, "OreGen.key");
            ores.add(createOreEntry(typedKey, blockId, veinsPerChunk, veinSize, minY, maxY, dimension, targetBlockId,
                    allowedBiomes, resourceOwner, owner, resourceOwner + ":worldgen[" + (index + 1) + "]", salt));
            addOreFeature(typedKey, blockId, veinsPerChunk, veinSize, minY, maxY, dimension, targetBlockId,
                    allowedBiomes, salt, index);
        }

        private WorldGenKey addFeature(String key, WorldGenKind kind, String type, WorldFeature feature,
                List<WorldGenKey> dependencies, int maxBlocks, int maxRadius) {
            ensureOpen();
            int index = declarationIndex++;
            WorldGenKey typedKey = parseKey(key, kind, "Feature.key");
            features.add(new FeatureDefinition(typedKey, type, resourceOwner, owner, source(index), feature,
                    dependencies, maxBlocks, maxRadius));
            return typedKey;
        }

        private WorldGenKey addPlacement(String key, WorldGenKey featureKey, GenerationStage stage,
                Set<String> dimensions, IntRange attempts, double extraChance, double probability,
                HeightProvider height, PlacementConditions conditions, List<WorldGenKey> before,
                List<WorldGenKey> after, int priority, long salt, int successLimit, String horizontal,
                int gridSpacing) {
            ensureOpen();
            int index = declarationIndex++;
            WorldGenKey typedKey = parseKey(key, WorldGenKind.PLACEMENT, "Placement.key");
            placements.add(new PlacementDefinition(typedKey, featureKey, resourceOwner, owner, source(index), stage,
                    dimensions, attempts, extraChance, probability, height, conditions, before, after, priority, salt,
                    successLimit, horizontal, gridSpacing));
            return typedKey;
        }

        private WorldGenKey addRegionalStructure(String key, WorldGenKey startFeature, Set<String> dimensions,
                int spacing, int separation, long salt, String heightType, int heightValue, int maxDepth,
                int maxPieces, int maxDistance, double terminationChance, boolean entityMarkers,
                boolean lootMarkers, List<RegionalStructureDefinition.PieceChoice> pieces) {
            ensureOpen();
            int index = declarationIndex++;
            WorldGenKey typedKey = parseKey(key, WorldGenKind.STRUCTURE, "RegionalStructure.key");
            regionalStructures.add(new RegionalStructureDefinition(typedKey, startFeature, resourceOwner, owner,
                    source(index), dimensions, spacing, separation, salt, heightType, heightValue, maxDepth,
                    maxPieces, maxDistance, terminationChance, entityMarkers, lootMarkers, pieces));
            return typedKey;
        }

        private void addOreFeature(WorldGenKey placementKey, int blockId, int veinsPerChunk, int veinSize, int minY,
                int maxY, GenerationDimension dimension, Integer targetBlockId, BiomeGenBase[] allowedBiomes,
                long salt, int index) {
            String untyped = OreGenEntry.untypedPath(placementKey);
            WorldGenKey featureKey = WorldGenKey.parse(placementKey.getNamespace() + ":feature/" + untyped,
                    WorldGenKind.FEATURE);
            int[] replacements;
            if (targetBlockId != null) {
                replacements = new int[]{targetBlockId.intValue()};
            } else if (dimension == GenerationDimension.OVERWORLD) {
                replacements = new int[]{Block.stone.blockID};
            } else if (dimension == GenerationDimension.NETHER) {
                replacements = new int[]{Block.netherrack.blockID};
            } else {
                replacements = new int[]{Block.stone.blockID, Block.netherrack.blockID};
            }
            features.add(new FeatureDefinition(featureKey, "ore_vein", resourceOwner, owner, source(index),
                    BuiltInFeatures.oreVein(blockId, 0, veinSize, new BlockSet(replacements)),
                    Collections.<WorldGenKey>emptyList(), Math.min(WorldGenLimits.MAX_BLOCK_CHANGES_PER_FEATURE,
                            Math.max(64, veinSize * 16)), WorldGenLimits.MAX_FEATURE_RADIUS));
            Set<String> dimensions = dimensionKeys(dimension);
            Set<String> biomes = new HashSet<String>();
            if (allowedBiomes != null) {
                for (BiomeGenBase biome : allowedBiomes) {
                    biomes.add(biome.biomeName.toLowerCase(java.util.Locale.ROOT));
                }
            }
            PlacementConditions conditions = new PlacementConditions(null, false, null, null, null, 0, 15,
                    biomes, Collections.<String>emptySet());
            placements.add(new PlacementDefinition(placementKey, featureKey, resourceOwner, owner, source(index),
                    GenerationStage.UNDERGROUND_FEATURES, dimensions, new IntRange(veinsPerChunk, veinsPerChunk),
                    0.0D, 1.0D, HeightProvider.uniform(minY, maxY), conditions,
                    Collections.<WorldGenKey>emptyList(), Collections.<WorldGenKey>emptyList(), 0, salt,
                    veinsPerChunk, "chunk", 1));
        }

        private String source(int index) {
            return resourceOwner + ":worldgen[" + (index + 1) + "]";
        }

        private void ensureOpen() {
            if (closed || published) {
                throw new IllegalStateException("World-generation publication is already completed");
            }
            if (CURRENT_BATCH.get() != this) {
                throw new IllegalStateException("World-generation publication owner is not active");
            }
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            if (CURRENT_BATCH.get() == this) {
                CURRENT_BATCH.remove();
            }
            closed = true;
        }
    }

    private WorldGenRegistry() {
    }

    public static PublicationBatch beginPublication(String resourceOwner, String owner) {
        if (CURRENT_BATCH.get() != null) {
            throw new IllegalStateException("A world-generation publication is already active");
        }
        PublicationBatch batch = new PublicationBatch(requiredOwner(resourceOwner), requiredOwner(owner));
        CURRENT_BATCH.set(batch);
        return batch;
    }

    /** Removes all definitions, primarily for complete shutdown and tests. */
    public static synchronized void clear() {
        active = Snapshot.empty();
        FeaturePlacementRegistry.clear();
        RegionalStructureRegistry.clear();
    }

    /** Removes definitions whose script package no longer exists after a load pass. */
    public static synchronized void retainOwners(Set<String> resourceOwners) {
        List<OreGenEntry> retained = new ArrayList<OreGenEntry>();
        for (OreGenEntry entry : active.ores) {
            if (resourceOwners.contains(entry.resourceOwner)) {
                retained.add(entry);
            }
        }
        active = new Snapshot(retained);
        FeaturePlacementRegistry.retainOwners(resourceOwners);
        RegionalStructureRegistry.retainOwners(resourceOwners);
    }

    public static WorldGenKey addOreGen(int blockId, int veinsPerChunk, int veinSize, int minY, int maxY,
            GenerationDimension dimension, Integer targetBlockId, BiomeGenBase[] allowedBiomes) {
        return addOreGen(null, blockId, veinsPerChunk, veinSize, minY, maxY, dimension, targetBlockId, allowedBiomes,
                0L);
    }

    public static WorldGenKey addOreGen(String key, int blockId, int veinsPerChunk, int veinSize, int minY, int maxY,
            GenerationDimension dimension, Integer targetBlockId, BiomeGenBase[] allowedBiomes, long salt) {
        PublicationBatch batch = CURRENT_BATCH.get();
        if (batch != null) {
            batch.add(key, blockId, veinsPerChunk, veinSize, minY, maxY, dimension, targetBlockId, allowedBiomes, salt);
            return batch.ores.get(batch.ores.size() - 1).key;
        }

        String resourceOwner = requiredOwner(LuaScriptRegistry.getCurrentScriptFile());
        String owner = LuaScriptRegistry.getCurrentScriptIdentity();
        int index = countOwnerEntries(resourceOwner);
        WorldGenKey typedKey = key == null || key.trim().isEmpty()
                ? WorldGenKey.privateKey(WorldGenKind.PLACEMENT, resourceOwner, index)
                : parseKey(key, WorldGenKind.PLACEMENT, "OreGen.key");
        OreGenEntry entry = createOreEntry(typedKey, blockId, veinsPerChunk, veinSize, minY, maxY, dimension,
                targetBlockId, allowedBiomes, resourceOwner, owner, resourceOwner + ":worldgen[" + (index + 1) + "]",
                salt);
        PublicationBatch runtime = new PublicationBatch(resourceOwner, owner);
        runtime.addOreFeature(typedKey, blockId, veinsPerChunk, veinSize, minY, maxY, dimension, targetBlockId,
                allowedBiomes, salt, index);
        FeaturePlacementRegistry.publishAddition(runtime.features.get(0), runtime.placements.get(0));
        publishImmediate(entry);
        return typedKey;
    }

    /** Immutable feature description for debug exports and tests. */
    public static final class FeatureDescription {
        public final String key;
        public final String type;
        public final String owner;
        public final String source;
        public final List<String> dependencies;
        public final int maxBlocks;
        public final int maxRadius;

        private FeatureDescription(FeaturePlacementRegistry.FeatureDescription description) {
            key = description.key;
            type = description.type;
            owner = description.owner;
            source = description.source;
            dependencies = description.dependencies;
            maxBlocks = description.maxBlocks;
            maxRadius = description.maxRadius;
        }
    }

    /** Immutable placement description including rejection counters. */
    public static final class PlacementDescription {
        public final String key;
        public final String feature;
        public final String owner;
        public final String source;
        public final String stage;
        public final String actualStage;
        public final Set<String> dimensions;
        public final long salt;
        public final long accepted;
        public final long rejected;
        public final long blocksChanged;
        public final Map<String, Integer> rejectionReasons;
        public final boolean disabled;
        public final boolean template;

        private PlacementDescription(FeaturePlacementRegistry.PlacementDescription description) {
            key = description.key;
            feature = description.feature;
            owner = description.owner;
            source = description.source;
            stage = description.stage;
            actualStage = description.actualStage;
            dimensions = description.dimensions;
            salt = description.salt;
            accepted = description.accepted;
            rejected = description.rejected;
            blocksChanged = description.blocksChanged;
            rejectionReasons = description.rejectionReasons;
            disabled = description.disabled;
            template = description.template;
        }
    }

    public static WorldGenKey addFeature(String key, String type, WorldFeature feature,
            List<WorldGenKey> dependencies, int maxBlocks, int maxRadius) {
        return addFeature(key, WorldGenKind.FEATURE, type, feature, dependencies, maxBlocks, maxRadius);
    }

    /** Immutable local-structure description for diagnostics. */
    public static final class StructureDescription {
        public final String key;
        public final String owner;
        public final String assetSource;
        public final String dimensions;
        public final int paletteEntries;
        public final int paletteVariants;
        public final int blocks;
        public final int markers;
        public final String rotation;
        public final String mirror;
        public final boolean includeAir;
        public final double decay;
        public final String tileCollision;
        public final String unknownMetadata;
        public final int customMetadataTransforms;

        private StructureDescription(FeaturePlacementRegistry.StructureDescription description) {
            StructureFeature.Description value = description.value;
            key = description.key;
            owner = description.owner;
            assetSource = value.assetSource;
            dimensions = value.sizeX + "x" + value.sizeY + "x" + value.sizeZ;
            paletteEntries = value.paletteEntries;
            paletteVariants = value.paletteVariants;
            blocks = value.blocks;
            markers = value.markers;
            rotation = value.rotation;
            mirror = value.mirror;
            includeAir = value.includeAir;
            decay = value.decay;
            tileCollision = value.tileCollision;
            unknownMetadata = value.unknownMetadata;
            customMetadataTransforms = value.customMetadataTransforms;
        }
    }

    /** Immutable regional structure description including runtime diagnostics. */
    public static final class RegionalStructureDescription {
        public final String key;
        public final String owner;
        public final String source;
        public final String start;
        public final Set<String> dimensions;
        public final int spacing;
        public final int separation;
        public final long salt;
        public final String height;
        public final int pieceChoices;
        public final int maxDepth;
        public final int maxPieces;
        public final int maxDistance;
        public final double terminationChance;
        public final long starts;
        public final long completedChunks;
        public final long recoveredChunks;
        public final long rejectedChunks;
        public final long blocksChanged;
        public final Map<String, Integer> rejectionReasons;
        public final boolean disabled;

        private RegionalStructureDescription(RegionalStructureRegistry.Description description) {
            key = description.key;
            owner = description.owner;
            source = description.source;
            start = description.start;
            dimensions = description.dimensions;
            spacing = description.spacing;
            separation = description.separation;
            salt = description.salt;
            height = description.height;
            pieceChoices = description.pools;
            maxDepth = description.maxDepth;
            maxPieces = description.maxPieces;
            maxDistance = description.maxDistance;
            terminationChance = description.terminationChance;
            starts = description.starts;
            completedChunks = description.completedChunks;
            recoveredChunks = description.recoveredChunks;
            rejectedChunks = description.rejectedChunks;
            blocksChanged = description.blocksChanged;
            rejectionReasons = description.rejectionReasons;
            disabled = description.disabled;
        }
    }

    public static final class RegionalStructureLocation {
        public final int x;
        public final Integer y;
        public final int z;
        public final boolean generated;
        public final int chunkX;
        public final int chunkZ;

        private RegionalStructureLocation(RegionalStructureRegistry.Location location) {
            x = location.x;
            y = location.y;
            z = location.z;
            generated = location.generated;
            chunkX = location.chunkX;
            chunkZ = location.chunkZ;
        }
    }

    public static WorldGenKey addFeature(String key, WorldGenKind kind, String type, WorldFeature feature,
            List<WorldGenKey> dependencies, int maxBlocks, int maxRadius) {
        validateFeatureBudget(maxBlocks, maxRadius);
        PublicationBatch batch = CURRENT_BATCH.get();
        if (batch != null) {
            return batch.addFeature(key, kind, type, feature, dependencies, maxBlocks, maxRadius);
        }
        String resourceOwner = requiredOwner(LuaScriptRegistry.getCurrentScriptFile());
        String owner = requiredOwner(LuaScriptRegistry.getCurrentScriptIdentity());
        WorldGenKey typedKey = parseKey(key, kind, "Feature.key");
        FeatureDefinition definition = new FeatureDefinition(typedKey, type, resourceOwner, owner,
                resourceOwner + ":worldgen[feature]", feature, dependencies, maxBlocks, maxRadius);
        FeaturePlacementRegistry.publishAddition(definition, null);
        return typedKey;
    }

    public static WorldGenKey addPlacement(String key, WorldGenKey featureKey, GenerationStage stage,
            Set<String> dimensions, IntRange attempts, double extraChance, double probability, HeightProvider height,
            PlacementConditions conditions, List<WorldGenKey> before, List<WorldGenKey> after, int priority, long salt,
            int successLimit, String horizontal, int gridSpacing) {
        validatePlacement(attempts, extraChance, probability, successLimit, horizontal, gridSpacing);
        PublicationBatch batch = CURRENT_BATCH.get();
        if (batch != null) {
            return batch.addPlacement(key, featureKey, stage, dimensions, attempts, extraChance, probability, height,
                    conditions, before, after, priority, salt, successLimit, horizontal, gridSpacing);
        }
        String resourceOwner = requiredOwner(LuaScriptRegistry.getCurrentScriptFile());
        String owner = requiredOwner(LuaScriptRegistry.getCurrentScriptIdentity());
        WorldGenKey typedKey = parseKey(key, WorldGenKind.PLACEMENT, "Placement.key");
        PlacementDefinition definition = new PlacementDefinition(typedKey, featureKey, resourceOwner, owner,
                resourceOwner + ":worldgen[placement]", stage, dimensions, attempts, extraChance, probability, height,
                conditions, before, after, priority, salt, successLimit, horizontal, gridSpacing);
        FeaturePlacementRegistry.publishAddition(null, definition);
        return typedKey;
    }

    public static WorldGenKey addRegionalStructure(String key, WorldGenKey startFeature, Set<String> dimensions,
            int spacing, int separation, long salt, String heightType, int heightValue, int maxDepth,
            int maxPieces, int maxDistance, double terminationChance, boolean entityMarkers,
            boolean lootMarkers, List<RegionalStructureDefinition.PieceChoice> pieces) {
        validateRegionalStructure(spacing, separation, heightType, heightValue, maxDepth, maxPieces, maxDistance,
                terminationChance, pieces);
        PublicationBatch batch = CURRENT_BATCH.get();
        if (batch != null) {
            return batch.addRegionalStructure(key, startFeature, dimensions, spacing, separation, salt, heightType,
                    heightValue, maxDepth, maxPieces, maxDistance, terminationChance, entityMarkers, lootMarkers,
                    pieces);
        }
        String resourceOwner = requiredOwner(LuaScriptRegistry.getCurrentScriptFile());
        String owner = requiredOwner(LuaScriptRegistry.getCurrentScriptIdentity());
        WorldGenKey typedKey = parseKey(key, WorldGenKind.STRUCTURE, "RegionalStructure.key");
        RegionalStructureRegistry.publishAddition(new RegionalStructureDefinition(typedKey, startFeature,
                resourceOwner, owner, resourceOwner + ":worldgen[regional_structure]", dimensions, spacing,
                separation, salt, heightType, heightValue, maxDepth, maxPieces, maxDistance, terminationChance,
                entityMarkers, lootMarkers, pieces));
        return typedKey;
    }

    public static WorldGenKey addBiomePlacement(String key, WorldGenKey sourceKey, String biomeSelector) {
        PlacementDefinition source = placementDefinition(sourceKey);
        if (source == null) {
            throw new LuaError("Biome decorator references unknown placement " + sourceKey);
        }
        PublicationBatch batch = CURRENT_BATCH.get();
        if (batch == null) {
            throw new LuaError("Biome decorators may only be declared while a Lua package is loading");
        }
        batch.biomeDecoratorTemplates.add(sourceKey);
        PlacementConditions original = source.conditions;
        PlacementConditions conditions = new PlacementConditions(original.ground, original.requireSky,
                original.requireAir, original.requireWater, original.requireLava, original.minLight,
                original.maxLight, Collections.singleton(biomeSelector), original.excludeBiomes);
        return addPlacement(key, source.featureKey, source.stage, source.dimensions, source.attempts,
                source.extraChance, source.probability, source.height, conditions, source.before, source.after,
                source.priority, source.salt, source.successLimit, source.horizontal, source.gridSpacing);
    }

    public static boolean hasFeature(WorldGenKey key) {
        PublicationBatch batch = CURRENT_BATCH.get();
        if (batch != null) {
            for (FeatureDefinition definition : batch.features) {
                if (definition.key.equals(key)) {
                    return true;
                }
            }
        }
        return FeaturePlacementRegistry.findFeature(key) != null;
    }

    public static boolean hasPlacement(WorldGenKey key) {
        PublicationBatch batch = CURRENT_BATCH.get();
        if (batch != null) {
            for (PlacementDefinition definition : batch.placements) {
                if (definition.key.equals(key)) {
                    return true;
                }
            }
        }
        return FeaturePlacementRegistry.findPlacement(key) != null;
    }

    public static boolean hasRegionalStructure(WorldGenKey key) {
        PublicationBatch batch = CURRENT_BATCH.get();
        if (batch != null) {
            for (RegionalStructureDefinition definition : batch.regionalStructures) {
                if (definition.key.equals(key)) {
                    return true;
                }
            }
        }
        return RegionalStructureRegistry.has(key);
    }

    public static WorldGenKey featureKeyForPlacement(WorldGenKey placementKey) {
        PlacementDefinition definition = placementDefinition(placementKey);
        return definition == null ? null : definition.featureKey;
    }

    private static PlacementDefinition placementDefinition(WorldGenKey placementKey) {
        PlacementDefinition definition = FeaturePlacementRegistry.findPlacement(placementKey);
        PublicationBatch batch = CURRENT_BATCH.get();
        if (definition == null && batch != null) {
            for (PlacementDefinition pending : batch.placements) {
                if (pending.key.equals(placementKey)) {
                    definition = pending;
                    break;
                }
            }
        }
        return definition;
    }

    public static FeatureResult placeFeature(WorldGenKey key, World world, BlockPosition origin, long seed) {
        return FeaturePlacementRegistry.place(key, world, origin, seed);
    }

    public static FeatureResult placeFeature(WorldGenKey key, World world, BlockPosition origin, long seed,
            FeatureOptions options) {
        return FeaturePlacementRegistry.place(key, world, origin, seed, options);
    }

    public static FeatureResult previewFeature(WorldGenKey key, World world, BlockPosition origin, long seed,
            FeatureOptions options) {
        return FeaturePlacementRegistry.preview(key, world, origin, seed, options);
    }

    public static List<Description> snapshot() {
        List<Description> result = new ArrayList<Description>();
        for (OreGenEntry entry : active.ores) {
            result.add(new Description(entry));
        }
        return Collections.unmodifiableList(result);
    }

    public static List<FeatureDescription> featureSnapshot() {
        List<FeatureDescription> result = new ArrayList<FeatureDescription>();
        for (FeaturePlacementRegistry.FeatureDescription description : FeaturePlacementRegistry.featureSnapshot()) {
            result.add(new FeatureDescription(description));
        }
        return Collections.unmodifiableList(result);
    }

    public static List<PlacementDescription> placementSnapshot() {
        List<PlacementDescription> result = new ArrayList<PlacementDescription>();
        for (FeaturePlacementRegistry.PlacementDescription description : FeaturePlacementRegistry
                .placementSnapshot()) {
            result.add(new PlacementDescription(description));
        }
        return Collections.unmodifiableList(result);
    }

    public static List<StructureDescription> structureSnapshot() {
        List<StructureDescription> result = new ArrayList<StructureDescription>();
        for (FeaturePlacementRegistry.StructureDescription description : FeaturePlacementRegistry
                .structureSnapshot()) {
            result.add(new StructureDescription(description));
        }
        return Collections.unmodifiableList(result);
    }

    public static List<RegionalStructureDescription> regionalStructureSnapshot() {
        List<RegionalStructureDescription> result = new ArrayList<RegionalStructureDescription>();
        for (RegionalStructureRegistry.Description description : RegionalStructureRegistry.snapshot()) {
            result.add(new RegionalStructureDescription(description));
        }
        return Collections.unmodifiableList(result);
    }

    public static RegionalStructureLocation locateRegionalStructure(World world, WorldGenKey key, int x, int z,
            int maxRegions) {
        RegionalStructureRegistry.Location location = RegionalStructureRegistry.locate(world, key, x, z,
                maxRegions);
        return location == null ? null : new RegionalStructureLocation(location);
    }

    public static void regionalStructureChunkLoaded(Chunk chunk) {
        RegionalStructureRegistry.chunkLoaded(chunk);
    }

    public static void generateSurface(World world, Random ignored, int chunkX, int chunkZ) {
        generate(world, chunkX, chunkZ, false);
    }

    public static void generateNether(World world, Random ignored, int chunkX, int chunkZ) {
        generate(world, chunkX, chunkZ, true);
    }

    private static void generate(World world, int chunkX, int chunkZ, boolean nether) {
        FeaturePlacementRegistry.generate(world, chunkX, chunkZ, nether);
    }

    private static synchronized void publishBatch(PublicationBatch batch) {
        validateBatchKeys(batch.ores);
        List<OreGenEntry> next = new ArrayList<OreGenEntry>();
        for (OreGenEntry current : active.ores) {
            if (!current.resourceOwner.equals(batch.resourceOwner)) {
                next.add(current);
            }
        }
        ensureNoForeignCollisions(next, batch.ores, batch.resourceOwner);
        next.addAll(batch.ores);
        active = new Snapshot(next);
    }

    private static synchronized void publishImmediate(OreGenEntry entry) {
        List<OreGenEntry> next = new ArrayList<OreGenEntry>(active.ores);
        ensureNoForeignCollisions(next, Collections.singletonList(entry), entry.resourceOwner);
        if (active.byKey.containsKey(entry.key)) {
            throw new LuaError("OreGen.key: duplicate world-generation key: " + entry.key);
        }
        next.add(entry);
        active = new Snapshot(next);
    }

    private static void validateBatchKeys(List<OreGenEntry> entries) {
        Set<WorldGenKey> keys = new HashSet<WorldGenKey>();
        for (OreGenEntry entry : entries) {
            if (!keys.add(entry.key)) {
                throw new LuaError("OreGen.key: duplicate world-generation key: " + entry.key);
            }
        }
    }

    private static void ensureNoForeignCollisions(List<OreGenEntry> current, List<OreGenEntry> additions,
            String resourceOwner) {
        Map<WorldGenKey, OreGenEntry> occupied = new LinkedHashMap<WorldGenKey, OreGenEntry>();
        for (OreGenEntry entry : current) {
            occupied.put(entry.key, entry);
        }
        for (OreGenEntry entry : additions) {
            OreGenEntry conflict = occupied.get(entry.key);
            if (conflict != null && !conflict.resourceOwner.equals(resourceOwner)) {
                throw new LuaError("World-generation key " + entry.key + " belongs to " + conflict.owner);
            }
        }
    }

    private static OreGenEntry createOreEntry(WorldGenKey key, int blockId, int veinsPerChunk, int veinSize,
            int minY, int maxY, GenerationDimension dimension, Integer targetBlockId, BiomeGenBase[] allowedBiomes,
            String resourceOwner, String owner, String sourceLocation, long salt) {
        if (blockId <= 0 || blockId >= Block.blocksList.length || Block.blocksList[blockId] == null) {
            throw new LuaError("OreGen.block: unknown block id: " + blockId);
        }
        if (veinsPerChunk < 0 || veinsPerChunk > WorldGenLimits.MAX_ATTEMPTS_PER_CHUNK) {
            throw new LuaError("OreGen.veinsPerChunk: expected 0.." + WorldGenLimits.MAX_ATTEMPTS_PER_CHUNK);
        }
        if (veinSize < 1 || veinSize > WorldGenLimits.MAX_ORE_VEIN_SIZE) {
            throw new LuaError("OreGen.veinSize: expected 1.." + WorldGenLimits.MAX_ORE_VEIN_SIZE);
        }
        if (minY < WorldGenLimits.MIN_HEIGHT || maxY > WorldGenLimits.MAX_HEIGHT || minY > maxY) {
            throw new LuaError("OreGen.height: expected 0 <= min <= max <= 127");
        }
        if (dimension == null) {
            throw new LuaError("OreGen.dimension: value is required");
        }
        if (targetBlockId != null && (targetBlockId.intValue() <= 0
                || targetBlockId.intValue() >= Block.blocksList.length
                || Block.blocksList[targetBlockId.intValue()] == null)) {
            throw new LuaError("OreGen.replace: unknown block id: " + targetBlockId);
        }
        int biomeCount = allowedBiomes == null ? 0 : allowedBiomes.length;
        if (biomeCount > WorldGenLimits.MAX_BIOME_FILTERS) {
            throw new LuaError("OreGen.biomes: at most " + WorldGenLimits.MAX_BIOME_FILTERS + " biomes are allowed");
        }
        return new OreGenEntry(key, blockId, veinsPerChunk, veinSize, minY, maxY, dimension, targetBlockId,
                allowedBiomes, resourceOwner, owner, sourceLocation, salt);
    }

    public static BiomeGenBase[] resolveBiomes(String[] names) {
        if (names == null || names.length == 0) {
            return new BiomeGenBase[0];
        }
        if (names.length > WorldGenLimits.MAX_BIOME_FILTERS) {
            throw new LuaError("Biome filter has more than " + WorldGenLimits.MAX_BIOME_FILTERS + " entries");
        }
        List<BiomeGenBase> list = new ArrayList<BiomeGenBase>();
        for (String name : names) {
            BiomeGenBase biome = MinecraftBuiltins.resolveBiome(name);
            if (biome == null) {
                throw new LuaError("Biome: unknown biome: " + name);
            }
            list.add(biome);
        }
        return list.toArray(new BiomeGenBase[list.size()]);
    }

    private static WorldGenKey parseKey(String value, WorldGenKind kind, String field) {
        try {
            return WorldGenKey.parse(value.trim(), kind);
        } catch (IllegalArgumentException error) {
            throw new LuaError(field + ": " + error.getMessage());
        }
    }

    private static Set<String> dimensionKeys(GenerationDimension dimension) {
        Set<String> result = new HashSet<String>();
        if (dimension.includes(false)) {
            result.add("minecraft:overworld");
        }
        if (dimension.includes(true)) {
            result.add("minecraft:nether");
        }
        return result;
    }

    private static void validateFeatureBudget(int maxBlocks, int maxRadius) {
        if (maxBlocks < 1 || maxBlocks > WorldGenLimits.MAX_BLOCK_CHANGES_PER_FEATURE) {
            throw new LuaError("Feature.maxBlocks: expected 1.." + WorldGenLimits.MAX_BLOCK_CHANGES_PER_FEATURE);
        }
        if (maxRadius < 1 || maxRadius > WorldGenLimits.MAX_FEATURE_RADIUS) {
            throw new LuaError("Feature.maxRadius: expected 1.." + WorldGenLimits.MAX_FEATURE_RADIUS);
        }
    }

    private static void validatePlacement(IntRange attempts, double extraChance, double probability,
            int successLimit, String horizontal, int gridSpacing) {
        if (attempts.min < 0 || attempts.max > WorldGenLimits.MAX_ATTEMPTS_PER_CHUNK) {
            throw new LuaError("Placement.attempts: expected 0.." + WorldGenLimits.MAX_ATTEMPTS_PER_CHUNK);
        }
        if (!Double.isFinite(extraChance) || extraChance < 0.0D || extraChance >= 1.0D) {
            throw new LuaError("Placement.extraChance: expected a finite value >= 0 and < 1");
        }
        if (!Double.isFinite(probability) || probability < 0.0D || probability > 1.0D) {
            throw new LuaError("Placement.probability: expected a finite value between 0 and 1");
        }
        if (successLimit < 1 || successLimit > WorldGenLimits.MAX_ATTEMPTS_PER_CHUNK) {
            throw new LuaError("Placement.successLimit: expected 1.." + WorldGenLimits.MAX_ATTEMPTS_PER_CHUNK);
        }
        if (!"chunk".equals(horizontal) && !"grid".equals(horizontal)) {
            throw new LuaError("Placement.position.horizontal: expected 'chunk' or 'grid'");
        }
        if (gridSpacing < 1 || gridSpacing > 16) {
            throw new LuaError("Placement.position.gridSpacing: expected 1..16");
        }
    }

    private static void validateRegionalStructure(int spacing, int separation, String heightType, int heightValue,
            int maxDepth, int maxPieces, int maxDistance, double terminationChance,
            List<RegionalStructureDefinition.PieceChoice> pieces) {
        if (spacing < 2 || spacing > WorldGenLimits.MAX_REGIONAL_SPACING) {
            throw new LuaError("RegionalStructure.spacing: expected 2.."
                    + WorldGenLimits.MAX_REGIONAL_SPACING);
        }
        if (separation < 0 || separation >= spacing) {
            throw new LuaError("RegionalStructure.separation: expected 0..spacing-1");
        }
        if (!(heightType.equals("fixed") || heightType.equals("surface"))) {
            throw new LuaError("RegionalStructure.height.type: expected 'fixed' or 'surface'");
        }
        if (heightType.equals("fixed") && (heightValue < 0 || heightValue > 127)
                || heightType.equals("surface") && (heightValue < -127 || heightValue > 127)) {
            throw new LuaError("RegionalStructure.height: value is outside the supported world height");
        }
        if (maxDepth < 0 || maxDepth > WorldGenLimits.MAX_REGIONAL_DEPTH) {
            throw new LuaError("RegionalStructure.maxDepth: expected 0.."
                    + WorldGenLimits.MAX_REGIONAL_DEPTH);
        }
        if (maxPieces < 1 || maxPieces > WorldGenLimits.MAX_REGIONAL_PIECES) {
            throw new LuaError("RegionalStructure.maxPieces: expected 1.."
                    + WorldGenLimits.MAX_REGIONAL_PIECES);
        }
        if (maxDistance < 16 || maxDistance > WorldGenLimits.MAX_REGIONAL_DISTANCE) {
            throw new LuaError("RegionalStructure.maxDistance: expected 16.."
                    + WorldGenLimits.MAX_REGIONAL_DISTANCE);
        }
        if (maxDistance > spacing * 16) {
            throw new LuaError("RegionalStructure.maxDistance: must not exceed spacing * 16 blocks");
        }
        if (!Double.isFinite(terminationChance) || terminationChance < 0.0D || terminationChance > 1.0D) {
            throw new LuaError("RegionalStructure.terminationChance: expected a finite value from 0 to 1");
        }
        if (pieces.size() > 64) {
            throw new LuaError("RegionalStructure.pieces: at most 64 weighted entries are allowed");
        }
    }

    private static int countOwnerEntries(String resourceOwner) {
        int count = 0;
        for (OreGenEntry entry : active.ores) {
            if (entry.resourceOwner.equals(resourceOwner)) {
                count++;
            }
        }
        return count;
    }

    private static String requiredOwner(String owner) {
        if (owner == null || owner.trim().isEmpty()) {
            throw new IllegalStateException("World-generation declarations require an active script owner");
        }
        return owner;
    }

}
