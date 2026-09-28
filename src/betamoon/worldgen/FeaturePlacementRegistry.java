package betamoon.worldgen;

import betamoon.BetaMoonCommon;
import betamoon.luamodloader.LuaScriptErrors;
import betamoon.worldgen.structure.StructureFeature;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import net.minecraft.src.World;
import org.luaj.vm2.LuaError;

/**
 * Immutable runtime catalog and execution engine for reusable features and
 * placements.
 */
final class FeaturePlacementRegistry {
    private static final String ACTUAL_STAGE = "after_vanilla_population";
    private static volatile Snapshot active = Snapshot.empty();
    private static volatile Set<WorldGenKey> decoratorTemplates = Collections.emptySet();
    private static final Map<String, MutableDiagnostics> diagnostics = new LinkedHashMap<String, MutableDiagnostics>();
    private static final Set<WorldGenKey> disabled = new HashSet<WorldGenKey>();
    private static final Map<String, Set<WorldGenKey>> decoratorTemplatesByOwner = new LinkedHashMap<String, Set<WorldGenKey>>();

    private FeaturePlacementRegistry() {
    }

    static synchronized void clear() {
        active = Snapshot.empty();
        decoratorTemplates = Collections.emptySet();
        decoratorTemplatesByOwner.clear();
        diagnostics.clear();
        disabled.clear();
    }

    static synchronized void retainOwners(Set<String> owners) {
        List<FeatureDefinition> features = new ArrayList<FeatureDefinition>();
        List<PlacementDefinition> placements = new ArrayList<PlacementDefinition>();
        for (FeatureDefinition definition : active.features.values()) {
            if (owners.contains(definition.resourceOwner)) {
                features.add(definition);
            }
        }
        for (PlacementDefinition definition : active.placements) {
            if (owners.contains(definition.resourceOwner)) {
                placements.add(definition);
            }
        }
        active = Snapshot.compile(features, placements);
        decoratorTemplatesByOwner.keySet().retainAll(owners);
        rebuildDecoratorTemplates();
    }

    static synchronized void publishOwner(String resourceOwner, List<FeatureDefinition> ownerFeatures,
            List<PlacementDefinition> ownerPlacements, Set<WorldGenKey> ownerDecoratorTemplates) {
        active = ownerSnapshot(resourceOwner, ownerFeatures, ownerPlacements);
        decoratorTemplatesByOwner.put(resourceOwner,
                Collections.unmodifiableSet(new HashSet<WorldGenKey>(ownerDecoratorTemplates)));
        rebuildDecoratorTemplates();
        disabled.removeAll(keys(ownerPlacements));
    }

    static synchronized void validateOwner(String resourceOwner, List<FeatureDefinition> ownerFeatures,
            List<PlacementDefinition> ownerPlacements, Set<WorldGenKey> ownerDecoratorTemplates) {
        Snapshot candidate = ownerSnapshot(resourceOwner, ownerFeatures, ownerPlacements);
        for (WorldGenKey key : ownerDecoratorTemplates) {
            if (!candidate.placementsByKey.containsKey(key)) {
                throw new LuaError("Biome decorator references unknown placement " + key);
            }
        }
    }

    private static Snapshot ownerSnapshot(String resourceOwner, List<FeatureDefinition> ownerFeatures,
            List<PlacementDefinition> ownerPlacements) {
        List<FeatureDefinition> features = new ArrayList<FeatureDefinition>();
        List<PlacementDefinition> placements = new ArrayList<PlacementDefinition>();
        for (FeatureDefinition definition : active.features.values()) {
            if (!resourceOwner.equals(definition.resourceOwner)) {
                features.add(definition);
            }
        }
        for (PlacementDefinition definition : active.placements) {
            if (!resourceOwner.equals(definition.resourceOwner)) {
                placements.add(definition);
            }
        }
        features.addAll(ownerFeatures);
        placements.addAll(ownerPlacements);
        return Snapshot.compile(features, placements);
    }

    static synchronized void publishAddition(FeatureDefinition feature, PlacementDefinition placement) {
        List<FeatureDefinition> features = new ArrayList<FeatureDefinition>(active.features.values());
        List<PlacementDefinition> placements = new ArrayList<PlacementDefinition>(active.placements);
        if (feature != null) {
            features.add(feature);
        }
        if (placement != null) {
            placements.add(placement);
        }
        active = Snapshot.compile(features, placements);
    }

