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
    private final List<Connector> connectors;
    private final String generationSignature;

    public StructureFeature(StructureTemplate template, String assetSource, String defaultRotation,
            String defaultMirror, StructureProcessors processors) {
        this.template = template;
        this.assetSource = assetSource;
        this.defaultRotation = defaultRotation;
        this.defaultMirror = defaultMirror;
        this.processors = processors;
        connectors = Collections.unmodifiableList(parseConnectors(template));
        generationSignature = generationSignature(template, processors);
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
        return plan(context, origin, output, transform, context.random(), null, false);
    }

    /** Plans only the part of a deterministic regional piece contained by one chunk. */
    public FeatureResult planChunk(FeatureContext context, BlockPosition origin, PlacementPlan output,
            StructureTransform transform, long pieceSeed, int chunkX, int chunkZ, boolean recovery) {
        return plan(context, origin, output, transform, null, Long.valueOf(pieceSeed), recovery,
                chunkX << 4, (chunkX << 4) + 15, chunkZ << 4, (chunkZ << 4) + 15);
    }

    private FeatureResult plan(FeatureContext context, BlockPosition origin, PlacementPlan output,
            StructureTransform transform, Random sharedRandom, Long deterministicSeed, boolean recovery) {
        return plan(context, origin, output, transform, sharedRandom, deterministicSeed, recovery,
                Integer.MIN_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE);
    }

    private FeatureResult plan(FeatureContext context, BlockPosition origin, PlacementPlan output,
            StructureTransform transform, Random sharedRandom, Long deterministicSeed, boolean recovery,
            int minX, int maxX, int minZ, int maxZ) {
        Map<BlockPosition, Map<String, LuaValue>> markerData = markerData(transform);
        int before = output.size();
        for (int index = 0; index < template.blocks.size(); index++) {
            StructureTemplate.TemplateBlock block = template.blocks.get(index);
            Random random = deterministicSeed == null ? sharedRandom
                    : new Random(SeedMixer.derive(deterministicSeed.longValue(), index));
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
            int x = origin.x + relative.x;
            int y = origin.y + relative.y;
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
                metadata = BlockStateTransformRegistry.transform(blockId, selected.metadata, transform,
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
        int added = output.size() - before;
        return added == 0 ? FeatureResult.rejected(FeatureResult.NO_CHANGES)
                : FeatureResult.placed(added, null, null);
    }

    public Bounds bounds(BlockPosition origin, StructureTransform transform) {
        BlockPosition min = null;
        BlockPosition max = null;
        int[] xs = new int[]{-template.origin.x, template.sizeX - template.origin.x - 1};
        int[] ys = new int[]{-template.origin.y, template.sizeY - template.origin.y - 1};
        int[] zs = new int[]{-template.origin.z, template.sizeZ - template.origin.z - 1};
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

    public List<Connector> connectors(BlockPosition origin, StructureTransform transform) {
        List<Connector> result = new ArrayList<Connector>();
        for (Connector connector : connectors) {
            BlockPosition relative = transform.apply(connector.position.x, connector.position.y,
                    connector.position.z);
            result.add(new Connector(connector.pool, origin.offset(relative.x, relative.y, relative.z),
                    transform.apply(connector.facing)));
        }
        return Collections.unmodifiableList(result);
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
        return generationSignature;
    }

    public List<PositionedMarker> markers(BlockPosition origin, StructureTransform transform) {
        List<PositionedMarker> result = new ArrayList<PositionedMarker>();
        for (StructureTemplate.Marker marker : template.markers) {
            BlockPosition relative = transform.apply(marker.position.x - template.origin.x,
                    marker.position.y - template.origin.y, marker.position.z - template.origin.z);
            result.add(new PositionedMarker(origin.offset(relative.x, relative.y, relative.z), marker.name,
                    marker.value));
        }
        return Collections.unmodifiableList(result);
    }

    private static List<Connector> parseConnectors(StructureTemplate template) {
        List<Connector> result = new ArrayList<Connector>();
        for (StructureTemplate.Marker marker : template.markers) {
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
            BlockPosition relative = new BlockPosition(marker.position.x - template.origin.x,
                    marker.position.y - template.origin.y, marker.position.z - template.origin.z);
            result.add(new Connector((String) poolValue, relative,
                    StructureTransform.Direction.parse((String) facingValue)));
            if (result.size() > 64) {
                throw new IllegalArgumentException("structure contains more than 64 connector markers");
            }
        }
        return result;
    }

    private static String generationSignature(StructureTemplate template, StructureProcessors processors) {
        StringBuilder canonical = new StringBuilder(template.contentHash);
        canonical.append('|').append(processors.includeAir);
        canonical.append('|').append(Double.doubleToLongBits(processors.decay));
        canonical.append('|').append(processors.tileCollision).append('|').append(processors.unknownMetadata);
        canonical.append('|').append(new TreeMap<Integer, Integer>(processors.replacements));
        canonical.append('|').append(processors.allowedExisting == null ? "*"
                : java.util.Arrays.toString(processors.allowedExisting.values()));
        for (Map.Entry<Integer, CustomMetadataTransform> entry
                : new TreeMap<Integer, CustomMetadataTransform>(processors.metadataTransforms).entrySet()) {
            canonical.append('|').append(entry.getKey()).append('=').append(entry.getValue().generationSignature());
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

    private Map<BlockPosition, Map<String, LuaValue>> markerData(StructureTransform ignored) {
        Map<BlockPosition, Map<String, LuaValue>> result = new LinkedHashMap<BlockPosition, Map<String, LuaValue>>();
        for (StructureTemplate.Marker marker : template.markers) {
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
                processors.metadataTransforms.size());
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

        private Description(String assetSource, int sizeX, int sizeY, int sizeZ, int paletteEntries,
                int paletteVariants, int blocks, int markers, String rotation, String mirror, boolean includeAir,
                double decay, String tileCollision, String unknownMetadata, int customMetadataTransforms) {
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
}
