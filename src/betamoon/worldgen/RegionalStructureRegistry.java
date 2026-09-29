package betamoon.worldgen;

import betamoon.BetaMoonCommon;
import betamoon.luamodloader.LuaScriptErrors;
import betamoon.worldgen.structure.StructureFeature;
import betamoon.worldgen.structure.StructureTransform;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import net.minecraft.src.Chunk;
import net.minecraft.src.World;
import org.luaj.vm2.LuaError;

/** Deterministic random-spread starts, connector planning, and chunk-clipped execution. */
final class RegionalStructureRegistry {
    private static volatile Snapshot active = Snapshot.empty();
    private static final Map<String, MutableDiagnostics> diagnostics =
            new LinkedHashMap<String, MutableDiagnostics>();
    private static final Set<WorldGenKey> disabled = new HashSet<WorldGenKey>();

    private RegionalStructureRegistry() {
    }

    static synchronized void clear() {
        active = Snapshot.empty();
        diagnostics.clear();
        disabled.clear();
        RegionalStructureIndex.clearRuntime();
    }

    static synchronized void retainOwners(Set<String> owners) {
        List<RegionalStructureDefinition> retained = new ArrayList<RegionalStructureDefinition>();
        for (RegionalStructureDefinition definition : active.definitions.values()) {
            if (owners.contains(definition.resourceOwner)) {
                retained.add(definition);
            }
        }
        active = Snapshot.compile(retained);
    }

    static synchronized void publishOwner(String owner, List<RegionalStructureDefinition> additions) {
        active = ownerSnapshot(owner, additions);
        for (RegionalStructureDefinition definition : additions) {
            disabled.remove(definition.key);
        }
    }

    static synchronized void validateOwner(String owner, List<RegionalStructureDefinition> additions,
            List<FeatureDefinition> stagedFeatures) {
        List<RegionalStructureDefinition> values = new ArrayList<RegionalStructureDefinition>();
        for (RegionalStructureDefinition definition : active.definitions.values()) {
            if (!owner.equals(definition.resourceOwner)) {
                values.add(definition);
            }
        }
        values.addAll(additions);
        Snapshot.compile(values, stagedFeatures);
    }

    private static Snapshot ownerSnapshot(String owner, List<RegionalStructureDefinition> additions) {
        List<RegionalStructureDefinition> values = new ArrayList<RegionalStructureDefinition>();
        for (RegionalStructureDefinition definition : active.definitions.values()) {
            if (!owner.equals(definition.resourceOwner)) {
                values.add(definition);
            }
        }
        values.addAll(additions);
        return Snapshot.compile(values);
    }

    static synchronized void publishAddition(RegionalStructureDefinition definition) {
        List<RegionalStructureDefinition> values = new ArrayList<RegionalStructureDefinition>(
                active.definitions.values());
        values.add(definition);
        active = Snapshot.compile(values);
    }

    static boolean has(WorldGenKey key) {
        return active.definitions.containsKey(key);
    }

    static void generate(World world, int chunkX, int chunkZ, String dimension) {
        if (world == null || world.multiplayerWorld) {
            return;
        }
        Snapshot snapshot = active;
        RegionalStructureIndex index = RegionalStructureIndex.get(world);
        if (index == null) {
            return;
        }
        for (RegionalStructureDefinition definition : snapshot.definitions.values()) {
            if (disabled.contains(definition.key) || !matchesDimension(definition, dimension)) {
                continue;
            }
            discoverStarts(snapshot, index, world, definition, dimension, chunkX, chunkZ);
        }
        applyChunk(snapshot, index, world, dimension, chunkX, chunkZ, false);
    }

    static void chunkLoaded(Chunk chunk) {
        if (chunk == null || chunk.worldObj == null || chunk.worldObj.multiplayerWorld || !chunk.isTerrainPopulated
                || active.definitions.isEmpty()) {
            return;
        }
        String dimension = dimension(chunk.worldObj);
        RegionalStructureIndex index = RegionalStructureIndex.get(chunk.worldObj);
        if (index != null) {
            applyChunk(active, index, chunk.worldObj, dimension, chunk.xPosition, chunk.zPosition, true);
        }
    }

