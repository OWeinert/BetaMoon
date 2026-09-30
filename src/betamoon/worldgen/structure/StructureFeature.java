package betamoon.worldgen.structure;

import betamoon.worldgen.BlockPosition;
import betamoon.worldgen.FeatureContext;
import betamoon.worldgen.FeatureResult;
import betamoon.worldgen.PlacementPlan;
import betamoon.worldgen.SeedMixer;
import betamoon.worldgen.WorldFeature;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

/** Atomic local placement of one validated structure template. */
public final class StructureFeature implements WorldFeature {
    private final StructureTemplate template;
    private final String assetSource;
    private final String defaultRotation;
    private final String defaultMirror;
    private final StructureProcessors processors;
    private final TerrainPolicy terrain;
    private final StructureTerrainMask terrainMask;
    private final List<Connector> connectors;

    public StructureFeature(StructureTemplate template, String assetSource, String defaultRotation,
            String defaultMirror, StructureProcessors processors) {
        this(template, assetSource, defaultRotation, defaultMirror, processors, TerrainPolicy.EXACT);
    }

    public StructureFeature(StructureTemplate template, String assetSource, String defaultRotation,
            String defaultMirror, StructureProcessors processors, TerrainPolicy terrain) {
        this.template = template;
        this.assetSource = assetSource;
        this.defaultRotation = defaultRotation;
        this.defaultMirror = defaultMirror;
        this.processors = processors;
        this.terrain = terrain == null ? TerrainPolicy.EXACT : terrain;
        terrainMask = new StructureTerrainMask(template);
        connectors = Collections.unmodifiableList(parseConnectors(template));
    }

    @Override
    public FeatureResult plan(FeatureContext context, BlockPosition origin, PlacementPlan output) {
        StructureTransform transform;
        try {
            transform = StructureTransform.select(context.options().rotation, context.options().mirror,
                    defaultRotation, defaultMirror, context.random());
        } catch (IllegalArgumentException error) {
            return FeatureResult.rejected(FeatureResult.BLOCKED);
        }
        long placementSeed = context.random().nextLong();
        StructureTemplate.Resolved resolved;
        try {
            resolved = template.resolve(placementSeed);
        } catch (IllegalStateException error) {
            return FeatureResult.rejected(FeatureResult.BLOCKED);
        }
        StructureTerrainMask resolvedTerrainMask = new StructureTerrainMask(resolved, template);
        StructureTerrainPlanner.Result placement = StructureTerrainPlanner.prepare(context, origin, transform,
                terrain, context.sitePolicy(), resolvedTerrainMask,
                bounds(new BlockPosition(0, 0, 0), transform), output);
        if (placement.failure != null) {
            return FeatureResult.rejected(placement.failure, context.diagnostics());
        }
        return plan(context, placement.origin, output, transform, resolved, placementSeed, false,
                Integer.MIN_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE,
                placement.conformOffsets());
    }

    /** Plans only the part of a deterministic regional piece contained by one chunk. */
    public FeatureResult planChunk(FeatureContext context, BlockPosition origin, PlacementPlan output,
            StructureTransform transform, long pieceSeed, int chunkX, int chunkZ, boolean recovery) {
        return planChunk(context, origin, output, transform, pieceSeed, chunkX, chunkZ, recovery,
                Collections.<ConformOffset>emptyList());
    }

    /** Plans a saved regional piece with its resolved terrain-conforming column offsets. */
    public FeatureResult planChunk(FeatureContext context, BlockPosition origin, PlacementPlan output,
            StructureTransform transform, long pieceSeed, int chunkX, int chunkZ, boolean recovery,
            List<ConformOffset> conformOffsets) {
        StructureTemplate.Resolved resolved;
        try {
            resolved = template.resolve(pieceSeed);
        } catch (IllegalStateException error) {
            return FeatureResult.rejected(FeatureResult.BLOCKED);
        }
        return plan(context, origin, output, transform, resolved, pieceSeed, recovery, chunkX << 4,
                (chunkX << 4) + 15, chunkZ << 4, (chunkZ << 4) + 15, conformOffsets);
    }

