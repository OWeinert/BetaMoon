package betamoon.worldgen;

import betamoon.worldgen.structure.StructureFeature;
import betamoon.worldgen.structure.BlockStateTransformRegistry;
import betamoon.worldgen.structure.CustomMetadataTransform;
import betamoon.worldgen.structure.StructureProcessors;
import betamoon.worldgen.structure.StructureTemplate;
import betamoon.worldgen.structure.StructureTransform;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Random;
import net.minecraft.src.Block;
import net.minecraft.src.Chunk;
import net.minecraft.src.IChunkProvider;
import net.minecraft.src.IProgressUpdate;
import net.minecraft.src.World;
import net.minecraft.src.WorldProvider;

/** Verifies bounded tree planning, structure parsing/transforms, previews, and atomic failure. */
public final class StructureTreeFeatureTest {
    private StructureTreeFeatureTest() {
    }

    public static void main(String[] arguments) throws Exception {
        require(Block.stone != null, "Vanilla blocks are initialized");
        StructureTemplate template = StructureTemplate.read(("{"
                + "\"format\":\"betamoon_structure\",\"size\":[3,2,2],\"origin\":[1,0,0],"
                + "\"palette\":[{\"block\":\"minecraft:stone\",\"meta\":0},"
                + "{\"variants\":[{\"block\":3,\"weight\":2},{\"block\":4,\"weight\":1}]}],"
                + "\"blocks\":[{\"pos\":[1,0,0],\"state\":0},{\"pos\":[2,1,1],\"state\":1}],"
                + "\"markers\":[{\"pos\":[1,0,0],\"name\":\"loot\",\"value\":\"test:ruin\"}]}"
                ).getBytes(StandardCharsets.UTF_8));
        require(template.blocks.size() == 2 && template.markers.size() == 1,
                "Readable palette blocks and markers parse");
        BlockPosition rotated = new StructureTransform(StructureTransform.Rotation.CLOCKWISE_90,
                StructureTransform.Mirror.NONE).apply(2, 1, 3);
        require(rotated.x == -3 && rotated.y == 1 && rotated.z == 2,
                "Horizontal transforms rotate around the declared origin");
        verifyMetadataTransforms();

        TestWorld world = new TestWorld();
        StructureFeature structure = new StructureFeature(template, "memory:test.json", "clockwise_90", "none",
                new StructureProcessors(false, Collections.<Integer, Integer>emptyMap(), 0.0D, null, "reject",
                        "reject"));
        FeatureContext structureContext = context(world, 17L);
        PlacementPlan structurePlan = new PlacementPlan(new BlockPosition(8, 64, 8), 16, 8);
        FeatureResult planned = structure.plan(structureContext, new BlockPosition(8, 64, 8), structurePlan);
        require(planned.placed && structurePlan.size() == 2, "Structure uses the bounded placement plan");
        require(structurePlan.preview(structureContext).placed && world.getBlockId(8, 64, 8) == 0,
                "Preview validates the real plan without changing blocks");
        require(structurePlan.commit(structureContext).placed && world.getBlockId(8, 64, 8) == Block.stone.blockID,
                "Structure plan commits atomically");

        TreeFeature tree = new TreeFeature(Block.wood.blockID, 0, new IntRange(4, 4), 1, 0.0D, null,
                Block.leaves.blockID, 0, "sphere", new IntRange(2, 2), 1.0D,
                new BlockSet(Block.grass.blockID, Block.dirt.blockID), new BlockSet(0, Block.leaves.blockID));
        world.setBlockAndMetadata(4, 63, 4, Block.grass.blockID, 0);
        FeatureContext treeContext = context(world, 41L);
        PlacementPlan treePlan = new PlacementPlan(new BlockPosition(4, 64, 4), 512, 16);
        require(tree.plan(treeContext, new BlockPosition(4, 64, 4), treePlan).placed && treePlan.size() > 4,
                "Procedural tree compiles trunk and canopy into one plan");

        int before = world.getBlockId(15, 64, 15);
        PlacementPlan outside = new PlacementPlan(new BlockPosition(15, 64, 15), 8, 4);
        outside.setBlock(15, 64, 15, Block.stone.blockID, 0);
        outside.setBlock(16, 64, 15, Block.stone.blockID, 0);
        require(!outside.commit(context(world, 9L)).placed && world.getBlockId(15, 64, 15) == before,
                "An unloaded target rejects the complete plan before mutation");
        System.out.println("Structure/tree feature checks passed.");
    }

