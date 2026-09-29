package betamoon.worldgen;

import betamoon.worldgen.structure.SitePolicy;
import betamoon.worldgen.structure.StructureFeature;
import betamoon.worldgen.structure.StructureProcessors;
import betamoon.worldgen.structure.StructureTemplate;
import betamoon.worldgen.structure.StructureTransform;
import betamoon.worldgen.structure.TerrainPolicy;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Random;
import net.minecraft.src.Block;
import net.minecraft.src.Chunk;
import net.minecraft.src.IChunkProvider;
import net.minecraft.src.IProgressUpdate;
import net.minecraft.src.World;
import net.minecraft.src.WorldProvider;

/** Verifies shared surface sampling, footprint fitting, foundations, and site eligibility. */
public final class StructureTerrainPlacementTest {
    private StructureTerrainPlacementTest() {
    }

    public static void main(String[] arguments) throws Exception {
        TestWorld world = new TestWorld();
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                world.setBlockAndMetadata(x, 60, z, Block.stone.blockID, 0);
            }
        }
        world.setBlockAndMetadata(10, 61, 10, Block.waterStill.blockID, 0);
        world.setBlockAndMetadata(10, 62, 10, Block.waterStill.blockID, 0);
        world.setBlockAndMetadata(10, 63, 10, Block.waterStill.blockID, 0);

        FeatureContext sampling = context(world, 1L, SitePolicy.ANY);
        require(sampling.surfaceHeight(10, 10, TerrainSurface.OCEAN_FLOOR) == 61,
                "Ocean-floor sampling skips the complete water column");
        require(sampling.surfaceHeight(10, 10, TerrainSurface.FLUID_SURFACE) == 64,
                "Fluid-surface sampling returns the cell above the fluid column");
        require(sampling.surfaceHeight(10, 10, TerrainSurface.SOLID_SURFACE) == 61,
                "Solid-surface sampling ignores fluid rather than aliasing the height map");

        StructureTemplate pair = template(2);
        world.setBlockAndMetadata(4, 60, 4, Block.stone.blockID, 0);
        world.setBlockAndMetadata(5, 60, 4, 0, 0);
        world.setBlockAndMetadata(5, 58, 4, Block.stone.blockID, 0);
        TerrainPolicy foundation = new TerrainPolicy(TerrainPolicy.Mode.FOUNDATION,
                TerrainSurface.SOLID_SURFACE, TerrainPolicy.Anchor.MAXIMUM, 0.5D, 4, 4, 0.0D, 0,
                Block.cobblestone.blockID, 0, 4, 0, 0, 0, 0, 1, 4);
        StructureFeature supported = feature(pair, foundation);
        FeatureContext foundationContext = context(world, 2L, SitePolicy.ANY);
        PlacementPlan foundationPlan = new PlacementPlan(new BlockPosition(4, 80, 4), 32, 32);
        FeatureResult foundationResult = supported.plan(foundationContext, new BlockPosition(4, 80, 4),
                foundationPlan);
        require(foundationResult.placed && foundationPlan.size() == 4,
                "Foundation fitting includes structure blocks and bounded support changes in one plan");
        FeatureResult committedFoundation = foundationPlan.commit(foundationContext);
        require(committedFoundation.placed
                && world.getBlockId(5, 59, 4) == Block.cobblestone.blockID
                && world.getBlockId(5, 61, 4) == Block.stone.blockID
                && ((Number) committedFoundation.details.get("resolvedAnchorY")).intValue() == 61,
                "Foundation placement fills a low support column and keeps the structure rigid");

        StructureFeature exact = feature(template(1), TerrainPolicy.EXACT);
        SitePolicy land = new SitePolicy(SitePolicy.Type.LAND_SURFACE, SitePolicy.Scope.SUPPORT_FOOTPRINT,
                SitePolicy.Medium.ANY, 0, 127, 0, 127, 0.0D, 0.0D, 0.0D, 0, 127, null);
        FeatureContext landContext = context(world, 3L, land);
        PlacementPlan rejectedPlan = new PlacementPlan(new BlockPosition(10, 61, 10), 8, 8);
        FeatureResult rejected = exact.plan(landContext, new BlockPosition(10, 61, 10), rejectedPlan);
        require(!rejected.placed && FeatureResult.SITE_WRONG_SURFACE_RELATION.equals(rejected.reason)
                && rejectedPlan.size() == 0, "Land-only structures reject submerged footprints before mutation");

        SitePolicy underwater = new SitePolicy(SitePolicy.Type.UNDERWATER, SitePolicy.Scope.SUPPORT_FOOTPRINT,
                SitePolicy.Medium.WATER, 0, 127, 2, 4, 1.0D, 1.0D, 0.0D, 0, 127, null);
        FeatureContext waterContext = context(world, 4L, underwater);
        PlacementPlan waterPlan = new PlacementPlan(new BlockPosition(10, 61, 10), 8, 8);
        require(exact.plan(waterContext, new BlockPosition(10, 61, 10), waterPlan).placed,
                "Underwater profiles accept a sufficiently deep water-covered footprint");

        TerrainPolicy strictFit = new TerrainPolicy(TerrainPolicy.Mode.FIT, TerrainSurface.SOLID_SURFACE,
                TerrainPolicy.Anchor.MEDIAN, 0.5D, 0, 0, 0.0D, 0, 0, 0, 0, 0, 0, 0, 0, 1, 4);
        StructureFeature strict = feature(pair, strictFit);
        world.setBlockAndMetadata(3, 60, 8, 0, 0);
        world.setBlockAndMetadata(3, 58, 8, Block.stone.blockID, 0);
        PlacementPlan slopePlan = new PlacementPlan(new BlockPosition(2, 80, 8), 16, 32);
        FeatureResult slope = strict.plan(context(world, 5L, SitePolicy.ANY), new BlockPosition(2, 80, 8),
                slopePlan);
        require(!slope.placed && FeatureResult.TERRAIN_SLOPE.equals(slope.reason) && slopePlan.size() == 0,
                "A rejected footprint leaves the atomic plan empty");

        world.setBlockAndMetadata(7, 61, 8, Block.stone.blockID, 0);
        TerrainPolicy conform = new TerrainPolicy(TerrainPolicy.Mode.CONFORM, TerrainSurface.SOLID_SURFACE,
                TerrainPolicy.Anchor.MEDIAN, 0.5D, 4, 1, 0.0D, 0, 0, 0, 0, 0, 0, 0, 0, 1, 4);
        StructureFeature path = feature(conformTemplate(), conform);
        PlacementPlan conformPlan = new PlacementPlan(new BlockPosition(6, 61, 8), 16, 8);
        FeatureContext conformContext = context(world, 6L, SitePolicy.ANY);
        require(path.plan(conformContext, new BlockPosition(6, 61, 8), conformPlan).placed
                && conformPlan.contains(6, 61, 8) && conformPlan.contains(7, 62, 8),
                "Conform markers move only their transformed columns and respect neighboring steps");
        StructureFeature.RegionalPlacement regional = path.resolveRegional(context(world, 7L, SitePolicy.ANY),
                new BlockPosition(6, 61, 8), new StructureTransform(StructureTransform.Rotation.NONE,
                        StructureTransform.Mirror.NONE), SitePolicy.ANY);
        require(regional.accepted && regional.conformOffsets.size() == 2
                && regional.bounds.max.y == 62,
                "Regional planning resolves and bounds conform offsets before chunk persistence");

        world.setBlockAndMetadata(13, 61, 12, Block.stone.blockID, 0);
        world.setBlockAndMetadata(13, 62, 12, Block.stone.blockID, 0);
        TerrainPolicy terrace = new TerrainPolicy(TerrainPolicy.Mode.TERRACE, TerrainSurface.SOLID_SURFACE,
                TerrainPolicy.Anchor.MINIMUM, 0.5D, 4, 4, 0.0D, 0, 0, 0, 0, 3, 3, 0, 0, 1, 4);
        StructureFeature terraced = feature(pair, terrace);
        PlacementPlan terracePlan = new PlacementPlan(new BlockPosition(12, 80, 12), 32, 32);
        FeatureContext terraceContext = context(world, 8L, SitePolicy.ANY);
        require(terraced.plan(terraceContext, new BlockPosition(12, 80, 12), terracePlan).placed
                && plannedBlock(terracePlan, 13, 62, 12) == 0,
                "Terrace mode plans bounded cuts and structure blocks atomically");
        System.out.println("Structure terrain placement checks passed.");
    }

    private static StructureFeature feature(StructureTemplate template, TerrainPolicy terrain) {
        return new StructureFeature(template, "memory:terrain.json", "none", "none",
                new StructureProcessors(false, Collections.<Integer, Integer>emptyMap(), 0.0D, null, "reject",
                        "reject"), terrain);
    }

    private static StructureTemplate template(int width) throws Exception {
        StringBuilder blocks = new StringBuilder();
        for (int x = 0; x < width; x++) {
            if (x > 0) {
                blocks.append(',');
            }
            blocks.append("{\"pos\":[").append(x).append(",0,0],\"state\":0}");
        }
        return StructureTemplate.read(("{\"format\":\"betamoon_structure\",\"size\":[" + width
                + ",1,1],\"origin\":[0,0,0],\"palette\":[{\"block\":\"minecraft:stone\"}],\"blocks\":["
                + blocks + "]}").getBytes(StandardCharsets.UTF_8));
    }

    private static StructureTemplate conformTemplate() throws Exception {
        return StructureTemplate.read(("{\"format\":\"betamoon_structure\",\"size\":[2,1,1],"
                + "\"origin\":[0,0,0],\"palette\":[{\"block\":\"minecraft:stone\"}],"
                + "\"blocks\":[{\"pos\":[0,0,0],\"state\":0},{\"pos\":[1,0,0],\"state\":0}],"
                + "\"markers\":[{\"pos\":[0,0,0],\"name\":\"terrain_conform\"},"
                + "{\"pos\":[1,0,0],\"name\":\"terrain_conform\"}]}")
                .getBytes(StandardCharsets.UTF_8));
    }

    private static FeatureContext context(World world, long seed, SitePolicy site) {
        return new FeatureContext(world, new Random(seed),
                WorldGenKey.parse("test:feature/terrain", WorldGenKind.FEATURE), 32768, null,
                FeatureOptions.DEFAULT, site);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static int plannedBlock(PlacementPlan plan, int x, int y, int z) {
        for (PlacementPlan.PlannedBlock block : plan.plannedBlocks()) {
            if (block.position.x == x && block.position.y == y && block.position.z == z) {
                return block.blockId;
            }
        }
        return -1;
    }

    private static final class TestWorld extends World {
        private final Chunk chunk;

        private TestWorld() {
            super(null, "structure_terrain_test", new WorldProvider() {
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
                public String makeString() { return "StructureTerrainTest"; }
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