    private FeatureResult plan(FeatureContext context, BlockPosition origin, PlacementPlan output,
            StructureTransform transform, StructureTemplate.Resolved resolved, long placementSeed, boolean recovery,
            int minX, int maxX, int minZ, int maxZ, List<ConformOffset> conformOffsets) {
        Map<BlockPosition, Map<String, LuaValue>> markerData = markerData(resolved.markers);
        int before = output.size();
        for (StructureTemplate.TemplateBlock block : resolved.blocks) {
            Random random = new Random(blockSeed(placementSeed, block));
            StructureTemplate.State selected = template.palette.get(block.state).select(random);
            if (selected.blockId == 0 && !processors.includeAir) {
                continue;
            }
            if (processors.decay > 0.0D && random.nextDouble() < processors.decay) {
                continue;
            }
            int blockId = selected.blockId;
            Integer replacement = processors.replacements.get(Integer.valueOf(blockId));
            if (replacement != null) {
                blockId = replacement.intValue();
            }
            BlockPosition relative = transform.apply(block.position.x - template.origin.x,
                    block.position.y - template.origin.y, block.position.z - template.origin.z);
            int conformOffset = conformOffset(conformOffsets, relative.x, relative.z);
            int x = origin.x + relative.x;
            int y = origin.y + relative.y + conformOffset;
            int z = origin.z + relative.z;
            if (x < minX || x > maxX || z < minZ || z > maxZ) {
                continue;
            }
            int existing = context.blockId(x, y, z);
            if (existing < 0) {
                return FeatureResult.rejected(context.failure());
            }
            if (processors.allowedExisting != null && !processors.allowedExisting.contains(existing)) {
                return FeatureResult.rejected(FeatureResult.PROTECTED);
            }
            if (context.hasTileEntity(x, y, z) && !(recovery && existing == blockId)) {
                if (processors.tileCollision.equals("preserve")) {
                    continue;
                }
                return FeatureResult.rejected(FeatureResult.PROTECTED);
            }
            int metadata;
            try {
                metadata = BlockStateTransformRegistry.transform(blockId, selected.metadata,
                        transform.compose(block.localTransform),
                        processors.unknownMetadata, processors.metadataTransforms.get(Integer.valueOf(blockId)));
            } catch (IllegalArgumentException error) {
                return FeatureResult.rejected(FeatureResult.BLOCKED);
            }
            Map<String, LuaValue> data = luaData(selected.tileData);
            Map<String, LuaValue> marker = markerData.get(block.position);
            if (marker != null) {
                data.putAll(marker);
            }
            if (!output.setBlock(x, y, z, blockId, metadata, data)) {
                return FeatureResult.rejected(output.failure());
            }
        }
        for (StructureLoot loot : resolved.loots) {
            BlockPosition relative = transform.apply(loot.position.x - template.origin.x,
                    loot.position.y - template.origin.y, loot.position.z - template.origin.z);
            int conformOffset = conformOffset(conformOffsets, relative.x, relative.z);
            int x = origin.x + relative.x;
            int y = origin.y + relative.y + conformOffset;
            int z = origin.z + relative.z;
            if (x < minX || x > maxX || z < minZ || z > maxZ) {
                continue;
            }
            if (!output.contains(x, y, z)) {
                return FeatureResult.rejected(FeatureResult.BLOCKED);
            }
            long lootSeed = SeedMixer.derive(placementSeed,
                    SeedMixer.hash(loot.key == null ? loot.semantics() : loot.key));
            lootSeed = SeedMixer.derive(lootSeed, coordinateSeed(x, y, z));
            try {
                if (!output.addInventoryLoot(x, y, z, loot.plan(lootSeed))) {
                    return FeatureResult.rejected(output.failure());
                }
            } catch (IllegalStateException error) {
                return FeatureResult.rejected(FeatureResult.BLOCKED);
            }
        }
        int added = output.size() - before;
        return added == 0 ? FeatureResult.rejected(FeatureResult.NO_CHANGES)
                : FeatureResult.placed(added, null, null);
    }

