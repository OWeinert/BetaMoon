package betamoon.luaapi.world;

import betamoon.luamodloader.LuaScriptRegistry;
import betamoon.worldgen.BlockPosition;
import betamoon.worldgen.BlockSet;
import betamoon.worldgen.RegionalStructureDefinition;
import betamoon.worldgen.TerrainSurface;
import betamoon.worldgen.WorldGenKey;
import betamoon.worldgen.WorldGenKind;
import betamoon.worldgen.WorldGenLimits;
import betamoon.worldgen.WorldGenRegistry;
import betamoon.worldgen.structure.CustomMetadataTransform;
import betamoon.worldgen.structure.ConformPolicy;
import betamoon.worldgen.structure.ExcavationPolicy;
import betamoon.worldgen.structure.FoundationPolicy;
import betamoon.worldgen.structure.SitePolicy;
import betamoon.worldgen.structure.StructureExporter;
import betamoon.worldgen.structure.StructureFeature;
import betamoon.worldgen.structure.StructureProcessors;
import betamoon.worldgen.structure.StructureTemplate;
import betamoon.worldgen.structure.StructureTransform;
import betamoon.worldgen.structure.TerrainPolicy;
import betamoon.worldgen.structure.TerrainFootprintPolicy;
import betamoon.worldgen.structure.TerrainMaterialPolicy;
import betamoon.worldgen.structure.TerrainReplacementPolicy;
import betamoon.worldgen.structure.TerracePolicy;
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
import net.minecraft.src.Block;
import net.minecraft.src.BlockContainer;
import net.minecraft.src.World;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;
import static betamoon.luaapi.utils.LuaDeclarationValues.required;
import static betamoon.luaapi.utils.LuaDeclarationValues.fields;