    private static void discoverStarts(Snapshot snapshot, RegionalStructureIndex index, World world,
            RegionalStructureDefinition definition, String dimension, int chunkX, int chunkZ) {
        int radius = Math.floorDiv(definition.maxDistance + 15, 16) + 1;
        int minRegionX = Math.floorDiv(chunkX - radius, definition.spacing);
        int maxRegionX = Math.floorDiv(chunkX + radius, definition.spacing);
        int minRegionZ = Math.floorDiv(chunkZ - radius, definition.spacing);
        int maxRegionZ = Math.floorDiv(chunkZ + radius, definition.spacing);
        for (int regionX = minRegionX; regionX <= maxRegionX; regionX++) {
            for (int regionZ = minRegionZ; regionZ <= maxRegionZ; regionZ++) {
                Candidate candidate = candidate(definition, world.getRandomSeed(), dimension, regionX, regionZ);
                String id = id(definition.key, dimension, regionX, regionZ);
                if (index.contains(id)) {
                    continue;
                }
                int startX = (candidate.chunkX << 4) + 8;
                int startZ = (candidate.chunkZ << 4) + 8;
                if (!definition.heightType.equals("fixed") && !world.blockExists(startX, 64, startZ)) {
                    continue;
                }
                try {
                    RegionalStructurePlan plan = plan(world, definition, dimension, regionX, regionZ,
                            candidate);
                    if (plan != null) {
                        index.add(plan);
                        diagnostic(definition.key).starts++;
                        recoverLoadedChunks(snapshot, index, world, dimension, plan);
                    } else {
                        index.reject(id);
                        reject(definition.key, FeatureResult.OUT_OF_BOUNDS);
                    }
                } catch (Throwable error) {
                    disable(definition, error);
                    return;
                }
            }
        }
    }