    public Bounds bounds(BlockPosition origin, StructureTransform transform) {
        BlockPosition min = null;
        BlockPosition max = null;
        int[] xs = new int[]{template.minimum.x, template.maximum.x};
        int[] ys = new int[]{template.minimum.y, template.maximum.y};
        int[] zs = new int[]{template.minimum.z, template.maximum.z};
        for (int x : xs) {
            for (int y : ys) {
                for (int z : zs) {
                    BlockPosition relative = transform.apply(x, y, z);
                    BlockPosition position = origin.offset(relative.x, relative.y, relative.z);
                    min = min == null ? position : new BlockPosition(Math.min(min.x, position.x),
                            Math.min(min.y, position.y), Math.min(min.z, position.z));
                    max = max == null ? position : new BlockPosition(Math.max(max.x, position.x),
                            Math.max(max.y, position.y), Math.max(max.z, position.z));
                }
            }
        }
        return new Bounds(min, max);
    }

    public Bounds bounds(BlockPosition origin, StructureTransform transform, List<ConformOffset> offsets) {
        if (offsets == null || offsets.isEmpty()) {
            return bounds(origin, transform);
        }
        BlockPosition min = null;
        BlockPosition max = null;
        for (StructureTemplate.TemplateBlock block : template.blocks) {
            BlockPosition relative = transform.apply(block.position.x - template.origin.x,
                    block.position.y - template.origin.y, block.position.z - template.origin.z);
            BlockPosition position = origin.offset(relative.x,
                    relative.y + conformOffset(offsets, relative.x, relative.z), relative.z);
            min = min == null ? position : new BlockPosition(Math.min(min.x, position.x),
                    Math.min(min.y, position.y), Math.min(min.z, position.z));
            max = max == null ? position : new BlockPosition(Math.max(max.x, position.x),
                    Math.max(max.y, position.y), Math.max(max.z, position.z));
        }
        return new Bounds(min == null ? origin : min, max == null ? origin : max);
    }

    public List<Connector> connectors(BlockPosition origin, StructureTransform transform) {
        return connectors(origin, transform, Collections.<ConformOffset>emptyList());
    }

    public List<Connector> connectors(BlockPosition origin, StructureTransform transform,
            List<ConformOffset> offsets) {
        return positionedConnectors(connectors, origin, transform, offsets);
    }

    /** Resolves conditional connectors from the persisted regional piece seed. */
    public List<Connector> connectors(BlockPosition origin, StructureTransform transform,
            List<ConformOffset> offsets, long placementSeed) {
        return positionedConnectors(parseConnectors(template.resolve(placementSeed).markers), origin, transform,
                offsets);
    }

    private static List<Connector> positionedConnectors(List<Connector> connectors, BlockPosition origin,
            StructureTransform transform, List<ConformOffset> offsets) {
        List<Connector> result = new ArrayList<Connector>();
        for (Connector connector : connectors) {
            BlockPosition relative = transform.apply(connector.position.x, connector.position.y,
                    connector.position.z);
            result.add(new Connector(connector.pool, origin.offset(relative.x,
                    relative.y + conformOffset(offsets, relative.x, relative.z), relative.z),
                    transform.apply(connector.facing)));
        }
        return Collections.unmodifiableList(result);
    }

    private static int conformOffset(List<ConformOffset> offsets, int x, int z) {
        if (offsets == null) {
            return 0;
        }
        for (ConformOffset offset : offsets) {
            if (offset.x == x && offset.z == z) {
                return offset.y;
            }
        }
        return 0;
    }

    public boolean hasConnectorPool(String pool) {
        for (Connector connector : connectors) {
            if (connector.pool.equals(pool)) {
                return true;
            }
        }
        return false;
    }

    public String generationSignature() {
        return generationSignature(template, processors, terrain);
    }