    private static void verifyMetadataTransforms() throws Exception {
        StructureTransform clockwise = new StructureTransform(StructureTransform.Rotation.CLOCKWISE_90,
                StructureTransform.Mirror.NONE);
        require(transformed("stairCompactPlanks", 0, clockwise) == 2, "Stair facing rotates");
        require(transformed("doorWood", 0, clockwise) == 1, "Door lower-half facing rotates");
        require(transformed("rail", 6, clockwise) == 7, "Curved rail shape rotates");
        require(transformed("torchWood", 1, clockwise) == 3, "Wall torch facing rotates");
        require(transformed("ladder", 2, clockwise) == 5, "Ladder facing rotates");
        require(transformed("blockBed", 0, clockwise) == 1, "Bed facing rotates");
        require(transformed("stoneOvenIdle", 2, clockwise) == 5, "Furnace facing rotates");
        require(transformed("signPost", 0, clockwise) == 4, "Standing sign angle rotates");
        require(transformed("pumpkin", 0, clockwise) == 1, "Pumpkin facing rotates");
        require(transformed("button", 1, clockwise) == 3, "Button facing rotates");
        require(transformed("lever", 1, clockwise) == 3, "Lever facing rotates");
        int[] clockwiseMap = identityMetadata();
        clockwiseMap[2] = 9;
        CustomMetadataTransform custom = new CustomMetadataTransform(clockwiseMap, identityMetadata(),
                identityMetadata());
        require(custom.transform(2, clockwise) == 9, "Custom block metadata maps are supported");
    }

    private static int transformed(String field, int metadata, StructureTransform transform) throws Exception {
        Block block = (Block) Block.class.getField(field).get(null);
        return BlockStateTransformRegistry.transform(block.blockID, metadata, transform, "reject");
    }

    private static int[] identityMetadata() {
        int[] result = new int[16];
        for (int index = 0; index < result.length; index++) {
            result[index] = index;
        }
        return result;
    }

    private static FeatureContext context(World world, long seed) {
        return new FeatureContext(world, new Random(seed), WorldGenKey.parse("test:feature/unit", WorldGenKind.FEATURE),
                4096, null);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static final class TestWorld extends World {
        private final Chunk chunk;

        private TestWorld() {
            super(null, "structure_tree_test", new WorldProvider() {
            }, 123L);
            chunk = new Chunk(this, new byte[32768], 0, 0);
            chunk.isChunkLoaded = true;
            chunkProvider = new IChunkProvider() {
                public boolean chunkExists(int x, int z) { return x == 0 && z == 0; }
                public Chunk provideChunk(int x, int z) { return chunk; }
                public Chunk prepareChunk(int x, int z) { return chunk; }
                public void populate(IChunkProvider provider, int x, int z) { }
                public boolean saveChunks(boolean force, IProgressUpdate progress) { return true; }
                public boolean unload100OldestChunks() { return false; }
                public boolean canSave() { return false; }
                public String makeString() { return "StructureTreeTest"; }
            };
        }

        @Override
        protected IChunkProvider getChunkProvider() { return null; }

        @Override
        public Chunk getChunkFromChunkCoords(int x, int z) { return chunk; }

        @Override
        public boolean blockExists(int x, int y, int z) {
            return x >= 0 && x < 16 && z >= 0 && z < 16 && y >= 0 && y < 128;
        }
    }
}