    static synchronized void replace(FeatureDefinition feature, PlacementDefinition placement) {
        List<FeatureDefinition> features = new ArrayList<FeatureDefinition>();
        for (FeatureDefinition current : active.features.values()) {
            if (feature == null || !current.key.equals(feature.key)) {
                features.add(current);
            }
        }
        List<PlacementDefinition> placements = new ArrayList<PlacementDefinition>();
        for (PlacementDefinition current : active.placements) {
            if (placement == null || !current.key.equals(placement.key)) {
                placements.add(current);
            }
        }
        if (feature != null) {
            features.add(feature);
        }
        if (placement != null) {
            placements.add(placement);
        }
        active = Snapshot.compile(features, placements);
    }

    static synchronized void setEnabled(WorldGenKey key, boolean enabled) {
        if (enabled) {
            disabled.remove(key);
        } else {
            disabled.add(key);
        }
    }

    static FeatureDefinition findFeature(WorldGenKey key) {
        return active.features.get(key);
    }

    static PlacementDefinition findPlacement(WorldGenKey key) {
        return active.placementsByKey.get(key);
    }

    static FeatureResult place(WorldGenKey key, World world, BlockPosition origin, long seed) {
        return place(key, world, origin, seed, FeatureOptions.DEFAULT);
    }

    static FeatureResult place(WorldGenKey key, World world, BlockPosition origin, long seed, FeatureOptions options) {
        Snapshot snapshot = active;
        FeatureDefinition definition = snapshot.features.get(key);
        if (definition == null) {
            return FeatureResult.rejected(FeatureResult.RUNTIME_ERROR);
        }
        return executeFeature(snapshot, definition, world, origin, new Random(seed), options,
                ExecutionMode.RUNTIME);
    }

    static FeatureResult preview(WorldGenKey key, World world, BlockPosition origin, long seed,
            FeatureOptions options) {
        Snapshot snapshot = active;
        FeatureDefinition definition = snapshot.features.get(key);
        if (definition == null) {
            return FeatureResult.rejected(FeatureResult.RUNTIME_ERROR);
        }
        return executeFeature(snapshot, definition, world, origin, new Random(seed), options,
                ExecutionMode.PREVIEW);
    }

    static void generate(World world, int chunkX, int chunkZ, boolean nether) {
        Snapshot snapshot = active;
        String dimension = nether ? "minecraft:nether" : "minecraft:overworld";
        int logicalChunkX = Math.floorDiv(chunkX, 16);
        int logicalChunkZ = Math.floorDiv(chunkZ, 16);
        for (PlacementDefinition placement : snapshot.placements) {
            if (!placement.dimensions.isEmpty() && !placement.dimensions.contains(dimension)
                    || isDisabled(placement.key) || decoratorTemplates.contains(placement.key)) {
                continue;
            }
            FeatureDefinition feature = snapshot.features.get(placement.featureKey);
            if (feature == null) {
                continue;
            }
            long seed = SeedMixer.generationSeed(world.getRandomSeed(), dimension, placement.stage.getName(),
                    logicalChunkX, logicalChunkZ, placement.key, placement.salt);
            Random random = new Random(seed);
            if (placement.probability < 1.0D && random.nextDouble() >= placement.probability) {
                reject(placement.key, "probability");
                continue;
            }
            int successes = 0;
            int attempts = placement.sampleAttempts(random);
            for (int attempt = 0; attempt < attempts && successes < placement.successLimit; attempt++) {
                FeatureContext context = context(snapshot, world, random, feature);
                BlockPosition origin = placement.sampleOrigin(context, chunkX, chunkZ, attempt);
                if (origin == null) {
                    reject(placement.key, context.failure() == null ? FeatureResult.OUT_OF_BOUNDS : context.failure());
                    continue;
                }
                String rejection = placement.conditions.rejection(context, origin);
                if (rejection != null) {
                    reject(placement.key, rejection);
                    continue;
                }
                try {
                    FeatureResult result = executeFeature(snapshot, feature, context, origin);
                    if (result.placed) {
                        successes++;
                        accept(placement.key, result.blocksChanged);
                    } else {
                        reject(placement.key, result.reason);
                    }
                } catch (Throwable error) {
                    disable(placement, error);
                    break;
                }
            }
        }
        RegionalStructureRegistry.generate(world, logicalChunkX, logicalChunkZ, dimension);
    }

    static List<FeatureDescription> featureSnapshot() {
        List<FeatureDescription> result = new ArrayList<FeatureDescription>();
        for (FeatureDefinition definition : active.features.values()) {
            result.add(new FeatureDescription(definition));
        }
        return Collections.unmodifiableList(result);
    }