    /** Resolves every external/nested loot dependency while the publication batch is active. */
    public void validateLootReferences() throws java.io.IOException {
        for (StructureLoot loot : template.loots) {
            loot.validateReferences();
        }
    }

    public List<PositionedMarker> markers(BlockPosition origin, StructureTransform transform) {
        return markers(origin, transform, Collections.<ConformOffset>emptyList());
    }

    public List<PositionedMarker> markers(BlockPosition origin, StructureTransform transform,
            List<ConformOffset> offsets) {
        return positionedMarkers(template.markers, origin, transform, offsets);
    }

    /** Resolves placement-time marker variation using the same persisted seed as the piece blocks. */
    public List<PositionedMarker> markers(BlockPosition origin, StructureTransform transform,
            List<ConformOffset> offsets, long placementSeed) {
        return positionedMarkers(template.resolve(placementSeed).markers, origin, transform, offsets);
    }

    private static List<PositionedMarker> positionedMarkers(List<StructureTemplate.Marker> markers,
            BlockPosition origin, StructureTransform transform, List<ConformOffset> offsets) {
        List<PositionedMarker> result = new ArrayList<PositionedMarker>();
        for (StructureTemplate.Marker marker : markers) {
            BlockPosition relative = transform.apply(marker.position.x, marker.position.y, marker.position.z);
            result.add(new PositionedMarker(origin.offset(relative.x,
                    relative.y + conformOffset(offsets, relative.x, relative.z), relative.z), marker.name,
                    marker.value));
        }
        return Collections.unmodifiableList(result);
    }

    private static List<Connector> parseConnectors(StructureTemplate template) {
        return parseConnectors(template.markers);
    }

    private static List<Connector> parseConnectors(List<StructureTemplate.Marker> markers) {
        List<Connector> result = new ArrayList<Connector>();
        for (StructureTemplate.Marker marker : markers) {
            if (!marker.name.equals("connector")) {
                continue;
            }
            if (!(marker.value instanceof Map)) {
                throw new IllegalArgumentException("connector marker value must be an object");
            }
            Map<?, ?> value = (Map<?, ?>) marker.value;
            Object poolValue = value.get("pool");
            Object facingValue = value.get("facing");
            if (!(poolValue instanceof String) || !((String) poolValue).matches("[a-z][a-z0-9_.-]{0,63}")) {
                throw new IllegalArgumentException("connector marker pool must be a lowercase identifier");
            }
            if (!(facingValue instanceof String)) {
                throw new IllegalArgumentException("connector marker facing must be north, east, south, or west");
            }
            BlockPosition relative = marker.position;
            result.add(new Connector((String) poolValue, relative,
                    StructureTransform.Direction.parse((String) facingValue)));
            if (result.size() > 64) {
                throw new IllegalArgumentException("structure contains more than 64 connector markers");
            }
        }
        return result;
    }

