package betamoon.luaapi.world;

import betamoon.luamodloader.LuaScriptRegistry;
import betamoon.worldgen.BlockSet;
import betamoon.worldgen.RegionalStructureDefinition;
import betamoon.worldgen.TerrainSurface;
import betamoon.worldgen.WorldGenKey;
import betamoon.worldgen.WorldGenKind;
import betamoon.worldgen.WorldGenLimits;
import betamoon.worldgen.WorldGenRegistry;
import betamoon.worldgen.structure.CustomMetadataTransform;
import betamoon.worldgen.structure.SitePolicy;
import betamoon.worldgen.structure.StructureExporter;
import betamoon.worldgen.structure.StructureFeature;
import betamoon.worldgen.structure.StructureProcessors;
import betamoon.worldgen.structure.StructureTemplate;
import betamoon.worldgen.structure.StructureTransform;
import betamoon.worldgen.structure.TerrainPolicy;
import betamoon.worldgen.structure.WorldGenDataResolver;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import net.minecraft.src.World;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;
import static betamoon.luaapi.utils.LuaDeclarationValues.required;

/** Installs local JSON structure declarations and capture tooling. */
public final class StructureGenApi {
    private StructureGenApi() {
    }

    public static void attach(LuaTable worldgen) {
        final LuaTable structures = new LuaTable();
        structures.set("add", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs arguments) {
                LuaValue definition = argument(arguments, structures, 1);
                FeaturePlacementApi.table(definition, "worldgen.structures:add");
                String declaredKey = required(definition, "key").checkjstring();
                WorldGenKey key;
                try {
                    key = WorldGenKey.parse(declaredKey, WorldGenKind.STRUCTURE);
                } catch (IllegalArgumentException error) {
                    throw new LuaError("Structure.key: " + error.getMessage());
                }
                String owner = LuaScriptRegistry.getCurrentScriptFile();
                String path = definition.get("path").isnil() ? null : definition.get("path").checkjstring();
                String rotation = definition.get("rotation").optjstring("none");
                String mirror = definition.get("mirror").optjstring("none");
                validateTransform(rotation, mirror);
                try {
                    WorldGenDataResolver.ResolvedData data = WorldGenDataResolver.structure(owner, key, path);
                    StructureTemplate template = StructureTemplate.read(data.bytes);
                    StructureProcessors processors = processors(definition.get("processors"));
                    TerrainPolicy terrain = terrain(definition.get("terrain"));
                    StructureFeature feature = new StructureFeature(template, data.displayPath, rotation, mirror,
                            processors, terrain);
                    int radius = radius(template) + feature.extraRadius();
                    if (radius > WorldGenLimits.MAX_FEATURE_RADIUS) {
                        throw new LuaError("Structure terrain policy exceeds maximum placement radius "
                                + WorldGenLimits.MAX_FEATURE_RADIUS);
                    }
                    WorldGenRegistry.addFeature(declaredKey, WorldGenKind.STRUCTURE, "local_structure", feature,
                            Collections.<WorldGenKey>emptyList(), feature.maximumBlocks(), radius);
                    return new FeatureReference(key);
                } catch (IOException | IllegalArgumentException error) {
                    throw new LuaError("Structure " + key + ": " + error.getMessage());
                }
            }
        });
        structures.set("get", lookup(structures, false));
        structures.set("getRequired", lookup(structures, true));
        structures.set("addRegional", addRegional(structures));
        structures.set("getRegional", lookupRegional(structures, false));
        structures.set("getRegionalRequired", lookupRegional(structures, true));
        structures.set("export", exporter(structures));
        worldgen.set("structures", structures);
    }

    private static VarArgFunction addRegional(final LuaTable structures) {
        return new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs arguments) {
                LuaValue definition = argument(arguments, structures, 1);
                FeaturePlacementApi.table(definition, "worldgen.structures:addRegional");
                String declaredKey = required(definition, "key").checkjstring();
                WorldGenKey start = structureKey(required(definition, "start"), "RegionalStructure.start");
                int spacing = integer(definition.get("spacing"), "RegionalStructure.spacing", 2,
                        WorldGenLimits.MAX_REGIONAL_SPACING, 32);
                int separation = integer(definition.get("separation"), "RegionalStructure.separation", 0,
                        spacing - 1, Math.min(8, spacing - 1));
                long salt = definition.get("salt").isnil() ? 0L : definition.get("salt").checklong();
                Height height = height(definition.get("height"));
                SitePolicy site = FeaturePlacementApi.site(definition.get("site"), "RegionalStructure.site");
                int searchAttempts = 1;
                int searchRadius = 0;
                LuaValue siteSearch = definition.get("siteSearch");
                if (!siteSearch.isnil()) {
                    FeaturePlacementApi.table(siteSearch, "RegionalStructure.siteSearch");
                    searchAttempts = integer(siteSearch.get("attempts"), "RegionalStructure.siteSearch.attempts",
                            1, 32, 8);
                    searchRadius = integer(siteSearch.get("radius"), "RegionalStructure.siteSearch.radius",
                            0, 7, 7);
                }
                int connectorTolerance = integer(definition.get("connectorVerticalTolerance"),
                        "RegionalStructure.connectorVerticalTolerance", 0, 16, 0);
                int maxDepth = integer(definition.get("maxDepth"), "RegionalStructure.maxDepth", 0,
                        WorldGenLimits.MAX_REGIONAL_DEPTH, 4);
                int maxPieces = integer(definition.get("maxPieces"), "RegionalStructure.maxPieces", 1,
                        WorldGenLimits.MAX_REGIONAL_PIECES, 32);
                int maxDistance = integer(definition.get("maxDistance"), "RegionalStructure.maxDistance", 16,
                        WorldGenLimits.MAX_REGIONAL_DISTANCE, 128);
                double termination = FeaturePlacementApi.optionalNumber(definition.get("terminationChance"), 0.2D,
                        "RegionalStructure.terminationChance", 0.0D, 1.0D);
                LuaValue markers = definition.get("markers");
                boolean entities = false;
                boolean loot = false;
                if (!markers.isnil()) {
                    FeaturePlacementApi.table(markers, "RegionalStructure.markers");
                    entities = markers.get("entities").optboolean(false);
                    loot = markers.get("loot").optboolean(false);
                }
                WorldGenKey key = WorldGenRegistry.addRegionalStructure(declaredKey, start,
                        dimensions(definition.get("dimensions")), spacing, separation, salt, height.type,
                        height.value, maxDepth, maxPieces, maxDistance, termination, entities, loot,
                        pieces(definition.get("pieces")), site, searchAttempts, searchRadius, connectorTolerance);
                return new RegionalStructureReference(key);
            }
        };
    }

    private static VarArgFunction lookupRegional(final LuaTable structures, final boolean required) {
        return new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs arguments) {
                WorldGenKey key;
                try {
                    key = WorldGenKey.parse(argument(arguments, structures, 1).checkjstring(),
                            WorldGenKind.STRUCTURE);
                } catch (IllegalArgumentException error) {
                    throw new LuaError("Structure.getRegional: " + error.getMessage());
                }
                if (!WorldGenRegistry.hasRegionalStructure(key)) {
                    if (required) {
                        throw new LuaError("Regional structure is not registered: " + key);
                    }
                    return NIL;
                }
                return new RegionalStructureReference(key);
            }
        };
    }

    private static VarArgFunction lookup(final LuaTable structures, final boolean required) {
        return new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs arguments) {
                WorldGenKey key;
                try {
                    key = WorldGenKey.parse(argument(arguments, structures, 1).checkjstring(),
                            WorldGenKind.STRUCTURE);
                } catch (IllegalArgumentException error) {
                    throw new LuaError("Structure.get: " + error.getMessage());
                }
                if (!WorldGenRegistry.hasFeature(key)) {
                    if (required) {
                        throw new LuaError("Structure is not registered: " + key);
                    }
                    return NIL;
                }
                return new FeatureReference(key);
            }
        };
    }

    private static VarArgFunction exporter(final LuaTable structures) {
        return new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs arguments) {
                int offset = arguments.arg1() == structures ? 1 : 0;
                World world = LuaWorldActionAccess.requireMutableWorld(arguments.arg(1 + offset));
                int minX = coordinate(arguments.arg(2 + offset), "structures.export.minX");
                int minY = integer(arguments.arg(3 + offset), "structures.export.minY", 0, 127);
                int minZ = coordinate(arguments.arg(4 + offset), "structures.export.minZ");
                int maxX = coordinate(arguments.arg(5 + offset), "structures.export.maxX");
                int maxY = integer(arguments.arg(6 + offset), "structures.export.maxY", 0, 127);
                int maxZ = coordinate(arguments.arg(7 + offset), "structures.export.maxZ");
                LuaValue options = arguments.arg(8 + offset);
                int originX = minX;
                int originY = minY;
                int originZ = minZ;
                boolean includeAir = false;
                if (!options.isnil()) {
                    FeaturePlacementApi.table(options, "structures.export.options");
                    includeAir = options.get("includeAir").optboolean(false);
                    LuaValue origin = options.get("origin");
                    if (!origin.isnil()) {
                        FeaturePlacementApi.table(origin, "structures.export.options.origin");
                        originX = coordinate(required(origin, "x"), "structures.export.options.origin.x");
                        originY = integer(required(origin, "y"), "structures.export.options.origin.y", 0, 127);
                        originZ = coordinate(required(origin, "z"), "structures.export.options.origin.z");
                    }
                }
                try {
                    return valueOf(StructureExporter.capture(world, minX, minY, minZ, maxX, maxY, maxZ, originX,
                            originY, originZ, includeAir));
                } catch (IllegalArgumentException error) {
                    throw new LuaError("structures.export: " + error.getMessage());
                }
            }
        };
    }

    private static StructureProcessors processors(LuaValue value) {
        if (value.isnil()) {
            return new StructureProcessors(false, Collections.<Integer, Integer>emptyMap(), 0.0D, null, "reject",
                    "reject");
        }
        FeaturePlacementApi.table(value, "Structure.processors");
        boolean includeAir = value.get("includeAir").optboolean(false);
        double decay = FeaturePlacementApi.optionalNumber(value.get("decay"), 0.0D,
                "Structure.processors.decay", 0.0D, 1.0D);
        BlockSet allowed = value.get("replaceOnly").isnil() ? null
                : FeaturePlacementApi.blocks(value.get("replaceOnly"), "Structure.processors.replaceOnly");
        String tileCollision = value.get("tileCollision").optjstring("reject");
        if (!tileCollision.equals("reject") && !tileCollision.equals("preserve")) {
            throw new LuaError("Structure.processors.tileCollision: expected 'reject' or 'preserve'");
        }
        String unknownMetadata = value.get("unknownMetadata").optjstring("reject");
        if (!unknownMetadata.equals("reject") && !unknownMetadata.equals("preserve")) {
            throw new LuaError("Structure.processors.unknownMetadata: expected 'reject' or 'preserve'");
        }
        Map<Integer, Integer> replacements = new LinkedHashMap<Integer, Integer>();
        LuaValue replace = value.get("replace");
        if (!replace.isnil()) {
            FeaturePlacementApi.table(replace, "Structure.processors.replace");
            for (int index = 1; index <= replace.length(); index++) {
                LuaValue entry = replace.get(index);
                FeaturePlacementApi.table(entry, "Structure.processors.replace[" + index + "]");
                int from = FeaturePlacementApi.blockId(required(entry, "from"),
                        "Structure.processors.replace[" + index + "].from");
                int to = FeaturePlacementApi.blockId(required(entry, "to"),
                        "Structure.processors.replace[" + index + "].to");
                replacements.put(Integer.valueOf(from), Integer.valueOf(to));
            }
        }
        Map<Integer, CustomMetadataTransform> metadataTransforms = new LinkedHashMap<Integer, CustomMetadataTransform>();
        LuaValue transforms = value.get("metadataTransforms");
        if (!transforms.isnil()) {
            FeaturePlacementApi.table(transforms, "Structure.processors.metadataTransforms");
            for (int index = 1; index <= transforms.length(); index++) {
                LuaValue entry = transforms.get(index);
                String path = "Structure.processors.metadataTransforms[" + index + "]";
                FeaturePlacementApi.table(entry, path);
                int block = FeaturePlacementApi.blockId(required(entry, "block"), path + ".block");
                metadataTransforms.put(Integer.valueOf(block), new CustomMetadataTransform(
                        metadataMap(entry.get("clockwise"), path + ".clockwise"),
                        metadataMap(entry.get("leftRight"), path + ".leftRight"),
                        metadataMap(entry.get("frontBack"), path + ".frontBack")));
            }
        }
        return new StructureProcessors(includeAir, replacements, decay, allowed, tileCollision, unknownMetadata,
                metadataTransforms);
    }

    private static TerrainPolicy terrain(LuaValue value) {
        if (value.isnil()) {
            return TerrainPolicy.EXACT;
        }
        FeaturePlacementApi.table(value, "Structure.terrain");
        try {
            TerrainPolicy.Mode mode = TerrainPolicy.Mode.parse(value.get("mode").optjstring("exact"));
            if (mode == TerrainPolicy.Mode.EXACT) {
                return TerrainPolicy.EXACT;
            }
            TerrainSurface surface = TerrainSurface.parse(value.get("surface").optjstring("solid_surface"));
            if (surface == TerrainSurface.EXACT) {
                throw new LuaError("Structure.terrain.surface: exact is only valid with mode='exact'");
            }
            TerrainPolicy.Anchor anchor = TerrainPolicy.Anchor.parse(value.get("anchor").optjstring("median"));
            double percentile = FeaturePlacementApi.optionalNumber(value.get("percentile"), 0.5D,
                    "Structure.terrain.percentile", 0.0D, 1.0D);
            int maxSlope = integer(value.get("maxSlope"), "Structure.terrain.maxSlope", 0, 32, 2);
            int maxStep = integer(value.get("maxStep"), "Structure.terrain.maxStep", 0, 32, maxSlope);
            double defaultSupport = mode == TerrainPolicy.Mode.FIT ? 0.5D : 0.0D;
            double support = FeaturePlacementApi.optionalNumber(value.get("requireSupportRatio"), defaultSupport,
                    "Structure.terrain.requireSupportRatio", 0.0D, 1.0D);
            int offset = integer(value.get("verticalOffset"), "Structure.terrain.verticalOffset", -32, 32, 0);
            int foundationBlock = 0;
            int foundationMetadata = integer(value.get("foundationMeta"), "Structure.terrain.foundationMeta",
                    0, 15, 0);
            if (!value.get("foundationBlock").isnil()) {
                foundationBlock = FeaturePlacementApi.blockId(value.get("foundationBlock"),
                        "Structure.terrain.foundationBlock");
                if (foundationBlock == 0) {
                    throw new LuaError("Structure.terrain.foundationBlock: expected a non-air solid block");
                }
            } else if (mode == TerrainPolicy.Mode.FOUNDATION) {
                foundationBlock = net.minecraft.src.Block.cobblestone.blockID;
            }
            if (foundationBlock != 0 && (net.minecraft.src.Block.blocksList[foundationBlock] == null
                    || !net.minecraft.src.Block.blocksList[foundationBlock].blockMaterial.getIsSolid())) {
                throw new LuaError("Structure.terrain.foundationBlock: expected a structurally solid block");
            }
            if (mode == TerrainPolicy.Mode.FOUNDATION && foundationBlock == 0) {
                throw new LuaError("Structure.terrain.foundationBlock: foundation mode requires a solid block");
            }
            int foundationDepth = integer(value.get("maxFoundationDepth"),
                    "Structure.terrain.maxFoundationDepth", 1, 32, 6);
            int cutDepth = integer(value.get("maxCutDepth"), "Structure.terrain.maxCutDepth", 0, 16, 3);
            int fillDepth = integer(value.get("maxFillDepth"), "Structure.terrain.maxFillDepth", 0, 16, 3);
            int padding = integer(value.get("padding"), "Structure.terrain.padding", 0, 8, 0);
            int blendRadius = integer(value.get("blendRadius"), "Structure.terrain.blendRadius", 0, 8, 0);
            int blendStep = integer(value.get("maxBlendStep"), "Structure.terrain.maxBlendStep", 1, 8, 1);
            int conform = integer(value.get("maxConformDisplacement"),
                    "Structure.terrain.maxConformDisplacement", 1, 16, 4);
            return new TerrainPolicy(mode, surface, anchor, percentile, maxSlope, maxStep, support, offset,
                    foundationBlock, foundationMetadata, foundationDepth, cutDepth, fillDepth, padding,
                    blendRadius, blendStep, conform);
        } catch (IllegalArgumentException error) {
            throw new LuaError("Structure.terrain: " + error.getMessage());
        }
    }

    private static int[] metadataMap(LuaValue value, String path) {
        int[] result = new int[16];
        if (value.isnil()) {
            for (int metadata = 0; metadata < result.length; metadata++) {
                result[metadata] = metadata;
            }
            return result;
        }
        FeaturePlacementApi.table(value, path);
        if (value.length() != 16) {
            throw new LuaError(path + ": expected 16 metadata values for inputs 0..15");
        }
        for (int index = 0; index < result.length; index++) {
            result[index] = integer(value.get(index + 1), path + "[" + (index + 1) + "]", 0, 15);
        }
        return result;
    }

    private static int radius(StructureTemplate template) {
        int radius = 1;
        for (StructureTemplate.TemplateBlock block : template.blocks) {
            radius = Math.max(radius, Math.abs(block.position.x - template.origin.x));
            radius = Math.max(radius, Math.abs(block.position.y - template.origin.y));
            radius = Math.max(radius, Math.abs(block.position.z - template.origin.z));
        }
        if (radius > WorldGenLimits.MAX_FEATURE_RADIUS) {
            throw new LuaError("Structure dimensions exceed maximum placement radius "
                    + WorldGenLimits.MAX_FEATURE_RADIUS);
        }
        return radius;
    }

    private static void validateTransform(String rotation, String mirror) {
        try {
            StructureTransform.rotation(rotation, new Random(0L));
            StructureTransform.mirror(mirror, new Random(0L));
        } catch (IllegalArgumentException error) {
            throw new LuaError("Structure transform: " + error.getMessage());
        }
    }

    private static WorldGenKey structureKey(LuaValue value, String path) {
        if (value instanceof FeatureReference) {
            WorldGenKey key = ((FeatureReference) value).key();
            if (key.getKind() != WorldGenKind.STRUCTURE) {
                throw new LuaError(path + ": expected a local structure reference");
            }
            return key;
        }
        try {
            return WorldGenKey.parse(value.checkjstring(), WorldGenKind.STRUCTURE);
        } catch (IllegalArgumentException error) {
            throw new LuaError(path + ": " + error.getMessage());
        }
    }

    private static List<RegionalStructureDefinition.PieceChoice> pieces(LuaValue value) {
        if (value.isnil()) {
            return Collections.emptyList();
        }
        FeaturePlacementApi.table(value, "RegionalStructure.pieces");
        if (value.length() > 64) {
            throw new LuaError("RegionalStructure.pieces: at most 64 entries are allowed");
        }
        List<RegionalStructureDefinition.PieceChoice> result =
                new ArrayList<RegionalStructureDefinition.PieceChoice>();
        for (int index = 1; index <= value.length(); index++) {
            LuaValue entry = value.get(index);
            String path = "RegionalStructure.pieces[" + index + "]";
            FeaturePlacementApi.table(entry, path);
            String pool = required(entry, "pool").checkjstring();
            if (!pool.matches("[a-z][a-z0-9_.-]{0,63}")) {
                throw new LuaError(path + ".pool: expected a lowercase identifier");
            }
            WorldGenKey structure = structureKey(required(entry, "structure"), path + ".structure");
            int weight = integer(entry.get("weight"), path + ".weight", 1, 1000000, 1);
            result.add(new RegionalStructureDefinition.PieceChoice(pool, structure, weight));
        }
        return result;
    }

    private static Set<String> dimensions(LuaValue value) {
        Set<String> result = new LinkedHashSet<String>();
        if (value.isnil()) {
            result.add("minecraft:overworld");
            return result;
        }
        if (value.isstring()) {
            result.add(dimension(value.checkjstring()));
            return result;
        }
        FeaturePlacementApi.table(value, "RegionalStructure.dimensions");
        for (int index = 1; index <= value.length(); index++) {
            result.add(dimension(value.get(index).checkjstring()));
        }
        if (result.isEmpty()) {
            throw new LuaError("RegionalStructure.dimensions: expected at least one dimension");
        }
        return result;
    }

    private static String dimension(String value) {
        String normalized = value.trim().toLowerCase(java.util.Locale.ROOT);
        if (normalized.equals("overworld")) {
            return "minecraft:overworld";
        }
        if (normalized.equals("nether")) {
            return "minecraft:nether";
        }
        if (normalized.equals("minecraft:overworld") || normalized.equals("minecraft:nether")) {
            return normalized;
        }
        throw new LuaError("RegionalStructure.dimensions: unsupported dimension " + value);
    }

    private static Height height(LuaValue value) {
        if (value.isnil()) {
            return new Height("surface", 0);
        }
        if (value.isnumber()) {
            return new Height("fixed", integer(value, "RegionalStructure.height", 0, 127));
        }
        FeaturePlacementApi.table(value, "RegionalStructure.height");
        String type = value.get("type").optjstring("surface");
        if (type.equals("surface") || type.equals("world_surface") || type.equals("solid_surface")
                || type.equals("ocean_floor") || type.equals("fluid_surface")) {
            try {
                TerrainSurface.parse(type);
            } catch (IllegalArgumentException error) {
                throw new LuaError("RegionalStructure.height.type: " + error.getMessage());
            }
            return new Height(type, integer(value.get("offset"), "RegionalStructure.height.offset", -127, 127,
                    0));
        }
        if (type.equals("fixed")) {
            return new Height(type, integer(required(value, "value"), "RegionalStructure.height.value", 0, 127));
        }
        throw new LuaError("RegionalStructure.height.type: expected a surface sampler or 'fixed'");
    }

    private static LuaValue argument(Varargs arguments, LuaValue receiver, int index) {
        return arguments.arg(index + (arguments.arg1() == receiver ? 1 : 0));
    }

    private static int coordinate(LuaValue value, String path) {
        return integer(value, path, -30000000, 30000000);
    }

    private static int integer(LuaValue value, String path, int min, int max) {
        if (!value.isint()) {
            throw new LuaError(path + ": expected an integer");
        }
        int result = value.toint();
        if (result < min || result > max) {
            throw new LuaError(path + ": expected " + min + ".." + max);
        }
        return result;
    }

    private static int integer(LuaValue value, String path, int min, int max, int fallback) {
        return value.isnil() ? fallback : integer(value, path, min, max);
    }

    private static final class Height {
        private final String type;
        private final int value;

        private Height(String type, int value) {
            this.type = type;
            this.value = value;
        }
    }
}
