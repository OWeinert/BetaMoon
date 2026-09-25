package betamoon.worldgen.structure;

import betamoon.worldgen.BlockPosition;
import betamoon.worldgen.FeatureContext;
import betamoon.worldgen.FeatureResult;
import betamoon.worldgen.PlacementPlan;
import betamoon.worldgen.WorldFeature;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

/** Atomic local placement of one validated structure template. */
public final class StructureFeature implements WorldFeature {
    private final StructureTemplate template;
    private final String assetSource;
    private final String defaultRotation;
    private final String defaultMirror;
    private final StructureProcessors processors;

    public StructureFeature(StructureTemplate template, String assetSource, String defaultRotation,
            String defaultMirror, StructureProcessors processors) {
        this.template = template;
        this.assetSource = assetSource;
        this.defaultRotation = defaultRotation;
        this.defaultMirror = defaultMirror;
        this.processors = processors;
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
        Map<BlockPosition, Map<String, LuaValue>> markerData = markerData(transform);
        for (StructureTemplate.TemplateBlock block : template.blocks) {
            StructureTemplate.State selected = template.palette.get(block.state).select(context.random());
            if (selected.blockId == 0 && !processors.includeAir) {
                continue;
            }
            if (processors.decay > 0.0D && context.random().nextDouble() < processors.decay) {
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
            int existing = context.blockId(x, y, z);
            if (existing < 0) {
                return FeatureResult.rejected(context.failure());
            }
            if (processors.allowedExisting != null && !processors.allowedExisting.contains(existing)) {
                return FeatureResult.rejected(FeatureResult.PROTECTED);
            }
            if (context.hasTileEntity(x, y, z)) {
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
        return output.size() == 0 ? FeatureResult.rejected(FeatureResult.NO_CHANGES)
                : FeatureResult.placed(output.size(), null, null);
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
}