/**
 * Installs declarative structure authoring, registration, lookup, and capture
 * tooling.
 */
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
                String rotation = definition.get("rotation").optjstring("none");
                String mirror = definition.get("mirror").optjstring("none");
                validateTransform(rotation, mirror);
                try {
                    TemplateSource source = templateSource(definition, owner, key);
                    StructureProcessors processors = processors(definition.get("processors"));
                    TerrainPolicy terrain = terrain(definition.get("terrain"));
                    StructureFeature feature = new StructureFeature(source.template, source.displayPath, rotation,
                            mirror, processors, terrain);
                    int radius = radius(source.template) + feature.extraRadius();
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
        structures.set("builder", builder(structures));
        structures.set("export", exporter(structures));
        worldgen.set("structures", structures);
    }

    private static VarArgFunction builder(final LuaTable structures) {
        return new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs arguments) {
                return new StructureBuilder(argument(arguments, structures, 1));
            }
        };
    }

    private static TemplateSource templateSource(LuaValue definition, String owner, WorldGenKey key)
            throws IOException {
        LuaValue inline = definition.get("template");
        LuaValue pathValue = definition.get("path");
        if (!inline.isnil() && !pathValue.isnil()) {
            throw new LuaError("Structure: path and template are mutually exclusive");
        }
        if (inline instanceof StructureBuilder.CompiledStructureValue) {
            return new TemplateSource(((StructureBuilder.CompiledStructureValue) inline).template(),
                    owner + ":inline structure " + key);
        }
        if (!inline.isnil()) {
            if (!inline.istable()) {
                throw new LuaError("Structure.template: expected a structure table or compiled structure");
            }
            StructureTemplate template = StructureTemplate
                    .read(LuaStructureDocument.snapshot(inline, "Structure.template"));
            return new TemplateSource(template, owner + ":inline structure " + key);
        }
        String path = pathValue.isnil() ? null : pathValue.checkjstring();
        WorldGenDataResolver.ResolvedData data = WorldGenDataResolver.structure(owner, key, path);
        return new TemplateSource(StructureTemplate.read(data.bytes), data.displayPath);
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
                int separation = integer(definition.get("separation"), "RegionalStructure.separation", 0, spacing - 1,
                        Math.min(8, spacing - 1));
                long salt = definition.get("salt").isnil() ? 0L : definition.get("salt").checklong();
                Height height = height(definition.get("height"));
                SitePolicy site = FeaturePlacementApi.site(definition.get("site"), "RegionalStructure.site");
                int searchAttempts = 1;
                int searchRadius = 0;
                LuaValue siteSearch = definition.get("siteSearch");
                if (!siteSearch.isnil()) {
                    FeaturePlacementApi.table(siteSearch, "RegionalStructure.siteSearch");
                    searchAttempts = integer(siteSearch.get("attempts"), "RegionalStructure.siteSearch.attempts", 1, 32,
                            8);
                    searchRadius = integer(siteSearch.get("radius"), "RegionalStructure.siteSearch.radius", 0, 7, 7);
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
                if (!markers.isnil()) {
                    FeaturePlacementApi.table(markers, "RegionalStructure.markers");
                    fields(markers, "RegionalStructure.markers", "entities");
                    entities = markers.get("entities").optboolean(false);
                }
                WorldGenKey key = WorldGenRegistry.addRegionalStructure(declaredKey, start,
                        dimensions(definition.get("dimensions")), spacing, separation, salt, height.type, height.value,
                        maxDepth, maxPieces, maxDistance, termination, entities, pieces(definition.get("pieces")), site,
                        searchAttempts, searchRadius, connectorTolerance);
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
                    key = WorldGenKey.parse(argument(arguments, structures, 1).checkjstring(), WorldGenKind.STRUCTURE);
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
                    key = WorldGenKey.parse(argument(arguments, structures, 1).checkjstring(), WorldGenKind.STRUCTURE);
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
        double decay = FeaturePlacementApi.optionalNumber(value.get("decay"), 0.0D, "Structure.processors.decay", 0.0D,
                1.0D);
        BlockSet allowed = value.get("replaceOnly").isnil()
                ? null
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
                metadataTransforms.put(Integer.valueOf(block),
                        new CustomMetadataTransform(metadataMap(entry.get("clockwise"), path + ".clockwise"),
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
            TerrainSurface surface = TerrainSurface.parse(
                    value.get("surface").optjstring(mode == TerrainPolicy.Mode.EXACT ? "exact" : "solid_surface"));
            if (surface == TerrainSurface.EXACT && mode != TerrainPolicy.Mode.EXACT) {
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
            int foundationMetadata = integer(value.get("foundationMeta"), "Structure.terrain.foundationMeta", 0, 15, 0);
            if (!value.get("foundationBlock").isnil()) {
                foundationBlock = FeaturePlacementApi.blockId(value.get("foundationBlock"),
                        "Structure.terrain.foundationBlock");
                if (foundationBlock == 0) {
                    throw new LuaError("Structure.terrain.foundationBlock: expected a non-air solid block");
                }
            } else if (mode == TerrainPolicy.Mode.FOUNDATION) {
                foundationBlock = Block.cobblestone.blockID;
            }
            if (foundationBlock != 0 && (Block.blocksList[foundationBlock] == null
                    || !Block.blocksList[foundationBlock].blockMaterial.getIsSolid()
                    || Block.blocksList[foundationBlock] instanceof BlockContainer)) {
                throw new LuaError("Structure.terrain.foundationBlock: expected a structurally solid block");
            }
            if (mode == TerrainPolicy.Mode.FOUNDATION && foundationBlock == 0) {
                throw new LuaError("Structure.terrain.foundationBlock: foundation mode requires a solid block");
            }
            int foundationDepth = integer(value.get("maxFoundationDepth"), "Structure.terrain.maxFoundationDepth", 1,
                    32, 6);
            int cutDepth = integer(value.get("maxCutDepth"), "Structure.terrain.maxCutDepth", 0, 16, 3);
            int fillDepth = integer(value.get("maxFillDepth"), "Structure.terrain.maxFillDepth", 0, 16, 3);
            int padding = integer(value.get("padding"), "Structure.terrain.padding", 0, 8, 0);
            int blendRadius = integer(value.get("blendRadius"), "Structure.terrain.blendRadius", 0, 8, 0);
            int blendStep = integer(value.get("maxBlendStep"), "Structure.terrain.maxBlendStep", 1, 8, 1);
            int conform = integer(value.get("maxConformDisplacement"), "Structure.terrain.maxConformDisplacement", 1,
                    16, 4);
            Map<String, TerrainFootprintPolicy> masks = terrainMasks(value.get("masks"));
            FoundationPolicy foundation = foundation(value, mode, foundationBlock, foundationMetadata, foundationDepth);
            TerracePolicy terrace = terrace(value, mode, foundationBlock, foundationMetadata, cutDepth, fillDepth,
                    padding, blendRadius, blendStep);
            ConformPolicy conformPolicy = conform(value, mode, conform, maxStep);
            ExcavationPolicy excavation = excavation(value.get("excavation"));
            validateTerrainReferences(masks, foundation, terrace, conformPolicy, excavation);
            return new TerrainPolicy(mode, surface, anchor, percentile, maxSlope, maxStep, support, offset,
                    foundationBlock, foundationMetadata, foundationDepth, cutDepth, fillDepth, padding, blendRadius,
                    blendStep, conform, foundation, terrace, conformPolicy, excavation, masks);
        } catch (IllegalArgumentException error) {
            throw new LuaError("Structure.terrain: " + error.getMessage());
        }
    }

    private static Map<String, TerrainFootprintPolicy> terrainMasks(LuaValue value) {
        if (value.isnil()) {
            return Collections.emptyMap();
        }
        FeaturePlacementApi.table(value, "Structure.terrain.masks");
        Map<String, TerrainFootprintPolicy> result = new LinkedHashMap<String, TerrainFootprintPolicy>();
        for (LuaValue key : value.checktable().keys()) {
            String name = key.checkjstring();
            if (!name.matches("[a-z][a-z0-9_.-]{0,63}")) {
                throw new LuaError("Structure.terrain.masks: invalid mask name " + name);
            }
            result.put(name, footprint(value.get(key), "Structure.terrain.masks." + name,
                    TerrainFootprintPolicy.Source.SUPPORT, TerrainFootprintPolicy.Shape.MASK));
        }
        return result;
    }

    private static void validateTerrainReferences(Map<String, TerrainFootprintPolicy> masks,
            FoundationPolicy foundation, TerracePolicy terrace, ConformPolicy conform, ExcavationPolicy excavation) {
        Set<String> complete = new LinkedHashSet<String>();
        for (String name : masks.keySet()) {
            validateTerrainMask(name, masks, new LinkedHashSet<String>(), complete);
        }
        if (foundation != null) {
            validateTerrainMaskReference(foundation.footprint, masks, "Structure.terrain.foundation.footprint");
            if (foundation.edgeType == FoundationPolicy.EdgeType.AUTHORED
                    && (foundation.edgeMask == null || !masks.containsKey(foundation.edgeMask))) {
                throw new LuaError(
                        "Structure.terrain.foundation.edge.mask: unknown terrain mask " + foundation.edgeMask);
            }
        }
        if (terrace != null) {
            validateTerrainMaskReference(terrace.footprint, masks, "Structure.terrain.terrace.footprint");
            if (terrace.transitionType == TerracePolicy.TransitionType.AUTHORED
                    && (terrace.transitionMask == null || !masks.containsKey(terrace.transitionMask))) {
                throw new LuaError(
                        "Structure.terrain.terrace.transition.mask: unknown terrain mask " + terrace.transitionMask);
            }
        }
        if (conform != null) {
            validateTerrainMaskReference(conform.columns, masks, "Structure.terrain.conform.columns");
        }
        if (excavation != null) {
            for (ExcavationPolicy.Volume volume : excavation.volumes) {
                if (volume.footprint != null) {
                    validateTerrainMaskReference(volume.footprint, masks, "Structure.terrain.excavation footprint");
                }
                if (volume.shape == ExcavationPolicy.Shape.AUTHORED && volume.name != null
                        && !excavation.masks.containsKey(volume.name)) {
                    throw new LuaError("Structure.terrain.excavation: unknown authored voxel mask " + volume.name);
                }
            }
        }
    }

    private static void validateTerrainMask(String name, Map<String, TerrainFootprintPolicy> masks, Set<String> active,
            Set<String> complete) {
        if (complete.contains(name)) {
            return;
        }
        if (!active.add(name)) {
            throw new LuaError("Structure.terrain.masks: recursive terrain mask " + name);
        }
        TerrainFootprintPolicy declaration = masks.get(name);
        validateTerrainMaskReference(declaration, masks, "Structure.terrain.masks." + name);
        if (declaration.source == TerrainFootprintPolicy.Source.NAMED) {
            validateTerrainMask(declaration.name, masks, active, complete);
        }
        active.remove(name);
        complete.add(name);
    }

    private static void validateTerrainMaskReference(TerrainFootprintPolicy policy,
            Map<String, TerrainFootprintPolicy> masks, String path) {
        if (policy.source == TerrainFootprintPolicy.Source.NAMED && !masks.containsKey(policy.name)) {
            throw new LuaError(path + ": unknown terrain mask " + policy.name);
        }
    }

    private static FoundationPolicy foundation(LuaValue terrain, TerrainPolicy.Mode mode, int legacyBlock,
            int legacyMetadata, int legacyDepth) {
        LuaValue value = terrain.get("foundation");
        if (value.isnil()) {
            if (mode != TerrainPolicy.Mode.FOUNDATION) {
                return null;
            }
            TerrainMaterialPolicy material = TerrainMaterialPolicy.fixed(legacyBlock, legacyMetadata);
            return new FoundationPolicy(TerrainFootprintPolicy.SUPPORT, FoundationPolicy.EdgeType.HARD, 1, 0, null,
                    material, material, material, legacyDepth, TerrainReplacementPolicy.Replace.TERRAIN_AND_VEGETATION,
                    TerrainReplacementPolicy.Fluid.REJECT, 0, 0.0D, true);
        }
        if (mode != TerrainPolicy.Mode.FOUNDATION) {
            throw new LuaError("Structure.terrain.foundation: requires mode='foundation'");
        }
        FeaturePlacementApi.table(value, "Structure.terrain.foundation");
        TerrainFootprintPolicy footprint = footprint(value.get("footprint"), "Structure.terrain.foundation.footprint",
                TerrainFootprintPolicy.Source.SUPPORT, TerrainFootprintPolicy.Shape.MASK);
        LuaValue edge = value.get("edge");
        String edgeType = "hard";
        int stepEvery = 1;
        int expansion = 0;
        String edgeMask = null;
        if (!edge.isnil()) {
            FeaturePlacementApi.table(edge, "Structure.terrain.foundation.edge");
            edgeType = edge.get("type").optjstring("hard");
            stepEvery = integer(edge.get("stepEvery"), "Structure.terrain.foundation.edge.stepEvery", 1, 16, 1);
            expansion = integer(edge.get("maxExpansion"), "Structure.terrain.foundation.edge.maxExpansion", 0, 8, 0);
            edgeMask = edge.get("mask").isnil() ? null : edge.get("mask").checkjstring();
        }
        TerrainMaterialPolicy base = material(value.get("material"), "Structure.terrain.foundation.material",
                TerrainMaterialPolicy.fixed(Block.cobblestone.blockID, 0));
        TerrainMaterialPolicy cap = material(value.get("capMaterial"), "Structure.terrain.foundation.capMaterial",
                base);
        TerrainMaterialPolicy edgeMaterial = material(value.get("edgeMaterial"),
                "Structure.terrain.foundation.edgeMaterial", base);
        TerrainMaterialPolicy fill = material(value.get("fillMaterial"), "Structure.terrain.foundation.fillMaterial",
                base);
        int depth = integer(value.get("maxDepth"), "Structure.terrain.foundation.maxDepth", 1, 32, 6);
        if ((!terrain.get("foundationBlock").isnil()
                && (base.kind != TerrainMaterialPolicy.Kind.FIXED || base.blockId != legacyBlock))
                || (!terrain.get("foundationMeta").isnil()
                        && (base.kind != TerrainMaterialPolicy.Kind.FIXED || base.metadata != legacyMetadata))
                || (!terrain.get("maxFoundationDepth").isnil() && depth != legacyDepth)) {
            throw new LuaError("Structure.terrain: conflicting flat and nested foundation values");
        }
        int span = integer(value.get("maxUnsupportedSpan"), "Structure.terrain.foundation.maxUnsupportedSpan", 0, 32,
                0);
        double ratio = FeaturePlacementApi.optionalNumber(value.get("requireSupportRatio"), 0.0D,
                "Structure.terrain.foundation.requireSupportRatio", 0.0D, 1.0D);
        TerrainReplacementPolicy.Fluid fluidPolicy = TerrainReplacementPolicy.Fluid
                .parse(value.get("fluids").optjstring("reject"));
        if (fluidPolicy == TerrainReplacementPolicy.Fluid.DRAIN) {
            throw new LuaError("Structure.terrain.foundation.fluids: expected 'reject', 'replace', or 'preserve'");
        }
        return new FoundationPolicy(footprint, FoundationPolicy.EdgeType.parse(edgeType), stepEvery, expansion,
                edgeMask, cap, edgeMaterial, fill, depth,
                replacement(value.get("replace"), "Structure.terrain.foundation.replace",
                        TerrainReplacementPolicy.Replace.TERRAIN_AND_VEGETATION),
                fluidPolicy, span, ratio, integer(value.get("maxBlocks"), "Structure.terrain.foundation.maxBlocks", 1,
                        WorldGenLimits.MAX_BLOCK_CHANGES_PER_FEATURE, 4096),
                false);
    }

    private static TerracePolicy terrace(LuaValue terrain, TerrainPolicy.Mode mode, int legacyBlock, int legacyMetadata,
            int legacyCut, int legacyFill, int legacyPadding, int legacyRadius, int legacyStep) {
        LuaValue value = terrain.get("terrace");
        if (value.isnil()) {
            if (mode != TerrainPolicy.Mode.TERRACE) {
                return null;
            }
            TerrainFootprintPolicy footprint = footprint(LuaValue.NIL, "Structure.terrain.terrace.footprint",
                    TerrainFootprintPolicy.Source.BASE_BOUNDS, TerrainFootprintPolicy.Shape.BOUNDS, legacyPadding);
            TerrainMaterialPolicy material = legacyBlock == 0
                    ? TerrainMaterialPolicy.SAMPLE_SURFACE
                    : TerrainMaterialPolicy.fixed(legacyBlock, legacyMetadata);
            return new TerracePolicy(footprint,
                    legacyRadius == 0 ? TerracePolicy.TransitionType.HARD : TerracePolicy.TransitionType.GRADED,
                    legacyRadius, legacyStep, null, legacyCut, legacyFill, material,
                    legacyBlock == 0 ? TerrainMaterialPolicy.SAMPLE_SUBSURFACE : material,
                    TerrainReplacementPolicy.Replace.ORDINARY_TERRAIN, TerrainReplacementPolicy.Fluid.REJECT, true);
        }
        if (mode != TerrainPolicy.Mode.TERRACE) {
            throw new LuaError("Structure.terrain.terrace: requires mode='terrace'");
        }
        FeaturePlacementApi.table(value, "Structure.terrain.terrace");
        TerrainFootprintPolicy footprint = footprint(value.get("footprint"), "Structure.terrain.terrace.footprint",
                TerrainFootprintPolicy.Source.SUPPORT, TerrainFootprintPolicy.Shape.MASK);
        LuaValue transition = value.get("transition");
        String transitionType = "hard";
        int radius = 0;
        int step = 1;
        String transitionMask = null;
        if (!transition.isnil()) {
            FeaturePlacementApi.table(transition, "Structure.terrain.terrace.transition");
            transitionType = transition.get("type").optjstring("hard");
            radius = integer(transition.get("radius"), "Structure.terrain.terrace.transition.radius", 0, 8, 0);
            step = integer(transition.get("maxStep"), "Structure.terrain.terrace.transition.maxStep", 1, 8, 1);
            transitionMask = transition.get("mask").isnil() ? null : transition.get("mask").checkjstring();
        }
        int cut = integer(value.get("maxCutDepth"), "Structure.terrain.terrace.maxCutDepth", 0, 16, 3);
        int fill = integer(value.get("maxFillDepth"), "Structure.terrain.terrace.maxFillDepth", 0, 16, 6);
        TerrainMaterialPolicy top = material(value.get("topMaterial"), "Structure.terrain.terrace.topMaterial",
                TerrainMaterialPolicy.SAMPLE_SURFACE);
        TerrainMaterialPolicy lower = material(value.get("fillMaterial"), "Structure.terrain.terrace.fillMaterial",
                TerrainMaterialPolicy.SAMPLE_SUBSURFACE);
        if ((!terrain.get("foundationBlock").isnil()
                && (top.kind != TerrainMaterialPolicy.Kind.FIXED || lower.kind != TerrainMaterialPolicy.Kind.FIXED
                        || top.blockId != legacyBlock || lower.blockId != legacyBlock))
                || (!terrain.get("foundationMeta").isnil()
                        && (top.metadata != legacyMetadata || lower.metadata != legacyMetadata))
                || (!terrain.get("maxCutDepth").isnil() && cut != legacyCut)
                || (!terrain.get("maxFillDepth").isnil() && fill != legacyFill)
                || (!terrain.get("padding").isnil() && footprint.padding != legacyPadding)
                || (!terrain.get("blendRadius").isnil() && radius != legacyRadius)
                || (!terrain.get("maxBlendStep").isnil() && step != legacyStep)) {
            throw new LuaError("Structure.terrain: conflicting flat and nested terrace values");
        }
        TerrainReplacementPolicy.Fluid fluidPolicy = TerrainReplacementPolicy.Fluid
                .parse(value.get("fluids").optjstring("reject"));
        if (fluidPolicy == TerrainReplacementPolicy.Fluid.DRAIN) {
            throw new LuaError("Structure.terrain.terrace.fluids: expected 'reject', 'replace', or 'preserve'");
        }
        return new TerracePolicy(footprint, TerracePolicy.TransitionType.parse(transitionType), radius, step,
                transitionMask, cut, fill, top, lower,
                replacement(value.get("replace"), "Structure.terrain.terrace.replace",
                        TerrainReplacementPolicy.Replace.ORDINARY_TERRAIN),
                fluidPolicy, integer(value.get("maxBlocks"), "Structure.terrain.terrace.maxBlocks", 1,
                        WorldGenLimits.MAX_BLOCK_CHANGES_PER_FEATURE, 4096),
                false);
    }

    private static ConformPolicy conform(LuaValue terrain, TerrainPolicy.Mode mode, int legacyDisplacement,
            int legacyStep) {
        LuaValue value = terrain.get("conform");
        if (value.isnil()) {
            return mode == TerrainPolicy.Mode.CONFORM
                    ? new ConformPolicy(TerrainFootprintPolicy.SUPPORT, legacyDisplacement, legacyStep, 0, 0, false,
                            true)
                    : null;
        }
        if (mode != TerrainPolicy.Mode.CONFORM) {
            throw new LuaError("Structure.terrain.conform: requires mode='conform'");
        }
        FeaturePlacementApi.table(value, "Structure.terrain.conform");
        LuaValue columns = value.get("columns");
        if (columns.isnil()) {
            throw new LuaError("Structure.terrain.conform.columns: expected 'support', 'markers', or a selector");
        }
        boolean markerOnly = columns.isstring() && columns.checkjstring().equals("markers");
        TerrainFootprintPolicy selection = markerOnly
                ? TerrainFootprintPolicy.SUPPORT
                : footprint(columns, "Structure.terrain.conform.columns", TerrainFootprintPolicy.Source.SUPPORT,
                        TerrainFootprintPolicy.Shape.MASK);
        int displacement = integer(value.get("maxDisplacement"), "Structure.terrain.conform.maxDisplacement", 1, 16, 4);
        if (!terrain.get("maxConformDisplacement").isnil() && displacement != legacyDisplacement) {
            throw new LuaError("Structure.terrain: conflicting flat and nested conform displacement values");
        }
        int step = integer(value.get("maxStep"), "Structure.terrain.conform.maxStep", 0, 8, 1);
        int radius = 0;
        int passes = 0;
        LuaValue smoothing = value.get("smoothing");
        if (!smoothing.isnil()) {
            FeaturePlacementApi.table(smoothing, "Structure.terrain.conform.smoothing");
            radius = integer(smoothing.get("radius"), "Structure.terrain.conform.smoothing.radius", 1, 4, 1);
            passes = integer(smoothing.get("passes"), "Structure.terrain.conform.smoothing.passes", 1, 8, 1);
        }
        return new ConformPolicy(selection, displacement, step, radius, passes,
                value.get("allowEmpty").optboolean(false), markerOnly);
    }

    private static ExcavationPolicy excavation(LuaValue value) {
        if (value.isnil()) {
            return null;
        }
        FeaturePlacementApi.table(value, "Structure.terrain.excavation");
        if (!value.get("result").optjstring("air").equals("air")) {
            throw new LuaError("Structure.terrain.excavation.result: only 'air' is currently supported");
        }
        List<ExcavationPolicy.Volume> volumes = new ArrayList<ExcavationPolicy.Volume>();
        LuaValue declared = value.get("volumes");
        if (declared.isnil()) {
            volumes.add(excavationVolume(value, "Structure.terrain.excavation"));
        } else {
            if (!value.get("footprint").isnil() || !value.get("vertical").isnil() || !value.get("from").isnil()
                    || !value.get("to").isnil() || !value.get("shape").isnil() || !value.get("cells").isnil()) {
                throw new LuaError("Structure.terrain.excavation: volumes cannot be combined with a simple volume");
            }
            FeaturePlacementApi.table(declared, "Structure.terrain.excavation.volumes");
            if (declared.length() < 1 || declared.length() > 16) {
                throw new LuaError("Structure.terrain.excavation.volumes: expected 1..16 volumes");
            }
            for (int index = 1; index <= declared.length(); index++) {
                volumes.add(
                        excavationVolume(declared.get(index), "Structure.terrain.excavation.volumes[" + index + "]"));
            }
        }
        int maxBlocks = integer(value.get("maxBlocks"), "Structure.terrain.excavation.maxBlocks", 1,
                WorldGenLimits.MAX_BLOCK_CHANGES_PER_FEATURE, 4096);
        TerrainReplacementPolicy.Fluid fluidPolicy = TerrainReplacementPolicy.Fluid
                .parse(value.get("fluids").optjstring("reject"));
        if (fluidPolicy == TerrainReplacementPolicy.Fluid.REPLACE) {
            throw new LuaError("Structure.terrain.excavation.fluids: expected 'reject', 'drain', or 'preserve'");
        }
        return new ExcavationPolicy(volumes,
                replacement(value.get("replace"), "Structure.terrain.excavation.replace",
                        TerrainReplacementPolicy.Replace.ORDINARY_TERRAIN),
                fluidPolicy, 0, 0, maxBlocks, excavationMasks(value.get("masks")));
    }

    private static ExcavationPolicy.Volume excavationVolume(LuaValue value, String path) {
        FeaturePlacementApi.table(value, path);
        String shapeName = value.get("shape").optjstring("footprint");
        ExcavationPolicy.Shape shape = ExcavationPolicy.Shape.parse(shapeName);
        if (shape == ExcavationPolicy.Shape.AUTHORED) {
            LuaValue authored = value.get("cells");
            if (!authored.isnil()) {
                FeaturePlacementApi.table(authored, path + ".cells");
                if (authored.length() < 1 || authored.length() > 4096) {
                    throw new LuaError(path + ".cells: expected 1..4096 voxel positions");
                }
                List<BlockPosition> cells = new ArrayList<BlockPosition>();
                for (int index = 1; index <= authored.length(); index++) {
                    cells.add(position(authored.get(index), path + ".cells[" + index + "]"));
                }
                return new ExcavationPolicy.Volume(shape, null, null, null, null, null, null, 0, 0, 0,
                        value.get("name").isnil() ? null : value.get("name").checkjstring(), cells);
            }
            String name = value.get("name").isnil() ? null : value.get("name").checkjstring();
            if (name != null && !name.matches("[a-z][a-z0-9_.-]{0,63}")) {
                throw new LuaError(path + ".name: invalid mask name " + name);
            }
            return new ExcavationPolicy.Volume(shape, null, null, null, null, null, null, 0, 0, 0, name);
        }
        if (shape == ExcavationPolicy.Shape.FOOTPRINT) {
            TerrainFootprintPolicy footprint = footprint(value.get("footprint"), path + ".footprint",
                    TerrainFootprintPolicy.Source.BASE_BOUNDS, TerrainFootprintPolicy.Shape.MASK);
            LuaValue vertical = value.get("vertical");
            LuaValue from = value.get("from");
            LuaValue to = value.get("to");
            if (!vertical.isnil()) {
                FeaturePlacementApi.table(vertical, path + ".vertical");
                from = vertical.get("from");
                to = vertical.get("to");
            }
            return new ExcavationPolicy.Volume(shape, footprint, excavationBound(from, path + ".from", 0),
                    excavationBound(to, path + ".to", 0), null, null, null, 0, 0, 0, null);
        }
        if (shape == ExcavationPolicy.Shape.BOX) {
            BlockPosition minimum = position(required(value, "min"), path + ".min");
            BlockPosition maximum = position(required(value, "max"), path + ".max");
            if (minimum.x > maximum.x || minimum.y > maximum.y || minimum.z > maximum.z) {
                throw new LuaError(path + ": min must not exceed max");
            }
            return new ExcavationPolicy.Volume(shape, null, null, null, minimum, maximum, null, 0, 0, 0, null);
        }
        BlockPosition center = position(required(value, "center"), path + ".center");
        if (shape == ExcavationPolicy.Shape.CYLINDER) {
            int radius = integer(required(value, "radius"), path + ".radius", 1, 16);
            int height = integer(required(value, "height"), path + ".height", 1, 32);
            return new ExcavationPolicy.Volume(shape, null, null, null, null, null, center, radius, height, radius,
                    null);
        }
        LuaValue radii = required(value, "radii");
        FeaturePlacementApi.table(radii, path + ".radii");
        return new ExcavationPolicy.Volume(shape, null, null, null, null, null, center,
                integer(required(radii, "x"), path + ".radii.x", 1, 16),
                integer(required(radii, "y"), path + ".radii.y", 1, 16),
                integer(required(radii, "z"), path + ".radii.z", 1, 16), null);
    }

    private static ExcavationPolicy.Bound excavationBound(LuaValue value, String path, int fallback) {
        if (value.isnil()) {
            return new ExcavationPolicy.Bound(false, fallback);
        }
        if (value.isnumber()) {
            return new ExcavationPolicy.Bound(false, integer(value, path, -64, 64));
        }
        if (value.isstring()) {
            String text = value.checkjstring();
            if (text.equals("surface")) {
                return new ExcavationPolicy.Bound(true, 0);
            }
            if (text.matches("surface[+-][0-9]+")) {
                int offset = Integer.parseInt(text.substring(7));
                if (offset < -32 || offset > 32) {
                    throw new LuaError(path + ": surface offset must be from -32 through 32");
                }
                return new ExcavationPolicy.Bound(true, offset);
            }
        }
        FeaturePlacementApi.table(value, path);
        if (!value.get("surface").optboolean(false)) {
            throw new LuaError(path + ": expected an integer, 'surface', or {surface=true, offset=n}");
        }
        return new ExcavationPolicy.Bound(true, integer(value.get("offset"), path + ".offset", -32, 32, 0));
    }

    private static Map<String, List<BlockPosition>> excavationMasks(LuaValue value) {
        if (value.isnil()) {
            return Collections.emptyMap();
        }
        FeaturePlacementApi.table(value, "Structure.terrain.excavation.masks");
        Map<String, List<BlockPosition>> result = new LinkedHashMap<String, List<BlockPosition>>();
        for (LuaValue key : value.checktable().keys()) {
            String name = key.checkjstring();
            if (!name.matches("[a-z][a-z0-9_.-]{0,63}")) {
                throw new LuaError("Structure.terrain.excavation.masks: invalid mask name " + name);
            }
            LuaValue cells = value.get(key);
            FeaturePlacementApi.table(cells, "Structure.terrain.excavation.masks." + name);
            if (cells.length() < 1 || cells.length() > 4096) {
                throw new LuaError("Structure.terrain.excavation.masks." + name + ": expected 1..4096 positions");
            }
            List<BlockPosition> positions = new ArrayList<BlockPosition>();
            for (int index = 1; index <= cells.length(); index++) {
                positions.add(
                        position(cells.get(index), "Structure.terrain.excavation.masks." + name + "[" + index + "]"));
            }
            result.put(name, positions);
        }
        return result;
    }

    private static TerrainReplacementPolicy.Selector replacement(LuaValue value, String path,
            TerrainReplacementPolicy.Replace fallback) {
        if (value.isnil()) {
            return TerrainReplacementPolicy.Selector.preset(fallback);
        }
        if (value.isstring()) {
            return TerrainReplacementPolicy.Selector
                    .preset(TerrainReplacementPolicy.Replace.parse(value.checkjstring()));
        }
        FeaturePlacementApi.table(value, path);
        TerrainReplacementPolicy.Replace preset = value.get("preset").isnil()
                ? null
                : TerrainReplacementPolicy.Replace.parse(value.get("preset").checkjstring());
        BlockSet blocks = replacementBlocks(value.get("blocks"), path + ".blocks");
        BlockSet excludeBlocks = replacementBlocks(value.get("excludeBlocks"), path + ".excludeBlocks");
        Set<String> tags = replacementTags(value.get("tags"), path + ".tags");
        Set<String> excludeTags = replacementTags(value.get("excludeTags"), path + ".excludeTags");
        String match = value.get("match").optjstring("any");
        if (!match.equals("any") && !match.equals("all")) {
            throw new LuaError(path + ".match: expected 'any' or 'all'");
        }
        return new TerrainReplacementPolicy.Selector(preset, blocks, excludeBlocks, tags, excludeTags,
                match.equals("all"));
    }

    private static BlockSet replacementBlocks(LuaValue value, String path) {
        if (value.isnil()) {
            return new BlockSet();
        }
        return FeaturePlacementApi.blocks(value, path);
    }

    private static Set<String> replacementTags(LuaValue value, String path) {
        Set<String> tags = strings(value, path);
        for (String tag : tags) {
            if (!TerrainReplacementPolicy.validTag(tag)) {
                throw new LuaError(path + ": unknown terrain tag " + tag);
            }
        }
        return tags;
    }

    private static TerrainFootprintPolicy footprint(LuaValue value, String path,
            TerrainFootprintPolicy.Source defaultSource, TerrainFootprintPolicy.Shape defaultShape) {
        return footprint(value, path, defaultSource, defaultShape, 0);
    }

    private static TerrainFootprintPolicy footprint(LuaValue value, String path,
            TerrainFootprintPolicy.Source defaultSource, TerrainFootprintPolicy.Shape defaultShape,
            int defaultPadding) {
        if (value.isnil()) {
            return new TerrainFootprintPolicy(defaultSource, defaultShape, null, defaultPadding, 0, 0, false,
                    Collections.<String>emptySet(), Collections.<String>emptySet(), Collections.<String>emptySet(),
                    Collections.<String>emptySet(), "any", null, null, null);
        }
        if (value.isstring()) {
            String name = value.checkjstring();
            if (name.equals("support")) {
                return TerrainFootprintPolicy.SUPPORT;
            }
            if (name.equals("base_bounds")) {
                return new TerrainFootprintPolicy(TerrainFootprintPolicy.Source.BASE_BOUNDS,
                        TerrainFootprintPolicy.Shape.BOUNDS, null, 0, 0, 0, false, Collections.<String>emptySet(),
                        Collections.<String>emptySet(), Collections.<String>emptySet(), Collections.<String>emptySet(),
                        "any", null, null, null);
            }
            return new TerrainFootprintPolicy(TerrainFootprintPolicy.Source.NAMED, TerrainFootprintPolicy.Shape.MASK,
                    name, 0, 0, 0, true, Collections.<String>emptySet(), Collections.<String>emptySet(),
                    Collections.<String>emptySet(), Collections.<String>emptySet(), "any", null, null, null);
        }
        FeaturePlacementApi.table(value, path);
        String sourceName = value.get("source").optjstring(defaultSource.name().toLowerCase(java.util.Locale.ROOT));
        String name = value.get("name").isnil() ? null : value.get("name").checkjstring();
        TerrainFootprintPolicy.Source source;
        if (sourceName.equals("authored") || sourceName.equals("named")) {
            source = TerrainFootprintPolicy.Source.NAMED;
        } else {
            source = TerrainFootprintPolicy.Source.parse(sourceName);
        }
        String shapeName = value.get("shape").optjstring(defaultShape.name().toLowerCase(java.util.Locale.ROOT));
        TerrainFootprintPolicy.Shape shape = TerrainFootprintPolicy.Shape.parse(shapeName);
        if (shape == TerrainFootprintPolicy.Shape.AUTHORED && name != null) {
            source = TerrainFootprintPolicy.Source.NAMED;
        }
        LuaValue select = value.get("select");
        LuaValue exclude = value.get("exclude");
        if (select.isnil()) {
            select = new LuaTable();
        }
        if (exclude.isnil()) {
            exclude = new LuaTable();
        }
        Set<String> states = strings(select.get("states"), path + ".select.states");
        Set<String> tags = strings(select.get("tags"), path + ".select.tags");
        Set<String> excludedStates = strings(exclude.get("states"), path + ".exclude.states");
        if (excludedStates.isEmpty()) {
            excludedStates = strings(select.get("excludeStates"), path + ".select.excludeStates");
        }
        Set<String> excludedTags = strings(exclude.get("tags"), path + ".exclude.tags");
        if (excludedTags.isEmpty()) {
            excludedTags = strings(select.get("excludeTags"), path + ".select.excludeTags");
        }
        String match = select.get("match").optjstring("any");
        if (!match.equals("any") && !match.equals("all")) {
            throw new LuaError(path + ".select.match: expected 'any' or 'all'");
        }
        StructureFeature.Bounds bounds = null;
        LuaValue selectedBounds = select.get("bounds");
        if (!selectedBounds.isnil()) {
            FeaturePlacementApi.table(selectedBounds, path + ".select.bounds");
            bounds = new StructureFeature.Bounds(position(required(selectedBounds, "min"), path + ".select.bounds.min"),
                    position(required(selectedBounds, "max"), path + ".select.bounds.max"));
        }
        Integer minimumHeight = null;
        Integer maximumHeight = null;
        LuaValue height = select.get("height");
        if (!height.isnil()) {
            if (height.isnumber()) {
                minimumHeight = Integer.valueOf(integer(height, path + ".select.height", -64, 64));
                maximumHeight = minimumHeight;
            } else {
                FeaturePlacementApi.table(height, path + ".select.height");
                minimumHeight = Integer.valueOf(integer(required(height, "min"), path + ".select.height.min", -64, 64));
                maximumHeight = Integer.valueOf(integer(required(height, "max"), path + ".select.height.max", -64, 64));
            }
        }
        List<BlockPosition> cells = new ArrayList<BlockPosition>();
        LuaValue authoredCells = value.get("cells");
        if (!authoredCells.isnil()) {
            FeaturePlacementApi.table(authoredCells, path + ".cells");
            if (authoredCells.length() < 1 || authoredCells.length() > 4096) {
                throw new LuaError(path + ".cells: expected 1..4096 two-dimensional positions");
            }
            for (int index = 1; index <= authoredCells.length(); index++) {
                cells.add(position2(authoredCells.get(index), path + ".cells[" + index + "]"));
            }
            shape = TerrainFootprintPolicy.Shape.AUTHORED;
        }
        String nearMarker = null;
        int nearMarkerRadius = 0;
        LuaValue near = select.get("nearMarker");
        if (near.isstring()) {
            nearMarker = near.checkjstring();
        } else if (!near.isnil()) {
            FeaturePlacementApi.table(near, path + ".select.nearMarker");
            nearMarker = required(near, "name").checkjstring();
            nearMarkerRadius = integer(near.get("radius"), path + ".select.nearMarker.radius", 0, 16, 0);
        }
        return new TerrainFootprintPolicy(source, shape, name,
                integer(value.get("padding"), path + ".padding", 0, 8, defaultPadding),
                integer(value.get("inset"), path + ".inset", 0, 8, 0),
                integer(value.get("cornerRadius"), path + ".cornerRadius", 0, 16, 0),
                value.get("preserveHoles").optboolean(false), states, excludedStates, tags, excludedTags, match, bounds,
                minimumHeight, maximumHeight, cells, nearMarker, nearMarkerRadius);
    }

    private static TerrainMaterialPolicy material(LuaValue value, String path, TerrainMaterialPolicy fallback) {
        if (value.isnil()) {
            return fallback;
        }
        if (value.isstring()) {
            String name = value.checkjstring();
            if (name.equals("sample_surface")) {
                return TerrainMaterialPolicy.SAMPLE_SURFACE;
            }
            if (name.equals("sample_subsurface")) {
                return TerrainMaterialPolicy.SAMPLE_SUBSURFACE;
            }
            int block = FeaturePlacementApi.blockId(value, path);
            validateTerrainMaterial(block, path);
            return TerrainMaterialPolicy.fixed(block, 0);
        }
        if (!value.istable()) {
            int block = FeaturePlacementApi.blockId(value, path);
            validateTerrainMaterial(block, path);
            return TerrainMaterialPolicy.fixed(block, 0);
        }
        FeaturePlacementApi.table(value, path);
        LuaValue layers = value.get("layers");
        if (!layers.isnil()) {
            FeaturePlacementApi.table(layers, path + ".layers");
            List<TerrainMaterialPolicy.State> states = new ArrayList<TerrainMaterialPolicy.State>();
            for (int index = 1; index <= layers.length(); index++) {
                LuaValue entry = layers.get(index);
                FeaturePlacementApi.table(entry, path + ".layers[" + index + "]");
                int block = FeaturePlacementApi.blockId(required(entry, "block"),
                        path + ".layers[" + index + "].block");
                validateTerrainMaterial(block, path + ".layers[" + index + "].block");
                states.add(new TerrainMaterialPolicy.State(block,
                        integer(entry.get("metadata"), path + ".layers[" + index + "].metadata", 0, 15, 0),
                        integer(entry.get("weight"), path + ".layers[" + index + "].weight", 1, 1024, 1)));
            }
            if (states.isEmpty() || states.size() > 64) {
                throw new LuaError(path + ".layers: expected 1..64 entries");
            }
            return new TerrainMaterialPolicy(TerrainMaterialPolicy.Kind.PALETTE, 0, 0, states);
        }
        int block = FeaturePlacementApi.blockId(required(value, "block"), path + ".block");
        int metadata = integer(value.get("metadata"), path + ".metadata", 0, 15, 0);
        validateTerrainMaterial(block, path);
        return TerrainMaterialPolicy.fixed(block, metadata);
    }

    private static void validateTerrainMaterial(int block, String path) {
        if (block == 0 || Block.blocksList[block] == null || !Block.blocksList[block].blockMaterial.getIsSolid()
                || Block.blocksList[block] instanceof BlockContainer) {
            throw new LuaError(path + ": expected a structurally solid non-tile block");
        }
    }

    private static Set<String> strings(LuaValue value, String path) {
        if (value.isnil()) {
            return Collections.emptySet();
        }
        FeaturePlacementApi.table(value, path);
        Set<String> result = new LinkedHashSet<String>();
        for (int index = 1; index <= value.length(); index++) {
            result.add(value.get(index).checkjstring());
        }
        return result;
    }

    private static BlockPosition position(LuaValue value, String path) {
        FeaturePlacementApi.table(value, path);
        if (value.length() >= 3) {
            return new BlockPosition(integer(value.get(1), path + "[1]", -64, 64),
                    integer(value.get(2), path + "[2]", -64, 64), integer(value.get(3), path + "[3]", -64, 64));
        }
        return new BlockPosition(integer(required(value, "x"), path + ".x", -64, 64),
                integer(required(value, "y"), path + ".y", -64, 64),
                integer(required(value, "z"), path + ".z", -64, 64));
    }

    private static BlockPosition position2(LuaValue value, String path) {
        FeaturePlacementApi.table(value, path);
        if (value.length() >= 2) {
            return new BlockPosition(integer(value.get(1), path + "[1]", -64, 64), 0,
                    integer(value.get(2), path + "[2]", -64, 64));
        }
        return new BlockPosition(integer(required(value, "x"), path + ".x", -64, 64), 0,
                integer(required(value, "z"), path + ".z", -64, 64));
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
            throw new LuaError(
                    "Structure dimensions exceed maximum placement radius " + WorldGenLimits.MAX_FEATURE_RADIUS);
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
        List<RegionalStructureDefinition.PieceChoice> result = new ArrayList<RegionalStructureDefinition.PieceChoice>();
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
            return new Height(type, integer(value.get("offset"), "RegionalStructure.height.offset", -127, 127, 0));
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

    private static final class TemplateSource {
        private final StructureTemplate template;
        private final String displayPath;

        private TemplateSource(StructureTemplate template, String displayPath) {
            this.template = template;
            this.displayPath = displayPath;
        }
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