    static List<PlacementDescription> placementSnapshot() {
        List<PlacementDescription> result = new ArrayList<PlacementDescription>();
        for (PlacementDefinition definition : active.placements) {
            MutableDiagnostics values;
            synchronized (FeaturePlacementRegistry.class) {
                values = diagnostics.get(definition.key.toString());
            }
            result.add(new PlacementDescription(definition, values, decoratorTemplates.contains(definition.key)));
        }
        return Collections.unmodifiableList(result);
    }

    private static FeatureResult executeFeature(Snapshot snapshot, FeatureDefinition definition, World world,
            BlockPosition origin, Random random) {
        return executeFeature(snapshot, definition, world, origin, random, FeatureOptions.DEFAULT,
                ExecutionMode.WORLD_GENERATION);
    }

    static List<StructureDescription> structureSnapshot() {
        List<StructureDescription> result = new ArrayList<StructureDescription>();
        for (FeatureDefinition definition : active.features.values()) {
            if (definition.feature instanceof StructureFeature) {
                result.add(new StructureDescription(definition, ((StructureFeature) definition.feature).description()));
            }
        }
        return Collections.unmodifiableList(result);
    }

    private static FeatureResult executeFeature(Snapshot snapshot, FeatureDefinition definition, World world,
            BlockPosition origin, Random random, FeatureOptions options, ExecutionMode mode) {
        FeatureContext context = context(snapshot, world, random, definition, options);
        PlacementPlan plan = new PlacementPlan(origin, definition.maxBlocks, definition.maxRadius);
        FeatureResult planned = definition.feature.plan(context, origin, plan);
        if (!planned.placed) {
            return planned;
        }
        if (mode == ExecutionMode.PREVIEW) {
            return plan.preview(context);
        }
        return mode == ExecutionMode.RUNTIME ? plan.commitWithUpdates(context) : plan.commit(context);
    }

    private static FeatureResult executeFeature(Snapshot snapshot, FeatureDefinition definition, FeatureContext context,
            BlockPosition origin) {
        PlacementPlan plan = new PlacementPlan(origin, definition.maxBlocks, definition.maxRadius);
        FeatureResult planned = definition.feature.plan(context, origin, plan);
        if (!planned.placed) {
            return planned;
        }
        return plan.commit(context);
    }

    private static FeatureContext context(final Snapshot snapshot, World world, Random random,
            FeatureDefinition definition) {
        return context(snapshot, world, random, definition, FeatureOptions.DEFAULT);
    }

    private enum ExecutionMode {
        PREVIEW,
        WORLD_GENERATION,
        RUNTIME
    }

    private static FeatureContext context(final Snapshot snapshot, World world, Random random,
            FeatureDefinition definition, FeatureOptions options) {
        return new FeatureContext(world, random, definition.key, Math.max(256, definition.maxBlocks * 32),
                new FeatureContext.FeatureResolver() {
                    @Override
                    public FeatureDefinition find(WorldGenKey key) {
                        return snapshot.features.get(key);
                    }
                }, options);
    }

    private static synchronized boolean isDisabled(WorldGenKey key) {
        return disabled.contains(key);
    }

    private static void rebuildDecoratorTemplates() {
        Set<WorldGenKey> values = new HashSet<WorldGenKey>();
        for (Set<WorldGenKey> ownerValues : decoratorTemplatesByOwner.values()) {
            for (WorldGenKey key : ownerValues) {
                if (active.placementsByKey.containsKey(key)) {
                    values.add(key);
                }
            }
        }
        decoratorTemplates = Collections.unmodifiableSet(values);
    }

    private static synchronized void disable(PlacementDefinition placement, Throwable error) {
        disabled.add(placement.key);
        reject(placement.key, FeatureResult.RUNTIME_ERROR);
        String message = "Placement " + placement.key + " was disabled after an error: " + error.getMessage();
        BetaMoonCommon.LOGGER.warning(placement.owner + ": " + message);
        LuaScriptErrors.add(placement.owner, message);
    }

    private static synchronized void accept(WorldGenKey key, int blocks) {
        MutableDiagnostics values = diagnostic(key);
        values.accepted++;
        values.blocksChanged += blocks;
    }

    private static synchronized void reject(WorldGenKey key, String reason) {
        MutableDiagnostics values = diagnostic(key);
        values.rejected++;
        String stableReason = reason == null ? "unknown" : reason;
        Integer count = values.reasons.get(stableReason);
        values.reasons.put(stableReason, Integer.valueOf(count == null ? 1 : count.intValue() + 1));
    }

    private static MutableDiagnostics diagnostic(WorldGenKey key) {
        MutableDiagnostics values = diagnostics.get(key.toString());
        if (values == null) {
            values = new MutableDiagnostics();
            diagnostics.put(key.toString(), values);
        }
        return values;
    }

