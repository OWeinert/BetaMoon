package betamoon.luaapi.world;

import betamoon.worldgen.BlockSet;
import betamoon.worldgen.IntRange;
import betamoon.worldgen.TreeFeature;
import betamoon.worldgen.StructureTreeFeature;
import betamoon.worldgen.WorldFeature;
import betamoon.worldgen.WorldGenKey;
import betamoon.worldgen.WorldGenKind;
import betamoon.worldgen.WorldGenLimits;
import betamoon.worldgen.WorldGenRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.src.Block;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;
import static betamoon.luaapi.utils.LuaDeclarationValues.required;

/** Installs vanilla-adapter, procedural, and structure-backed tree declarations. */
public final class TreeGenApi {
    private TreeGenApi() {
    }

    public static void attach(LuaTable worldgen) {
        final LuaTable trees = new LuaTable();
        trees.set("add", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs arguments) {
                LuaValue definition = argument(arguments, trees, 1);
                FeaturePlacementApi.table(definition, "worldgen.trees:add");
                String key = required(definition, "key").checkjstring();
                CompiledTree compiled = compile(definition);
                WorldGenKey typed = WorldGenRegistry.addFeature(key, WorldGenKind.TREE, compiled.type,
                        compiled.feature, compiled.dependencies, compiled.maxBlocks, compiled.maxRadius);
                return new FeatureReference(typed);
            }
        });
        trees.set("get", lookup(trees, false));
        trees.set("getRequired", lookup(trees, true));
        worldgen.set("trees", trees);
    }

    private static VarArgFunction lookup(final LuaTable trees, final boolean required) {
        return new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs arguments) {
                String value = argument(arguments, trees, 1).checkjstring();
                WorldGenKey key;
                try {
                    key = WorldGenKey.parse(value, WorldGenKind.TREE);
                } catch (IllegalArgumentException error) {
                    throw new LuaError("Tree.get: " + error.getMessage());
                }
                if (!WorldGenRegistry.hasFeature(key)) {
                    if (required) {
                        throw new LuaError("Tree is not registered: " + key);
                    }
                    return NIL;
                }
                return new FeatureReference(key);
            }
        };
    }

    private static CompiledTree compile(LuaValue definition) {
        if (!definition.get("templates").isnil()) {
            LuaValue templates = definition.get("templates");
            FeaturePlacementApi.table(templates, "Tree.templates");
            List<WorldGenKey> dependencies = new ArrayList<WorldGenKey>();
            int[] weights = new int[templates.length()];
            int total = 0;
            for (int index = 1; index <= templates.length(); index++) {
                LuaValue entry = templates.get(index);
                FeaturePlacementApi.table(entry, "Tree.templates[" + index + "]");
                dependencies.add(FeaturePlacementApi.featureKey(required(entry, "structure"),
                        "Tree.templates[" + index + "].structure"));
                int weight = FeaturePlacementApi.optionalInteger(entry.get("weight"), 1,
                        "Tree.templates[" + index + "].weight", 1, 1000000);
                total += weight;
                weights[index - 1] = total;
            }
            if (dependencies.isEmpty()) {
                throw new LuaError("Tree.templates: expected at least one structure");
            }
            BlockSet ground = definition.get("ground").isnil()
                    ? new BlockSet(Block.grass.blockID, Block.dirt.blockID)
                    : FeaturePlacementApi.blocks(definition.get("ground"), "Tree.ground");
            String rotation = definition.get("rotation").optjstring("random_horizontal");
            String mirror = definition.get("mirror").optjstring("none");
            try {
                betamoon.worldgen.structure.StructureTransform.rotation(rotation, new java.util.Random(0L));
                betamoon.worldgen.structure.StructureTransform.mirror(mirror, new java.util.Random(0L));
            } catch (IllegalArgumentException error) {
                throw new LuaError("Tree transform: " + error.getMessage());
            }
            WorldFeature structureTree = new StructureTreeFeature(dependencies, weights, ground, rotation, mirror);
            return new CompiledTree("structure_tree", structureTree, dependencies,
                    WorldGenLimits.MAX_BLOCK_CHANGES_PER_FEATURE, WorldGenLimits.MAX_FEATURE_RADIUS);
        }

        String generator = definition.get("generator").optjstring("").toLowerCase(Locale.ROOT);
        LuaValue trunk = definition.get("trunk");
        LuaValue canopy = definition.get("canopy");
        int trunkBlock;
        int trunkMetadata;
        IntRange height;
        int radius;
        double bend;
        int leavesBlock;
        int leavesMetadata;
        String shape;
        IntRange canopyRadius;
        double density;
        TreeFeature.Branches branches = null;

        if (!generator.isEmpty()) {
            trunkBlock = definition.get("trunk").isnil() ? Block.wood.blockID
                    : FeaturePlacementApi.blockId(definition.get("trunk"), "Tree.trunk");
            leavesBlock = definition.get("leaves").isnil() ? Block.leaves.blockID
                    : FeaturePlacementApi.blockId(definition.get("leaves"), "Tree.leaves");
            trunkMetadata = FeaturePlacementApi.metadata(definition.get("trunkMetadata"), "Tree.trunkMetadata");
            leavesMetadata = FeaturePlacementApi.metadata(definition.get("leavesMetadata"), "Tree.leavesMetadata");
            if (generator.equals("minecraft:normal") || generator.equals("normal")) {
                height = new IntRange(4, 6);
                shape = "layered_disk";
                canopyRadius = new IntRange(2, 3);
            } else if (generator.equals("minecraft:big") || generator.equals("big")) {
                height = new IntRange(7, 11);
                shape = "sphere";
                canopyRadius = new IntRange(3, 4);
                branches = new TreeFeature.Branches(0.55D, new IntRange(2, 5), new IntRange(2, 4), 0.25D);
            } else if (generator.equals("minecraft:taiga") || generator.equals("taiga")) {
                height = new IntRange(6, 10);
                shape = "cone";
                canopyRadius = new IntRange(2, 3);
            } else {
                throw new LuaError("Tree.generator: unknown vanilla adapter: " + generator);
            }
            radius = 1;
            bend = 0.0D;
            density = 0.9D;
        } else {
            FeaturePlacementApi.table(trunk, "Tree.trunk");
            FeaturePlacementApi.table(canopy, "Tree.canopy");
            trunkBlock = FeaturePlacementApi.blockId(required(trunk, "block"), "Tree.trunk.block");
            trunkMetadata = FeaturePlacementApi.metadata(trunk.get("metadata"), "Tree.trunk.metadata");
            height = FeaturePlacementApi.range(required(trunk, "height"), "Tree.trunk.height", 1, 24);
            radius = FeaturePlacementApi.optionalInteger(trunk.get("radius"), 1, "Tree.trunk.radius", 1, 2);
            bend = FeaturePlacementApi.optionalNumber(trunk.get("bend"), 0.0D, "Tree.trunk.bend", 0.0D, 1.0D);
            leavesBlock = FeaturePlacementApi.blockId(required(canopy, "block"), "Tree.canopy.block");
            leavesMetadata = FeaturePlacementApi.metadata(canopy.get("metadata"), "Tree.canopy.metadata");
            shape = canopy.get("shape").optjstring("sphere");
            if (!shape.equals("sphere") && !shape.equals("layered_disk") && !shape.equals("cone")
                    && !shape.equals("clustered_sphere")) {
                throw new LuaError("Tree.canopy.shape: unsupported shape: " + shape);
            }
            canopyRadius = FeaturePlacementApi.range(required(canopy, "radius"), "Tree.canopy.radius", 1, 8);
            density = FeaturePlacementApi.optionalNumber(canopy.get("density"), 1.0D, "Tree.canopy.density", 0.05D,
                    1.0D);
            LuaValue branchValue = definition.get("branches");
            if (!branchValue.isnil()) {
                FeaturePlacementApi.table(branchValue, "Tree.branches");
                double start = FeaturePlacementApi.optionalNumber(branchValue.get("start"), 0.55D,
                        "Tree.branches.start", 0.1D, 0.95D);
                IntRange count = FeaturePlacementApi.range(required(branchValue, "count"), "Tree.branches.count", 0,
                        16);
                IntRange length = FeaturePlacementApi.range(required(branchValue, "length"),
                        "Tree.branches.length", 1, 12);
                double bias = FeaturePlacementApi.optionalNumber(branchValue.get("upwardBias"), 0.25D,
                        "Tree.branches.upwardBias", 0.0D, 1.0D);
                branches = new TreeFeature.Branches(start, count, length, bias);
            }
        }

        BlockSet ground = definition.get("ground").isnil()
                ? new BlockSet(Block.grass.blockID, Block.dirt.blockID)
                : FeaturePlacementApi.blocks(definition.get("ground"), "Tree.ground");
        BlockSet replace = definition.get("replace").isnil() ? new BlockSet(0, Block.leaves.blockID)
                : FeaturePlacementApi.blocks(definition.get("replace"), "Tree.replace");
        WorldFeature feature = new TreeFeature(trunkBlock, trunkMetadata, height, radius, bend, branches, leavesBlock,
                leavesMetadata, shape, canopyRadius, density, ground, replace);
        int maxRadius = Math.min(WorldGenLimits.MAX_FEATURE_RADIUS,
                Math.max(height.max + canopyRadius.max, canopyRadius.max + 12));
        return new CompiledTree(generator.isEmpty() ? "procedural_tree" : "vanilla_tree", feature,
                new ArrayList<WorldGenKey>(), WorldGenLimits.MAX_BLOCK_CHANGES_PER_FEATURE, maxRadius);
    }

    private static LuaValue argument(Varargs arguments, LuaValue receiver, int index) {
        return arguments.arg(index + (arguments.arg1() == receiver ? 1 : 0));
    }

    private static final class CompiledTree {
        private final String type;
        private final WorldFeature feature;
        private final List<WorldGenKey> dependencies;
        private final int maxBlocks;
        private final int maxRadius;

        private CompiledTree(String type, WorldFeature feature, List<WorldGenKey> dependencies, int maxBlocks,
                int maxRadius) {
            this.type = type;
            this.feature = feature;
            this.dependencies = dependencies;
            this.maxBlocks = maxBlocks;
            this.maxRadius = maxRadius;
        }
    }
}