    private static String generationSignature(StructureTemplate template, StructureProcessors processors,
            TerrainPolicy terrain) {
        StringBuilder canonical = new StringBuilder(template.contentHash);
        canonical.append('|').append(processors.includeAir);
        canonical.append('|').append(Double.doubleToLongBits(processors.decay));
        canonical.append('|').append(processors.tileCollision).append('|').append(processors.unknownMetadata);
        canonical.append('|').append(terrain.signature());
        canonical.append('|').append(new TreeMap<Integer, Integer>(processors.replacements));
        canonical.append('|').append(processors.allowedExisting == null ? "*"
                : java.util.Arrays.toString(processors.allowedExisting.values()));
        for (Map.Entry<Integer, CustomMetadataTransform> entry
                : new TreeMap<Integer, CustomMetadataTransform>(processors.metadataTransforms).entrySet()) {
            canonical.append('|').append(entry.getKey()).append('=').append(entry.getValue().generationSignature());
        }
        for (StructureLoot loot : template.loots) {
            canonical.append("|loot=").append(loot.semantics()).append(':')
                    .append(loot.dependencySignature());
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes("UTF-8"));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                result.append(String.format(java.util.Locale.ROOT, "%02x", value & 0xff));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        } catch (java.io.UnsupportedEncodingException impossible) {
            throw new IllegalStateException("UTF-8 is unavailable", impossible);
        }
    }

    private Map<BlockPosition, Map<String, LuaValue>> markerData(List<StructureTemplate.Marker> markers) {
        Map<BlockPosition, Map<String, LuaValue>> result = new LinkedHashMap<BlockPosition, Map<String, LuaValue>>();
        for (StructureTemplate.Marker marker : markers) {
            if (!marker.name.equals("tile_data") && !marker.name.equals("data")) {
                continue;
            }
            if (!(marker.value instanceof Map)) {
                continue;
            }
            result.put(marker.position, luaData(castMap(marker.value)));
        }
        return result;
    }

    private static long blockSeed(long placementSeed, StructureTemplate.TemplateBlock block) {
        long coordinate = ((long) block.position.x & 0x1fffffL) << 42;
        coordinate ^= ((long) block.position.y & 0x1fffffL) << 21;
        coordinate ^= (long) block.position.z & 0x1fffffL;
        return SeedMixer.derive(SeedMixer.derive(placementSeed, coordinate), block.state);
    }

    private static long coordinateSeed(int x, int y, int z) {
        long coordinate = ((long) x & 0x1fffffL) << 42;
        coordinate ^= ((long) y & 0x1fffffL) << 21;
        coordinate ^= (long) z & 0x1fffffL;
        return coordinate;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object value) {
        return (Map<String, Object>) value;
    }

    private static Map<String, LuaValue> luaData(Map<String, Object> source) {
        if (source.isEmpty()) {
            return new LinkedHashMap<String, LuaValue>();
        }
        Map<String, LuaValue> result = new LinkedHashMap<String, LuaValue>();
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            result.put(entry.getKey(), lua(entry.getValue()));
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static LuaValue lua(Object value) {
        if (value == null) {
            return LuaValue.NIL;
        }
        if (value instanceof Boolean) {
            return LuaValue.valueOf(((Boolean) value).booleanValue());
        }
        if (value instanceof Number) {
            return LuaValue.valueOf(((Number) value).doubleValue());
        }
        if (value instanceof String) {
            return LuaValue.valueOf((String) value);
        }
        LuaTable table = new LuaTable();
        if (value instanceof List) {
            List<Object> values = (List<Object>) value;
            for (int index = 0; index < values.size(); index++) {
                table.set(index + 1, lua(values.get(index)));
            }
            return table;
        }
        if (value instanceof Map) {
            for (Map.Entry<String, Object> entry : ((Map<String, Object>) value).entrySet()) {
                table.set(entry.getKey(), lua(entry.getValue()));
            }
            return table;
        }
        return LuaValue.valueOf(String.valueOf(value));
    }

    public Description description() {
        int variants = 0;
        for (StructureTemplate.PaletteEntry entry : template.palette) {
            variants += entry.variants();
        }
        return new Description(assetSource, template.sizeX, template.sizeY, template.sizeZ, template.palette.size(),
                variants, template.blocks.size(), template.markers.size(), defaultRotation, defaultMirror,
                processors.includeAir, processors.decay, processors.tileCollision, processors.unknownMetadata,
                processors.metadataTransforms.size(), terrain.mode.luaName(), terrain.surface.getName(),
                terrainMask.supportColumns());
    }

    /** Resolves regional terrain once so later chunk recovery never resamples a partially generated site. */
    public RegionalPlacement resolveRegional(FeatureContext context, BlockPosition requestedOrigin,
            StructureTransform transform, SitePolicy site) {
        return resolveRegional(context, requestedOrigin, transform, site, 0L);
    }

    /** Resolves regional terrain from the same deterministic seed later used for piece placement. */
    public RegionalPlacement resolveRegional(FeatureContext context, BlockPosition requestedOrigin,
            StructureTransform transform, SitePolicy site, long placementSeed) {
        StructureTemplate.Resolved resolved;
        try {
            resolved = template.resolve(placementSeed);
        } catch (IllegalStateException error) {
            return RegionalPlacement.rejected(FeatureResult.BLOCKED);
        }
        StructureTerrainMask resolvedTerrainMask = new StructureTerrainMask(resolved, template);
        PlacementPlan terrainChanges = new PlacementPlan(requestedOrigin, maximumBlocks(),
                Math.min(betamoon.worldgen.WorldGenLimits.MAX_FEATURE_RADIUS,
                        Math.max(1, extraRadius() + maximumTemplateRadius())));
        StructureTerrainPlanner.Result result = StructureTerrainPlanner.prepare(context, requestedOrigin, transform,
                terrain, site, resolvedTerrainMask, bounds(new BlockPosition(0, 0, 0), transform), terrainChanges);
        if (result.failure != null) {
            return RegionalPlacement.rejected(result.failure);
        }
        List<ConformOffset> conformOffsets = result.conformOffsets();
        Bounds placedBounds = bounds(result.origin, transform, conformOffsets);
        BlockPosition minimum = placedBounds.min;
        BlockPosition maximum = placedBounds.max;
        for (PlacementPlan.PlannedBlock block : terrainChanges.plannedBlocks()) {
            minimum = new BlockPosition(Math.min(minimum.x, block.position.x), Math.min(minimum.y, block.position.y),
                    Math.min(minimum.z, block.position.z));
            maximum = new BlockPosition(Math.max(maximum.x, block.position.x), Math.max(maximum.y, block.position.y),
                    Math.max(maximum.z, block.position.z));
        }
        return RegionalPlacement.accepted(result.origin, new Bounds(minimum, maximum),
                terrainChanges.plannedBlocks(), conformOffsets);
    }

    private int maximumTemplateRadius() {
        int radius = 1;
        for (StructureTemplate.TemplateBlock block : template.blocks) {
            radius = Math.max(radius, Math.abs(block.position.x - template.origin.x));
            radius = Math.max(radius, Math.abs(block.position.y - template.origin.y));
            radius = Math.max(radius, Math.abs(block.position.z - template.origin.z));
        }
        return radius;
    }

    public int maximumBlocks() {
        long total = (long) template.blocks.size()
                + terrain.extraBlocks(terrainMask.supportColumns(), template.sizeX, template.sizeZ);
        if (total > betamoon.worldgen.WorldGenLimits.MAX_BLOCK_CHANGES_PER_FEATURE) {
            throw new IllegalArgumentException("structure and terrain policy may change more than "
                    + betamoon.worldgen.WorldGenLimits.MAX_BLOCK_CHANGES_PER_FEATURE + " blocks");
        }
        return (int) Math.max(1L, total);
    }

    public int extraRadius() {
        return terrain.extraRadius();
    }

    public int maximumReads() {
        long footprintColumns = (long) template.sizeX * template.sizeZ;
        long volume = footprintColumns * template.sizeY;
        long reads = volume + footprintColumns * (betamoon.worldgen.WorldGenLimits.MAX_HEIGHT + 1L) * 2L
                + (long) maximumBlocks() * 4L;
        return (int) Math.min(betamoon.worldgen.WorldGenLimits.MAX_TERRAIN_READS_PER_FEATURE,
                Math.max(256L, reads));
    }

    public static final class Description {
        public final String assetSource;
        public final int sizeX;
        public final int sizeY;
        public final int sizeZ;
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
        public final String terrainMode;
        public final String terrainSurface;
        public final int supportColumns;

        private Description(String assetSource, int sizeX, int sizeY, int sizeZ, int paletteEntries,
                int paletteVariants, int blocks, int markers, String rotation, String mirror, boolean includeAir,
                double decay, String tileCollision, String unknownMetadata, int customMetadataTransforms) {
            this(assetSource, sizeX, sizeY, sizeZ, paletteEntries, paletteVariants, blocks, markers, rotation,
                    mirror, includeAir, decay, tileCollision, unknownMetadata, customMetadataTransforms, "exact",
                    "exact", 0);
        }

        private Description(String assetSource, int sizeX, int sizeY, int sizeZ, int paletteEntries,
                int paletteVariants, int blocks, int markers, String rotation, String mirror, boolean includeAir,
                double decay, String tileCollision, String unknownMetadata, int customMetadataTransforms,
                String terrainMode, String terrainSurface, int supportColumns) {
            this.assetSource = assetSource;
            this.sizeX = sizeX;
            this.sizeY = sizeY;
            this.sizeZ = sizeZ;
            this.paletteEntries = paletteEntries;
            this.paletteVariants = paletteVariants;
            this.blocks = blocks;
            this.markers = markers;
            this.rotation = rotation;
            this.mirror = mirror;
            this.includeAir = includeAir;
            this.decay = decay;
            this.tileCollision = tileCollision;
            this.unknownMetadata = unknownMetadata;
            this.customMetadataTransforms = customMetadataTransforms;
            this.terrainMode = terrainMode;
            this.terrainSurface = terrainSurface;
            this.supportColumns = supportColumns;
        }
    }

    public static final class Bounds {
        public final BlockPosition min;
        public final BlockPosition max;

        public Bounds(BlockPosition min, BlockPosition max) {
            this.min = min;
            this.max = max;
        }

        public boolean intersects(Bounds other) {
            return min.x <= other.max.x && max.x >= other.min.x && min.y <= other.max.y && max.y >= other.min.y
                    && min.z <= other.max.z && max.z >= other.min.z;
        }

        public boolean intersectsChunk(int chunkX, int chunkZ) {
            int minChunkX = chunkX << 4;
            int minChunkZ = chunkZ << 4;
            return min.x <= minChunkX + 15 && max.x >= minChunkX && min.z <= minChunkZ + 15
                    && max.z >= minChunkZ;
        }
    }

    public static final class Connector {
        public final String pool;
        public final BlockPosition position;
        public final StructureTransform.Direction facing;

        public Connector(String pool, BlockPosition position, StructureTransform.Direction facing) {
            this.pool = pool;
            this.position = position;
            this.facing = facing;
        }
    }

    public static final class PositionedMarker {
        public final BlockPosition position;
        public final String name;
        public final Object value;

        public PositionedMarker(BlockPosition position, String name, Object value) {
            this.position = position;
            this.name = name;
            this.value = value;
        }
    }

    public static final class RegionalPlacement {
        public final boolean accepted;
        public final BlockPosition origin;
        public final Bounds bounds;
        public final List<PlacementPlan.PlannedBlock> terrainChanges;
        public final String failure;
        public final List<ConformOffset> conformOffsets;

        private RegionalPlacement(boolean accepted, BlockPosition origin, Bounds bounds,
                List<PlacementPlan.PlannedBlock> terrainChanges, String failure,
                List<ConformOffset> conformOffsets) {
            this.accepted = accepted;
            this.origin = origin;
            this.bounds = bounds;
            this.terrainChanges = terrainChanges;
            this.failure = failure;
            this.conformOffsets = conformOffsets;
        }

        private static RegionalPlacement accepted(BlockPosition origin, Bounds bounds,
                List<PlacementPlan.PlannedBlock> terrainChanges, List<ConformOffset> conformOffsets) {
            return new RegionalPlacement(true, origin, bounds, terrainChanges, null, conformOffsets);
        }

        private static RegionalPlacement rejected(String failure) {
            return new RegionalPlacement(false, null, null, Collections.<PlacementPlan.PlannedBlock>emptyList(),
                    failure, Collections.<ConformOffset>emptyList());
        }
    }

    public static final class ConformOffset {
        public final int x;
        public final int z;
        public final int y;

        public ConformOffset(int x, int z, int y) {
            this.x = x;
            this.z = z;
            this.y = y;
        }
    }
}