    private static Set<WorldGenKey> keys(List<PlacementDefinition> placements) {
        Set<WorldGenKey> result = new HashSet<WorldGenKey>();
        for (PlacementDefinition placement : placements) {
            result.add(placement.key);
        }
        return result;
    }

    private static final class Snapshot {
        private final Map<WorldGenKey, FeatureDefinition> features;
        private final List<PlacementDefinition> placements;
        private final Map<WorldGenKey, PlacementDefinition> placementsByKey;

        private Snapshot(Map<WorldGenKey, FeatureDefinition> features, List<PlacementDefinition> placements) {
            this.features = Collections.unmodifiableMap(features);
            this.placements = Collections.unmodifiableList(placements);
            Map<WorldGenKey, PlacementDefinition> keyed = new LinkedHashMap<WorldGenKey, PlacementDefinition>();
            for (PlacementDefinition placement : placements) {
                keyed.put(placement.key, placement);
            }
            placementsByKey = Collections.unmodifiableMap(keyed);
        }

        private static Snapshot empty() {
            return new Snapshot(new LinkedHashMap<WorldGenKey, FeatureDefinition>(),
                    Collections.<PlacementDefinition>emptyList());
        }

        private static Snapshot compile(List<FeatureDefinition> featureValues,
                List<PlacementDefinition> placementValues) {
            Map<WorldGenKey, FeatureDefinition> features = new LinkedHashMap<WorldGenKey, FeatureDefinition>();
            for (FeatureDefinition feature : featureValues) {
                FeatureDefinition conflict = features.put(feature.key, feature);
                if (conflict != null) {
                    throw collision(feature.key, conflict.owner, feature.owner);
                }
            }
            validateFeatureReferences(features);

            Map<WorldGenKey, PlacementDefinition> placements = new LinkedHashMap<WorldGenKey, PlacementDefinition>();
            for (PlacementDefinition placement : placementValues) {
                PlacementDefinition conflict = placements.put(placement.key, placement);
                if (conflict != null) {
                    throw collision(placement.key, conflict.owner, placement.owner);
                }
                if (!features.containsKey(placement.featureKey)) {
                    throw new LuaError(
                            "Placement " + placement.key + " references unknown feature " + placement.featureKey);
                }
            }
            List<PlacementDefinition> ordered = orderPlacements(placements);
            return new Snapshot(features, ordered);
        }

        private static LuaError collision(WorldGenKey key, String firstOwner, String secondOwner) {
            return new LuaError(
                    "World-generation key " + key + " is declared by both " + firstOwner + " and " + secondOwner);
        }

        private static void validateFeatureReferences(Map<WorldGenKey, FeatureDefinition> features) {
            Set<WorldGenKey> visiting = new HashSet<WorldGenKey>();
            Set<WorldGenKey> visited = new HashSet<WorldGenKey>();
            for (FeatureDefinition feature : features.values()) {
                visitFeature(feature, features, visiting, visited);
            }
        }

        private static void visitFeature(FeatureDefinition feature, Map<WorldGenKey, FeatureDefinition> features,
                Set<WorldGenKey> visiting, Set<WorldGenKey> visited) {
            if (visited.contains(feature.key)) {
                return;
            }
            if (!visiting.add(feature.key)) {
                throw new LuaError("Feature dependency cycle contains " + feature.key);
            }
            for (WorldGenKey dependency : feature.dependencies) {
                FeatureDefinition target = features.get(dependency);
                if (target == null) {
                    throw new LuaError("Feature " + feature.key + " references unknown feature " + dependency);
                }
                visitFeature(target, features, visiting, visited);
            }
            visiting.remove(feature.key);
            visited.add(feature.key);
        }

        private static List<PlacementDefinition> orderPlacements(Map<WorldGenKey, PlacementDefinition> placements) {
            List<PlacementDefinition> result = new ArrayList<PlacementDefinition>();
            for (GenerationStage stage : GenerationStage.values()) {
                Map<WorldGenKey, Set<WorldGenKey>> outgoing = new LinkedHashMap<WorldGenKey, Set<WorldGenKey>>();
                Map<WorldGenKey, Integer> incoming = new LinkedHashMap<WorldGenKey, Integer>();
                for (PlacementDefinition placement : placements.values()) {
                    if (placement.stage == stage) {
                        outgoing.put(placement.key, new LinkedHashSet<WorldGenKey>());
                        incoming.put(placement.key, Integer.valueOf(0));
                    }
                }
                for (PlacementDefinition placement : placements.values()) {
                    if (placement.stage != stage) {
                        continue;
                    }
                    for (WorldGenKey key : placement.after) {
                        addEdge(key, placement.key, stage, placements, outgoing, incoming);
                    }
                    for (WorldGenKey key : placement.before) {
                        addEdge(placement.key, key, stage, placements, outgoing, incoming);
                    }
                }
                while (!incoming.isEmpty()) {
                    PlacementDefinition next = nextAvailable(placements, incoming);
                    if (next == null) {
                        throw new LuaError("Placement ordering cycle in stage " + stage.getName());
                    }
                    result.add(next);
                    incoming.remove(next.key);
                    for (WorldGenKey target : outgoing.get(next.key)) {
                        incoming.put(target, Integer.valueOf(incoming.get(target).intValue() - 1));
                    }
                }
            }
            return result;
        }

