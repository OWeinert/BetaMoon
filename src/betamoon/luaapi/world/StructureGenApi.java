package betamoon.luaapi.world;

import betamoon.luamodloader.LuaScriptRegistry;
import betamoon.worldgen.BlockSet;
import betamoon.worldgen.WorldGenKey;
import betamoon.worldgen.WorldGenKind;
import betamoon.worldgen.WorldGenLimits;
import betamoon.worldgen.WorldGenRegistry;
import betamoon.worldgen.structure.StructureExporter;
import betamoon.worldgen.structure.CustomMetadataTransform;
import betamoon.worldgen.structure.StructureFeature;
import betamoon.worldgen.structure.StructureProcessors;
import betamoon.worldgen.structure.StructureTemplate;
import betamoon.worldgen.structure.StructureTransform;
import betamoon.worldgen.structure.WorldGenDataResolver;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
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
                    StructureFeature feature = new StructureFeature(template, data.displayPath, rotation, mirror,
                            processors);
                    int radius = radius(template);
                    WorldGenRegistry.addFeature(declaredKey, WorldGenKind.STRUCTURE, "local_structure", feature,
                            Collections.<WorldGenKey>emptyList(), Math.max(1, template.blocks.size()), radius);
                    return new FeatureReference(key);
                } catch (IOException error) {
                    throw new LuaError("Structure " + key + ": " + error.getMessage());
                }
            }
        });
        structures.set("get", lookup(structures, false));
        structures.set("getRequired", lookup(structures, true));
        structures.set("export", exporter(structures));
        worldgen.set("structures", structures);
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
}