    private static void recoverLoadedChunks(Snapshot snapshot, RegionalStructureIndex index, World world,
            String dimension, RegionalStructurePlan plan) {
        Set<Long> visited = new HashSet<Long>();
        for (RegionalStructurePlan.Piece piece : plan.pieces) {
            int minChunkX = Math.floorDiv(piece.bounds.min.x, 16);
            int maxChunkX = Math.floorDiv(piece.bounds.max.x, 16);
            int minChunkZ = Math.floorDiv(piece.bounds.min.z, 16);
            int maxChunkZ = Math.floorDiv(piece.bounds.max.z, 16);
            for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
                for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                    long chunkKey = ((long) chunkX << 32) ^ (chunkZ & 0xffffffffL);
                    if (visited.add(Long.valueOf(chunkKey))
                            && world.blockExists((chunkX << 4) + 8, 64, (chunkZ << 4) + 8)) {
                        Chunk loaded = world.getChunkFromChunkCoords(chunkX, chunkZ);
                        if (loaded.isTerrainPopulated) {
                            applyChunk(snapshot, index, world, dimension, chunkX, chunkZ, true);
                        }
                    }
                }
            }
        }
    }

    static RegionalStructurePlan plan(World world, RegionalStructureDefinition definition,
            String dimension, int regionX, int regionZ, Candidate candidate) {
        FeatureDefinition rootDefinition = FeaturePlacementRegistry.findFeature(definition.startFeature);
        if (rootDefinition == null || !(rootDefinition.feature instanceof StructureFeature)) {
            throw new IllegalStateException("Missing root structure feature " + definition.startFeature);
        }
        Random random = new Random(candidate.seed);
        StructureTransform rootTransform = new StructureTransform(
                StructureTransform.Rotation.values()[random.nextInt(StructureTransform.Rotation.values().length)],
                StructureTransform.Mirror.NONE);
        StructureFeature rootFeature = (StructureFeature) rootDefinition.feature;
        StructureFeature.RegionalPlacement rootPlacement = null;
        for (int attempt = 0; attempt < definition.siteSearchAttempts && rootPlacement == null; attempt++) {
            BlockPosition proposed = regionalOrigin(world, definition, candidate, attempt);
            if (proposed == null) {
                continue;
            }
            StructureFeature.RegionalPlacement resolved = resolveRegional(world, definition, rootDefinition,
                    rootFeature, proposed, rootTransform, SeedMixer.derive(candidate.seed, attempt));
            if (resolved.accepted && withinWorld(resolved.bounds)) {
                rootPlacement = resolved;
            }
        }
        if (rootPlacement == null) {
            return null;
        }
        BlockPosition start = rootPlacement.origin;
        StructureFeature.Bounds rootBounds = rootPlacement.bounds;
        List<RegionalStructurePlan.Piece> pieces = new ArrayList<RegionalStructurePlan.Piece>();
        long rootSeed = SeedMixer.derive(candidate.seed, 0L);
        RegionalStructurePlan.Piece root = new RegionalStructurePlan.Piece(definition.startFeature, start,
                rootTransform, rootSeed, 0, rootBounds, rootFeature.generationSignature(),
                terrainChanges(world, rootPlacement), rootPlacement.conformOffsets);
        if (root.terrainChanges.size() > WorldGenLimits.MAX_REGIONAL_TERRAIN_CHANGES) {
            return null;
        }
        pieces.add(root);
        int terrainChangeCount = root.terrainChanges.size();

        Deque<OpenConnector> open = new ArrayDeque<OpenConnector>();
        for (StructureFeature.Connector connector : rootFeature.connectors(start, rootTransform,
                rootPlacement.conformOffsets)) {
            open.addLast(new OpenConnector(connector, 1));
        }
        while (!open.isEmpty() && pieces.size() < definition.maxPieces) {
            OpenConnector parent = open.removeFirst();
            if (parent.depth > definition.maxDepth || random.nextDouble() < definition.terminationChance) {
                continue;
            }
            Attachment attachment = selectAttachment(world, definition, parent.connector, pieces, start, random);
            if (attachment == null) {
                continue;
            }
            if (terrainChangeCount + attachment.terrainChanges.size()
                    > WorldGenLimits.MAX_REGIONAL_TERRAIN_CHANGES) {
                continue;
            }
            StructureFeature feature = structureFeature(attachment.choice.feature);
            long pieceSeed = SeedMixer.derive(candidate.seed, pieces.size());
            RegionalStructurePlan.Piece piece = new RegionalStructurePlan.Piece(attachment.choice.feature,
                    attachment.origin, attachment.transform, pieceSeed, parent.depth, attachment.bounds,
                    feature.generationSignature(), attachment.terrainChanges, attachment.conformOffsets);
            pieces.add(piece);
            terrainChangeCount += attachment.terrainChanges.size();
            for (StructureFeature.Connector connector : feature.connectors(attachment.origin,
                    attachment.transform, attachment.conformOffsets)) {
                if (sameConnector(connector, attachment.usedConnector)) {
                    continue;
                }
                open.addLast(new OpenConnector(connector, parent.depth + 1));
            }
        }
        return new RegionalStructurePlan(definition.key, dimension, regionX, regionZ, candidate.chunkX,
                candidate.chunkZ, candidate.seed, pieces, definition.placementSignature());
    }

    private static Attachment selectAttachment(World world, RegionalStructureDefinition definition,
            StructureFeature.Connector parent, List<RegionalStructurePlan.Piece> placed, BlockPosition start,
            Random random) {
        List<Attachment> candidates = new ArrayList<Attachment>();
        int targetX = parent.position.x + parent.facing.x;
        int targetZ = parent.position.z + parent.facing.z;
        for (RegionalStructureDefinition.PieceChoice choice : definition.pieces) {
            if (!choice.pool.equals(parent.pool)) {
                continue;
            }
            StructureFeature feature = structureFeature(choice.feature);
            if (feature == null) {
                continue;
            }
            for (StructureTransform.Rotation rotation : StructureTransform.Rotation.values()) {
                StructureTransform transform = new StructureTransform(rotation, StructureTransform.Mirror.NONE);
                for (StructureFeature.Connector relative : feature.connectors(new BlockPosition(0, 0, 0),
                        transform)) {
                    if (!relative.pool.equals(parent.pool) || relative.facing != parent.facing.opposite()) {
                        continue;
                    }
                    BlockPosition origin = new BlockPosition(targetX - relative.position.x,
                            parent.position.y - relative.position.y, targetZ - relative.position.z);
                    FeatureDefinition featureDefinition = FeaturePlacementRegistry.findFeature(choice.feature);
                    StructureFeature.RegionalPlacement placement = resolveRegional(world, definition,
                            featureDefinition, feature, origin, transform, random.nextLong());
                    if (!placement.accepted
                            || Math.abs(placement.origin.y - origin.y) > definition.connectorVerticalTolerance) {
                        continue;
                    }
                    StructureFeature.Bounds bounds = placement.bounds;
                    if (!withinWorld(bounds) || !withinDistance(bounds, start, definition.maxDistance)
                            || collides(bounds, placed)) {
                        continue;
                    }
                    StructureFeature.Connector used = matchingConnector(feature, placement.origin, transform,
                            relative, placement.conformOffsets);
                    if (Math.abs(used.position.y - parent.position.y) > definition.connectorVerticalTolerance) {
                        continue;
                    }
                    candidates.add(new Attachment(choice, placement.origin, transform, bounds, used,
                            terrainChanges(world, placement), placement.conformOffsets));
                }
            }
        }
        if (!candidates.isEmpty()) {
            long total = 0L;
            for (Attachment candidate : candidates) {
                total += candidate.choice.weight;
            }
            long selected = (long) (random.nextDouble() * total);
            for (int index = 0; index < candidates.size(); index++) {
                Attachment candidate = candidates.get(index);
                selected -= candidate.choice.weight;
                if (selected < 0) {
                    return candidate;
                }
            }
        }
        return null;
    }

    private static StructureFeature.Connector matchingConnector(StructureFeature feature, BlockPosition origin,
            StructureTransform transform, StructureFeature.Connector relative,
            List<StructureFeature.ConformOffset> conformOffsets) {
        for (StructureFeature.Connector connector : feature.connectors(origin, transform, conformOffsets)) {
            if (connector.pool.equals(relative.pool) && connector.facing == relative.facing
                    && connector.position.x - origin.x == relative.position.x
                    && connector.position.z - origin.z == relative.position.z) {
                return connector;
            }
        }
        throw new IllegalStateException("Resolved connector disappeared from structure feature");
    }

    private static BlockPosition regionalOrigin(World world, RegionalStructureDefinition definition,
            Candidate candidate, int attempt) {
        int centerX = (candidate.chunkX << 4) + 8;
        int centerZ = (candidate.chunkZ << 4) + 8;
        int x = centerX;
        int z = centerZ;
        if (attempt > 0 && definition.siteSearchRadius > 0) {
            Random search = new Random(SeedMixer.derive(candidate.seed, attempt));
            int radius = definition.siteSearchRadius;
            x += search.nextInt(radius * 2 + 1) - radius;
            z += search.nextInt(radius * 2 + 1) - radius;
            int chunkMinX = candidate.chunkX << 4;
            int chunkMinZ = candidate.chunkZ << 4;
            x = Math.max(chunkMinX, Math.min(chunkMinX + 15, x));
            z = Math.max(chunkMinZ, Math.min(chunkMinZ + 15, z));
        }
        int y = regionalHeight(world, definition, x, z, candidate.seed);
        return y < WorldGenLimits.MIN_HEIGHT || y > WorldGenLimits.MAX_HEIGHT
                ? null : new BlockPosition(x, y, z);
    }

    private static int regionalHeight(World world, RegionalStructureDefinition definition, int x, int z, long seed) {
        if (definition.heightType.equals("fixed")) {
            return definition.heightValue;
        }
        TerrainSurface surface;
        try {
            surface = TerrainSurface.parse(definition.heightType);
        } catch (IllegalArgumentException error) {
            return -1;
        }
        FeatureContext context = new FeatureContext(world, new Random(seed), definition.key, 4096, null);
        int height = context.surfaceHeight(x, z, surface);
        return height < 0 ? -1 : height + definition.heightValue;
    }

    private static StructureFeature.RegionalPlacement resolveRegional(World world,
            RegionalStructureDefinition definition, FeatureDefinition featureDefinition, StructureFeature feature,
            BlockPosition origin, StructureTransform transform, long seed) {
        FeatureContext context = new FeatureContext(world, new Random(seed), featureDefinition.key,
                Math.max(4096, feature.maximumReads()), null, FeatureOptions.DEFAULT, definition.site);
        return feature.resolveRegional(context, origin, transform, definition.site);
    }

    private static List<RegionalStructurePlan.Piece.TerrainChange> terrainChanges(World world,
            StructureFeature.RegionalPlacement placement) {
        List<RegionalStructurePlan.Piece.TerrainChange> result =
                new ArrayList<RegionalStructurePlan.Piece.TerrainChange>();
        for (PlacementPlan.PlannedBlock block : placement.terrainChanges) {
            result.add(new RegionalStructurePlan.Piece.TerrainChange(block.position, block.blockId, block.metadata,
                    world.getBlockId(block.position.x, block.position.y, block.position.z),
                    world.getBlockMetadata(block.position.x, block.position.y, block.position.z)));
        }
        return result;
    }

    private static boolean withinDistance(StructureFeature.Bounds bounds, BlockPosition start, int distance) {
        return Math.abs(bounds.min.x - start.x) <= distance && Math.abs(bounds.max.x - start.x) <= distance
                && Math.abs(bounds.min.z - start.z) <= distance && Math.abs(bounds.max.z - start.z) <= distance;
    }

    private static boolean withinWorld(StructureFeature.Bounds bounds) {
        return bounds.min.x >= -30000000 && bounds.max.x <= 30000000
                && bounds.min.z >= -30000000 && bounds.max.z <= 30000000
                && bounds.min.y >= WorldGenLimits.MIN_HEIGHT && bounds.max.y <= WorldGenLimits.MAX_HEIGHT;
    }

    private static boolean collides(StructureFeature.Bounds bounds, List<RegionalStructurePlan.Piece> pieces) {
        for (RegionalStructurePlan.Piece piece : pieces) {
            if (bounds.intersects(piece.bounds)) {
                return true;
            }
        }
        return false;
    }

    private static boolean sameConnector(StructureFeature.Connector left, StructureFeature.Connector right) {
        return left.pool.equals(right.pool) && left.position.equals(right.position) && left.facing == right.facing;
    }

    private static void applyChunk(Snapshot snapshot, RegionalStructureIndex index, World world, String dimension,
            int chunkX, int chunkZ, boolean recovery) {
        for (RegionalStructurePlan plan : index.forChunk(chunkX, chunkZ)) {
            RegionalStructureDefinition definition = snapshot.definitions.get(plan.definitionKey);
            if (definition == null || disabled.contains(plan.definitionKey) || !plan.dimension.equals(dimension)
                    || plan.isComplete(chunkX, chunkZ)) {
                continue;
            }
            try {
                if (!plan.placementSignature.isEmpty()
                        && !plan.placementSignature.equals(definition.placementSignature())) {
                    reject(definition.key, "definition_changed");
                    continue;
                }
                FeatureContext context = new FeatureContext(world, new Random(plan.seed), plan.definitionKey,
                        WorldGenLimits.MAX_REGIONAL_BLOCKS_PER_CHUNK * 32, new FeatureContext.FeatureResolver() {
                            @Override
                            public FeatureDefinition find(WorldGenKey key) {
                                return FeaturePlacementRegistry.findFeature(key);
                            }
                        });
                PlacementPlan changes = new PlacementPlan(
                        new BlockPosition((chunkX << 4) + 8, 64, (chunkZ << 4) + 8),
                        WorldGenLimits.MAX_REGIONAL_BLOCKS_PER_CHUNK, 128);
                for (RegionalStructurePlan.Piece piece : plan.pieces) {
                    if (!piece.bounds.intersectsChunk(chunkX, chunkZ)) {
                        continue;
                    }
                    StructureFeature feature = structureFeature(piece.featureKey);
                    if (feature == null) {
                        throw new IllegalStateException("Missing structure feature " + piece.featureKey);
                    }
                    if (!piece.generationSignature.equals(feature.generationSignature())) {
                        reject(definition.key, "definition_changed");
                        return;
                    }
                    for (RegionalStructurePlan.Piece.TerrainChange terrain : piece.terrainChanges) {
                        if (Math.floorDiv(terrain.position.x, 16) == chunkX
                                && Math.floorDiv(terrain.position.z, 16) == chunkZ) {
                            int existing = context.blockId(terrain.position.x, terrain.position.y,
                                    terrain.position.z);
                            int metadata = context.metadata(terrain.position.x, terrain.position.y,
                                    terrain.position.z);
                            if (existing < 0 || metadata < 0) {
                                reject(definition.key, context.failure());
                                return;
                            }
                            boolean alreadyApplied = existing == terrain.blockId && metadata == terrain.metadata;
                            boolean unchanged = terrain.expectedBlockId < 0
                                    || existing == terrain.expectedBlockId && metadata == terrain.expectedMetadata;
                            if (!alreadyApplied && (!unchanged
                                    || context.hasTileEntity(terrain.position.x, terrain.position.y,
                                            terrain.position.z))) {
                                reject(definition.key, FeatureResult.TERRAIN_PROTECTED_BLOCK);
                                return;
                            }
                            if (!changes.setBlock(terrain.position.x, terrain.position.y, terrain.position.z,
                                    terrain.blockId, terrain.metadata)) {
                                reject(definition.key, changes.failure());
                                return;
                            }
                        }
                    }
                    FeatureResult result = feature.planChunk(context, piece.origin, changes, piece.transform,
                            piece.seed, chunkX, chunkZ, recovery, piece.conformOffsets);
                    if (!result.placed && !FeatureResult.NO_CHANGES.equals(result.reason)) {
                        reject(definition.key, result.reason);
                        return;
                    }
                }
                FeatureResult result = changes.size() == 0 ? FeatureResult.placed(0, null, null)
                        : changes.commit(context);
                if (!result.placed) {
                    reject(definition.key, result.reason);
                    return;
                }
                int markerFailures = RegionalStructureMarkers.apply(world, definition, plan, chunkX, chunkZ);
                if (markerFailures > 0) {
                    reject(definition.key, "marker_error");
                }
                plan.complete(chunkX, chunkZ);
                index.changed(plan);
                MutableDiagnostics values = diagnostic(definition.key);
                values.completedChunks++;
                values.blocksChanged += result.blocksChanged;
                if (recovery) {
                    values.recoveredChunks++;
                }
            } catch (Throwable error) {
                disable(definition, error);
            }
        }
    }

    static Location locate(World world, WorldGenKey key, int blockX, int blockZ, int maxRegions) {
        RegionalStructureDefinition definition = active.definitions.get(key);
        if (definition == null) {
            return null;
        }
        String dimension = dimension(world);
        if (!matchesDimension(definition, dimension)) {
            return null;
        }
        int centerRegionX = Math.floorDiv(Math.floorDiv(blockX, 16), definition.spacing);
        int centerRegionZ = Math.floorDiv(Math.floorDiv(blockZ, 16), definition.spacing);
        Location best = null;
        long bestDistance = Long.MAX_VALUE;
        RegionalStructureIndex index = RegionalStructureIndex.get(world);
        for (int radius = 0; radius <= maxRegions; radius++) {
            for (int x = centerRegionX - radius; x <= centerRegionX + radius; x++) {
                for (int z = centerRegionZ - radius; z <= centerRegionZ + radius; z++) {
                    if (radius > 0 && x != centerRegionX - radius && x != centerRegionX + radius
                            && z != centerRegionZ - radius && z != centerRegionZ + radius) {
                        continue;
                    }
                    Candidate candidate = candidate(definition, world.getRandomSeed(), dimension, x, z);
                    int candidateX = (candidate.chunkX << 4) + 8;
                    int candidateZ = (candidate.chunkZ << 4) + 8;
                    long deltaX = (long) candidateX - blockX;
                    long deltaZ = (long) candidateZ - blockZ;
                    long distance = deltaX * deltaX + deltaZ * deltaZ;
                    if (distance >= bestDistance) {
                        continue;
                    }
                    RegionalStructurePlan saved = index == null ? null
                            : index.find(id(key, dimension, x, z));
                    BlockPosition savedOrigin = saved == null ? null : saved.pieces.get(0).origin;
                    Integer y = savedOrigin == null ? null : Integer.valueOf(savedOrigin.y);
                    best = new Location(savedOrigin == null ? candidateX : savedOrigin.x, y,
                            savedOrigin == null ? candidateZ : savedOrigin.z, saved != null,
                            savedOrigin == null ? candidate.chunkX : Math.floorDiv(savedOrigin.x, 16),
                            savedOrigin == null ? candidate.chunkZ : Math.floorDiv(savedOrigin.z, 16));
                    bestDistance = distance;
                }
            }
        }
        return best;
    }

    static Candidate candidate(RegionalStructureDefinition definition, long worldSeed, String dimension,
            int regionX, int regionZ) {
        long seed = SeedMixer.generationSeed(worldSeed, dimension, "large_structures", regionX, regionZ,
                definition.key, definition.salt);
        Random random = new Random(seed);
        int spread = definition.spacing - definition.separation;
        int chunkX = regionX * definition.spacing + random.nextInt(spread);
        int chunkZ = regionZ * definition.spacing + random.nextInt(spread);
        return new Candidate(chunkX, chunkZ, seed);
    }

    static List<Description> snapshot() {
        List<Description> result = new ArrayList<Description>();
        for (RegionalStructureDefinition definition : active.definitions.values()) {
            result.add(new Description(definition, diagnostics.get(definition.key.toString()),
                    disabled.contains(definition.key)));
        }
        return Collections.unmodifiableList(result);
    }

    private static StructureFeature structureFeature(WorldGenKey key) {
        FeatureDefinition definition = FeaturePlacementRegistry.findFeature(key);
        return definition != null && definition.feature instanceof StructureFeature
                ? (StructureFeature) definition.feature : null;
    }

    private static boolean matchesDimension(RegionalStructureDefinition definition, String dimension) {
        return definition.dimensions.isEmpty() || definition.dimensions.contains(dimension);
    }

    private static String dimension(World world) {
        return world.worldProvider.worldType == -1 ? "minecraft:nether" : "minecraft:overworld";
    }

    private static String id(WorldGenKey key, String dimension, int regionX, int regionZ) {
        return key + "@" + dimension + "@" + regionX + "," + regionZ;
    }

    private static synchronized MutableDiagnostics diagnostic(WorldGenKey key) {
        MutableDiagnostics values = diagnostics.get(key.toString());
        if (values == null) {
            values = new MutableDiagnostics();
            diagnostics.put(key.toString(), values);
        }
        return values;
    }

    private static void reject(WorldGenKey key, String reason) {
        MutableDiagnostics values = diagnostic(key);
        values.rejectedChunks++;
        String stable = reason == null ? "unknown" : reason;
        Integer count = values.reasons.get(stable);
        values.reasons.put(stable, Integer.valueOf(count == null ? 1 : count.intValue() + 1));
    }

    private static synchronized void disable(RegionalStructureDefinition definition, Throwable error) {
        disabled.add(definition.key);
        reject(definition.key, FeatureResult.RUNTIME_ERROR);
        String detail = error.getMessage() == null ? error.toString() : error.getMessage();
        String message = "Regional structure " + definition.key + " was disabled after an error: " + detail;
        BetaMoonCommon.LOGGER.warning(definition.owner + ": " + message);
        LuaScriptErrors.add(definition.owner, message);
    }

    private static final class Snapshot {
        private final Map<WorldGenKey, RegionalStructureDefinition> definitions;

        private Snapshot(Map<WorldGenKey, RegionalStructureDefinition> definitions) {
            this.definitions = Collections.unmodifiableMap(definitions);
        }

        private static Snapshot empty() {
            return new Snapshot(new LinkedHashMap<WorldGenKey, RegionalStructureDefinition>());
        }

        private static Snapshot compile(List<RegionalStructureDefinition> values) {
            return compile(values, Collections.<FeatureDefinition>emptyList());
        }

        private static Snapshot compile(List<RegionalStructureDefinition> values,
                List<FeatureDefinition> stagedFeatures) {
            Collections.sort(values, new Comparator<RegionalStructureDefinition>() {
                @Override
                public int compare(RegionalStructureDefinition left, RegionalStructureDefinition right) {
                    return left.key.compareTo(right.key);
                }
            });
            Map<WorldGenKey, RegionalStructureDefinition> definitions =
                    new LinkedHashMap<WorldGenKey, RegionalStructureDefinition>();
            for (RegionalStructureDefinition definition : values) {
                RegionalStructureDefinition conflict = definitions.put(definition.key, definition);
                if (conflict != null) {
                    throw new LuaError("Regional structure key " + definition.key + " is declared by both "
                            + conflict.owner + " and " + definition.owner);
                }
                StructureFeature root = structureFeature(definition.startFeature, stagedFeatures);
                if (root == null) {
                    throw new LuaError("Regional structure " + definition.key
                            + " references unknown local structure " + definition.startFeature);
                }
                validateMarkers(definition, root);
                for (RegionalStructureDefinition.PieceChoice choice : definition.pieces) {
                    StructureFeature feature = structureFeature(choice.feature, stagedFeatures);
                    if (feature == null) {
                        throw new LuaError("Regional structure " + definition.key
                                + " references unknown local structure " + choice.feature);
                    }
                    if (!feature.hasConnectorPool(choice.pool)) {
                        throw new LuaError("Regional structure piece " + choice.feature
                                + " has no connector for pool '" + choice.pool + "'");
                    }
                    validateMarkers(definition, feature);
                }
            }
            return new Snapshot(definitions);
        }

        private static void validateMarkers(RegionalStructureDefinition definition, StructureFeature feature) {
            try {
                RegionalStructureMarkers.validate(feature, definition.entityMarkers, definition.lootMarkers);
            } catch (IllegalArgumentException error) {
                throw new LuaError("Regional structure " + definition.key + ": " + error.getMessage());
            }
        }

        private static StructureFeature structureFeature(WorldGenKey key, List<FeatureDefinition> stagedFeatures) {
            for (FeatureDefinition definition : stagedFeatures) {
                if (definition.key.equals(key) && definition.feature instanceof StructureFeature) {
                    return (StructureFeature) definition.feature;
                }
            }
            return RegionalStructureRegistry.structureFeature(key);
        }
    }

    static final class Candidate {
        final int chunkX;
        final int chunkZ;
        final long seed;

        Candidate(int chunkX, int chunkZ, long seed) {
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.seed = seed;
        }
    }

    static final class Location {
        final int x;
        final Integer y;
        final int z;
        final boolean generated;
        final int chunkX;
        final int chunkZ;

        Location(int x, Integer y, int z, boolean generated, int chunkX, int chunkZ) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.generated = generated;
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
        }
    }

    static final class Description {
        final String key;
        final String owner;
        final String source;
        final String start;
        final Set<String> dimensions;
        final int spacing;
        final int separation;
        final long salt;
        final String height;
        final int pools;
        final int maxDepth;
        final int maxPieces;
        final int maxDistance;
        final double terminationChance;
        final String site;
        final int siteSearchAttempts;
        final int siteSearchRadius;
        final int connectorVerticalTolerance;
        final long starts;
        final long completedChunks;
        final long recoveredChunks;
        final long rejectedChunks;
        final long blocksChanged;
        final Map<String, Integer> rejectionReasons;
        final boolean disabled;

        private Description(RegionalStructureDefinition definition, MutableDiagnostics values, boolean disabled) {
            key = definition.key.toString();
            owner = definition.owner;
            source = definition.sourceLocation;
            start = definition.startFeature.toString();
            dimensions = definition.dimensions;
            spacing = definition.spacing;
            separation = definition.separation;
            salt = definition.salt;
            height = definition.heightType + ":" + definition.heightValue;
            pools = definition.pieces.size();
            maxDepth = definition.maxDepth;
            maxPieces = definition.maxPieces;
            maxDistance = definition.maxDistance;
            terminationChance = definition.terminationChance;
            site = definition.site.type.luaName();
            siteSearchAttempts = definition.siteSearchAttempts;
            siteSearchRadius = definition.siteSearchRadius;
            connectorVerticalTolerance = definition.connectorVerticalTolerance;
            starts = values == null ? 0 : values.starts;
            completedChunks = values == null ? 0 : values.completedChunks;
            recoveredChunks = values == null ? 0 : values.recoveredChunks;
            rejectedChunks = values == null ? 0 : values.rejectedChunks;
            blocksChanged = values == null ? 0 : values.blocksChanged;
            rejectionReasons = values == null ? Collections.<String, Integer>emptyMap()
                    : Collections.unmodifiableMap(new LinkedHashMap<String, Integer>(values.reasons));
            this.disabled = disabled;
        }
    }

    private static final class OpenConnector {
        private final StructureFeature.Connector connector;
        private final int depth;

        private OpenConnector(StructureFeature.Connector connector, int depth) {
            this.connector = connector;
            this.depth = depth;
        }
    }

    private static final class Attachment {
        private final RegionalStructureDefinition.PieceChoice choice;
        private final BlockPosition origin;
        private final StructureTransform transform;
        private final StructureFeature.Bounds bounds;
        private final StructureFeature.Connector usedConnector;
        private final List<RegionalStructurePlan.Piece.TerrainChange> terrainChanges;
        private final List<StructureFeature.ConformOffset> conformOffsets;

        private Attachment(RegionalStructureDefinition.PieceChoice choice, BlockPosition origin,
                StructureTransform transform, StructureFeature.Bounds bounds,
                StructureFeature.Connector usedConnector,
                List<RegionalStructurePlan.Piece.TerrainChange> terrainChanges,
                List<StructureFeature.ConformOffset> conformOffsets) {
            this.choice = choice;
            this.origin = origin;
            this.transform = transform;
            this.bounds = bounds;
            this.usedConnector = usedConnector;
            this.terrainChanges = terrainChanges;
            this.conformOffsets = conformOffsets;
        }
    }

    private static final class MutableDiagnostics {
        private long starts;
        private long completedChunks;
        private long recoveredChunks;
        private long rejectedChunks;
        private long blocksChanged;
        private final Map<String, Integer> reasons = new LinkedHashMap<String, Integer>();
    }
}