        private static void addEdge(WorldGenKey source, WorldGenKey target, GenerationStage stage,
                Map<WorldGenKey, PlacementDefinition> placements, Map<WorldGenKey, Set<WorldGenKey>> outgoing,
                Map<WorldGenKey, Integer> incoming) {
            PlacementDefinition sourcePlacement = placements.get(source);
            PlacementDefinition targetPlacement = placements.get(target);
            if (sourcePlacement == null || targetPlacement == null) {
                throw new LuaError(
                        "Placement ordering references unknown key " + (sourcePlacement == null ? source : target));
            }
            if (sourcePlacement.stage != stage || targetPlacement.stage != stage) {
                throw new LuaError("Placement ordering cannot cross generation stages: " + source + " -> " + target);
            }
            if (outgoing.get(source).add(target)) {
                incoming.put(target, Integer.valueOf(incoming.get(target).intValue() + 1));
            }
        }

        private static PlacementDefinition nextAvailable(Map<WorldGenKey, PlacementDefinition> placements,
                Map<WorldGenKey, Integer> incoming) {
            PlacementDefinition best = null;
            for (Map.Entry<WorldGenKey, Integer> entry : incoming.entrySet()) {
                if (entry.getValue().intValue() != 0) {
                    continue;
                }
                PlacementDefinition candidate = placements.get(entry.getKey());
                if (best == null || candidate.priority < best.priority
                        || candidate.priority == best.priority && candidate.key.compareTo(best.key) < 0) {
                    best = candidate;
                }
            }
            return best;
        }
    }

    private static final class MutableDiagnostics {
        private long accepted;
        private long rejected;
        private long blocksChanged;
        private final Map<String, Integer> reasons = new LinkedHashMap<String, Integer>();
    }

    static final class FeatureDescription {
        final String key;
        final String type;
        final String owner;
        final String source;
        final List<String> dependencies;
        final int maxBlocks;
        final int maxRadius;

        private FeatureDescription(FeatureDefinition definition) {
            key = definition.key.toString();
            type = definition.type;
            owner = definition.owner;
            source = definition.sourceLocation;
            List<String> values = new ArrayList<String>();
            for (WorldGenKey dependency : definition.dependencies) {
                values.add(dependency.toString());
            }
            dependencies = Collections.unmodifiableList(values);
            maxBlocks = definition.maxBlocks;
            maxRadius = definition.maxRadius;
        }
    }

    static final class PlacementDescription {
        final String key;
        final String feature;
        final String owner;
        final String source;
        final String stage;
        final String actualStage;
        final Set<String> dimensions;
        final long salt;
        final long accepted;
        final long rejected;
        final long blocksChanged;
        final Map<String, Integer> rejectionReasons;
        final boolean disabled;
        final boolean template;

        private PlacementDescription(PlacementDefinition definition, MutableDiagnostics values, boolean template) {
            key = definition.key.toString();
            feature = definition.featureKey.toString();
            owner = definition.owner;
            source = definition.sourceLocation;
            stage = definition.stage.getName();
            actualStage = ACTUAL_STAGE;
            dimensions = definition.dimensions;
            salt = definition.salt;
            accepted = values == null ? 0 : values.accepted;
            rejected = values == null ? 0 : values.rejected;
            blocksChanged = values == null ? 0 : values.blocksChanged;
            rejectionReasons = values == null
                    ? Collections.<String, Integer>emptyMap()
                    : Collections.unmodifiableMap(new LinkedHashMap<String, Integer>(values.reasons));
            disabled = isDisabled(definition.key);
            this.template = template;
        }
    }

    static final class StructureDescription {
        final String key;
        final String owner;
        final StructureFeature.Description value;

        private StructureDescription(FeatureDefinition definition, StructureFeature.Description value) {
            key = definition.key.toString();
            owner = definition.owner;
            this.value = value;
        }
    }
}
