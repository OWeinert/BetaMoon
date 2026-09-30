package betamoon.worldgen;

import betamoon.worldgen.structure.SitePolicy;
import betamoon.worldgen.structure.ExcavationPolicy;
import betamoon.worldgen.structure.FoundationPolicy;
import betamoon.worldgen.structure.StructureFeature;
import betamoon.worldgen.structure.StructureProcessors;
import betamoon.worldgen.structure.StructureTemplate;
import betamoon.worldgen.structure.StructureTransform;
import betamoon.worldgen.structure.TerrainFootprintPolicy;
import betamoon.worldgen.structure.TerrainMaterialPolicy;
import betamoon.worldgen.structure.TerrainPolicy;
import betamoon.worldgen.structure.TerrainReplacementPolicy;
import betamoon.worldgen.structure.TerracePolicy;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import net.minecraft.src.Block;
import net.minecraft.src.Chunk;
import net.minecraft.src.IChunkProvider;
import net.minecraft.src.IProgressUpdate;
import net.minecraft.src.World;
import net.minecraft.src.WorldProvider;
import net.minecraft.src.TileEntityChest;

/**
 * Verifies shared surface sampling, footprint fitting, foundations, and site
 * eligibility.
 */
public final class StructureTerrainPlacementTest {
    private StructureTerrainPlacementTest() {
    }

    public static void main(String[] arguments) throws Exception {
        verifySitePolicyActivation();
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
        TerrainPolicy foundation = new TerrainPolicy(TerrainPolicy.Mode.FOUNDATION, TerrainSurface.SOLID_SURFACE,
                TerrainPolicy.Anchor.MAXIMUM, 0.5D, 4, 4, 0.0D, 0, Block.cobblestone.blockID, 0, 4, 0, 0, 0, 0, 1, 4);
        StructureFeature supported = feature(pair, foundation);
        FeatureContext foundationContext = context(world, 2L, SitePolicy.ANY);
        PlacementPlan foundationPlan = new PlacementPlan(new BlockPosition(4, 80, 4), 32, 32);
        FeatureResult foundationResult = supported.plan(foundationContext, new BlockPosition(4, 80, 4), foundationPlan);
        require(foundationResult.placed && foundationPlan.size() == 4,
                "Foundation fitting includes structure blocks and bounded support changes in one plan");
        FeatureResult committedFoundation = foundationPlan.commit(foundationContext);
        require(committedFoundation.placed && world.getBlockId(5, 59, 4) == Block.cobblestone.blockID
                && world.getBlockId(5, 61, 4) == Block.stone.blockID
                && ((Number) committedFoundation.details.get("resolvedAnchorY")).intValue() == 61,
                "Foundation placement fills a low support column and keeps the structure rigid");

        world.setBlockAndMetadata(8, 60, 8, Block.dirt.blockID, 0);
        world.setBlockAndMetadata(9, 60, 8, 0, 0);
        world.setBlockAndMetadata(9, 59, 8, 0, 0);
        world.setBlockAndMetadata(9, 58, 8, Block.stone.blockID, 0);
        TerrainPolicy embeddedFoundation = new TerrainPolicy(TerrainPolicy.Mode.FOUNDATION,
                TerrainSurface.SOLID_SURFACE, TerrainPolicy.Anchor.MAXIMUM, 0.5D, 2, 2, 0.0D, -1,
                Block.cobblestone.blockID, 0, 4, 0, 0, 0, 0, 1, 4);
        StructureFeature embedded = feature(pair, embeddedFoundation);
        SitePolicy embeddedLand = new SitePolicy(SitePolicy.Type.LAND_SURFACE, SitePolicy.Scope.SUPPORT_FOOTPRINT,
                SitePolicy.Medium.ANY, 0, 127, 0, 127, 0.0D, 0.0D, 0.0D, 0, 127, null);
        FeatureContext embeddedContext = context(world, 9L, embeddedLand);
        PlacementPlan embeddedPlan = new PlacementPlan(new BlockPosition(8, 80, 8), 32, 32);
        FeatureResult embeddedResult = embedded.plan(embeddedContext, new BlockPosition(8, 80, 8), embeddedPlan);
        require(embeddedResult.placed && embeddedPlan.contains(8, 60, 8) && embeddedPlan.contains(9, 58, 8)
                && embeddedPlan.contains(9, 59, 8) && embeddedPlan.contains(9, 60, 8)
                && ((Number) embeddedContext.diagnostics().get("resolvedAnchorY")).intValue() == 60,
                "Negative foundation offsets embed the floor while low columns still receive support");
        FeatureResult committedEmbedded = embeddedPlan.commit(embeddedContext);
        require(committedEmbedded.placed && world.getBlockId(8, 60, 8) == Block.stone.blockID
                && world.getBlockId(9, 58, 8) == Block.cobblestone.blockID
                && world.getBlockId(9, 59, 8) == Block.cobblestone.blockID
                && world.getBlockId(9, 60, 8) == Block.stone.blockID,
                "Embedded floors replace terrain and shift their generated foundation by the same offset");

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
        FeatureResult slope = strict.plan(context(world, 5L, SitePolicy.ANY), new BlockPosition(2, 80, 8), slopePlan);
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
                new BlockPosition(6, 61, 8),
                new StructureTransform(StructureTransform.Rotation.NONE, StructureTransform.Mirror.NONE),
                SitePolicy.ANY);
        require(regional.accepted && regional.conformOffsets.size() == 2 && regional.bounds.max.y == 62,
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
        verifyNestedAdaptationVariants(world);
        verifyExcavationAndPrecedence(world);
        System.out.println("Structure terrain placement checks passed.");
    }

    private static void verifyNestedAdaptationVariants(TestWorld world) throws Exception {
        TerrainMaterialPolicy cobblestone = TerrainMaterialPolicy.fixed(Block.cobblestone.blockID, 0);
        Map<String, TerrainFootprintPolicy> masks = new LinkedHashMap<String, TerrainFootprintPolicy>();
        masks.put("outer",
                new TerrainFootprintPolicy(TerrainFootprintPolicy.Source.SUPPORT, TerrainFootprintPolicy.Shape.MASK,
                        null, 2, 0, 0, true, Collections.<String>emptySet(), Collections.<String>emptySet(),
                        Collections.<String>emptySet(), Collections.<String>emptySet(), "any", null, null, null));
        for (FoundationPolicy.EdgeType edge : FoundationPolicy.EdgeType.values()) {
            FoundationPolicy policy = new FoundationPolicy(
                    new TerrainFootprintPolicy(TerrainFootprintPolicy.Source.BASE_BOUNDS,
                            TerrainFootprintPolicy.Shape.ROUNDED_BOUNDS, null, 0, 0, 1, false,
                            Collections.<String>emptySet(), Collections.<String>emptySet(),
                            Collections.<String>emptySet(), Collections.<String>emptySet(), "any", null, null, null),
                    edge, 1, edge == FoundationPolicy.EdgeType.HARD ? 0 : 1,
                    edge == FoundationPolicy.EdgeType.AUTHORED ? "outer" : null, cobblestone, cobblestone, cobblestone,
                    6,
                    TerrainReplacementPolicy.Selector.preset(TerrainReplacementPolicy.Replace.TERRAIN_AND_VEGETATION),
                    TerrainReplacementPolicy.Fluid.REJECT, 0, 0.0D, 128, false);
            TerrainPolicy terrain = adaptationTerrain(TerrainPolicy.Mode.FOUNDATION, 2, policy, null, masks);
            PlacementPlan first = new PlacementPlan(new BlockPosition(7, 80, 4), 256, 32);
            PlacementPlan second = new PlacementPlan(new BlockPosition(7, 80, 4), 256, 32);
            FeatureResult firstResult = feature(template(2), terrain).plan(context(world, 80L, SitePolicy.ANY),
                    new BlockPosition(7, 80, 4), first);
            require(firstResult.placed,
                    "Nested foundation edge plans successfully: " + edge + " (" + firstResult.reason + ")");
            require(feature(template(2), terrain).plan(context(world, 80L, SitePolicy.ANY), new BlockPosition(7, 80, 4),
                    second).placed && plannedBlocks(first).equals(plannedBlocks(second)),
                    "Foundation edge is deterministic for a fixed seed: " + edge);
        }

        for (TerracePolicy.TransitionType transition : TerracePolicy.TransitionType.values()) {
            TerracePolicy policy = new TerracePolicy(TerrainFootprintPolicy.SUPPORT, transition,
                    transition == TerracePolicy.TransitionType.HARD ? 0 : 1, 1,
                    transition == TerracePolicy.TransitionType.AUTHORED ? "outer" : null, 3, 3,
                    TerrainMaterialPolicy.SAMPLE_SURFACE, TerrainMaterialPolicy.SAMPLE_SUBSURFACE,
                    TerrainReplacementPolicy.Selector.preset(TerrainReplacementPolicy.Replace.ORDINARY_TERRAIN),
                    TerrainReplacementPolicy.Fluid.REJECT, 128, false);
            TerrainPolicy terrain = adaptationTerrain(TerrainPolicy.Mode.TERRACE, 1, null, policy, masks);
            PlacementPlan plan = new PlacementPlan(new BlockPosition(7, 80, 4), 256, 32);
            require(feature(template(2), terrain).plan(context(world, 90L, SitePolicy.ANY), new BlockPosition(7, 80, 4),
                    plan).placed, "Nested terrace transition plans successfully: " + transition);
        }
    }

    private static TerrainPolicy adaptationTerrain(TerrainPolicy.Mode mode, int verticalOffset,
            FoundationPolicy foundation, TerracePolicy terrace, Map<String, TerrainFootprintPolicy> masks) {
        return new TerrainPolicy(mode, TerrainSurface.SOLID_SURFACE, TerrainPolicy.Anchor.MEDIAN, 0.5D, 4, 4, 0.0D,
                verticalOffset, Block.cobblestone.blockID, 0, 6, 3, 3, 0, 1, 1, 4, foundation, terrace, null, null,
                masks);
    }

    private static String plannedBlocks(PlacementPlan plan) {
        StringBuilder result = new StringBuilder();
        for (PlacementPlan.PlannedBlock block : plan.plannedBlocks()) {
            result.append(block.position).append('=').append(block.blockId).append(':').append(block.metadata)
                    .append(';');
        }
        return result.toString();
    }

    private static void verifyExcavationAndPrecedence(TestWorld world) throws Exception {
        ExcavationPolicy.Volume box = new ExcavationPolicy.Volume(ExcavationPolicy.Shape.BOX, null, null, null,
                new BlockPosition(0, 0, 0), new BlockPosition(0, 2, 0), null, 0, 0, 0, null);
        world.setBlockAndMetadata(2, 60, 12, Block.dirt.blockID, 0);
        world.setBlockAndMetadata(2, 61, 12, Block.dirt.blockID, 0);
        world.setBlockAndMetadata(2, 62, 12, Block.dirt.blockID, 0);
        StructureFeature excavated = feature(template(1),
                exactExcavation(-2, TerrainReplacementPolicy.Fluid.REJECT, box));
        FeatureContext context = context(world, 31L, SitePolicy.ANY);
        PlacementPlan plan = new PlacementPlan(new BlockPosition(2, 62, 12), 64, 16);
        FeatureResult result = excavated.plan(context, new BlockPosition(2, 62, 12), plan);
        require(result.placed && plannedBlock(plan, 2, 60, 12) == Block.stone.blockID
                && plannedBlock(plan, 2, 61, 12) == 0 && plannedBlock(plan, 2, 62, 12) == 0,
                "Exact-mode excavation follows verticalOffset and final structure geometry wins overlaps");
        require(plan.commit(context).placed && world.getBlockId(2, 60, 12) == Block.stone.blockID
                && world.getBlockId(2, 61, 12) == 0 && world.getBlockId(2, 62, 12) == 0,
                "Excavation and structure writes commit through one atomic plan");

        world.setBlockAndMetadata(4, 60, 12, Block.dirt.blockID, 0);
        world.setBlockAndMetadata(4, 61, 12, Block.waterStill.blockID, 0);
        StructureFeature rejectsFluid = feature(template(1),
                exactExcavation(0, TerrainReplacementPolicy.Fluid.REJECT,
                        new ExcavationPolicy.Volume(ExcavationPolicy.Shape.BOX, null, null, null,
                                new BlockPosition(0, 0, 0), new BlockPosition(0, 1, 0), null, 0, 0, 0, null)));
        PlacementPlan rejected = new PlacementPlan(new BlockPosition(4, 60, 12), 32, 8);
        FeatureResult fluidResult = rejectsFluid.plan(context(world, 32L, SitePolicy.ANY), new BlockPosition(4, 60, 12),
                rejected);
        require(!fluidResult.placed && FeatureResult.EXCAVATION_FLUID_COLLISION.equals(fluidResult.reason)
                && world.getBlockId(4, 60, 12) == Block.dirt.blockID,
                "Fluid rejection leaves the world unchanged after earlier planned excavation writes");

        world.setBlockAndMetadata(5, 60, 12, Block.dirt.blockID, 0);
        world.setBlockAndMetadata(5, 61, 12, Block.waterStill.blockID, 0);
        StructureFeature preservesFluid = feature(template(1),
                exactExcavation(0, TerrainReplacementPolicy.Fluid.PRESERVE,
                        new ExcavationPolicy.Volume(ExcavationPolicy.Shape.BOX, null, null, null,
                                new BlockPosition(0, 0, 0), new BlockPosition(0, 1, 0), null, 0, 0, 0, null)));
        FeatureContext preserveContext = context(world, 35L, SitePolicy.ANY);
        PlacementPlan preservePlan = new PlacementPlan(new BlockPosition(5, 60, 12), 32, 8);
        require(preservesFluid.plan(preserveContext, new BlockPosition(5, 60, 12), preservePlan).placed
                && plannedBlock(preservePlan, 5, 61, 12) == -1,
                "Excavation preserve leaves intersected fluid cells out of the mutation plan");
        require(preservePlan.commit(preserveContext).placed && world.getBlockId(5, 61, 12) == Block.waterStill.blockID,
                "Preserved fluids remain in the excavated cavity");

        world.setBlockAndMetadata(6, 60, 12, Block.bedrock.blockID, 0);
        StructureFeature rejectsProtected = feature(template(1),
                exactExcavation(0, TerrainReplacementPolicy.Fluid.DRAIN,
                        new ExcavationPolicy.Volume(ExcavationPolicy.Shape.BOX, null, null, null,
                                new BlockPosition(0, 0, 0), new BlockPosition(0, 0, 0), null, 0, 0, 0, null)));
        FeatureResult protectedResult = rejectsProtected.plan(context(world, 33L, SitePolicy.ANY),
                new BlockPosition(6, 60, 12), new PlacementPlan(new BlockPosition(6, 60, 12), 16, 8));
        require(!protectedResult.placed && FeatureResult.EXCAVATION_PROTECTED_BLOCK.equals(protectedResult.reason),
                "Excavation never removes hard-protected bedrock");

        world.setBlockAndMetadata(7, 60, 12, Block.chest.blockID, 0);
        world.setBlockTileEntity(7, 60, 12, new TileEntityChest());
        StructureFeature rejectsTile = feature(template(1),
                exactExcavation(0, TerrainReplacementPolicy.Fluid.DRAIN,
                        new ExcavationPolicy.Volume(ExcavationPolicy.Shape.BOX, null, null, null,
                                new BlockPosition(0, 0, 0), new BlockPosition(0, 0, 0), null, 0, 0, 0, null)));
        FeatureResult tileResult = rejectsTile.plan(context(world, 36L, SitePolicy.ANY), new BlockPosition(7, 60, 12),
                new PlacementPlan(new BlockPosition(7, 60, 12), 16, 8));
        require(!tileResult.placed && FeatureResult.EXCAVATION_TILE_COLLISION.equals(tileResult.reason),
                "Excavation rejects tile entities even where final structure geometry would win");

        world.setBlockAndMetadata(8, 60, 12, Block.dirt.blockID, 0);
        StructureProcessors decay = new StructureProcessors(false, Collections.<Integer, Integer>emptyMap(), 1.0D, null,
                "reject", "reject");
        StructureFeature decayed = new StructureFeature(template(1), "memory:decay.json", "none", "none", decay,
                exactExcavation(0, TerrainReplacementPolicy.Fluid.REJECT,
                        new ExcavationPolicy.Volume(ExcavationPolicy.Shape.BOX, null, null, null,
                                new BlockPosition(0, 0, 0), new BlockPosition(0, 0, 0), null, 0, 0, 0, null)));
        FeatureContext decayContext = context(world, 34L, SitePolicy.ANY);
        PlacementPlan decayPlan = new PlacementPlan(new BlockPosition(8, 60, 12), 16, 8);
        require(decayed.plan(decayContext, new BlockPosition(8, 60, 12), decayPlan).placed
                && plannedBlock(decayPlan, 8, 60, 12) == 0,
                "A processor-removed structure cell retains the excavation result");

        world.setBlockAndMetadata(11, 60, 12, Block.dirt.blockID, 0);
        ExcavationPolicy.Volume toSurface = new ExcavationPolicy.Volume(ExcavationPolicy.Shape.FOOTPRINT,
                TerrainFootprintPolicy.SUPPORT, new ExcavationPolicy.Bound(false, 0),
                new ExcavationPolicy.Bound(true, 0), null, null, null, 0, 0, 0, null);
        StructureFeature surfaceExcavation = feature(template(1),
                exactExcavation(0, TerrainReplacementPolicy.Fluid.REJECT, toSurface));
        PlacementPlan surfacePlan = new PlacementPlan(new BlockPosition(11, 58, 12), 32, 8);
        require(surfaceExcavation.plan(context(world, 37L, SitePolicy.ANY), new BlockPosition(11, 58, 12),
                surfacePlan).placed && plannedBlock(surfacePlan, 11, 60, 12) == 0,
                "Surface-relative excavation uses the cached pre-mutation top for each footprint column");

        PlacementPlan priority = new PlacementPlan(new BlockPosition(10, 60, 12), 4, 4);
        priority.setBlock(10, 60, 12, 0, 0, PlacementPlan.WritePriority.EXCAVATION);
        priority.setBlock(10, 60, 12, Block.cobblestone.blockID, 0, PlacementPlan.WritePriority.TERRAIN_ADAPTATION);
        priority.setBlock(10, 60, 12, Block.stone.blockID, 0, PlacementPlan.WritePriority.STRUCTURE);
        priority.setBlock(10, 60, 12, 0, 0, PlacementPlan.WritePriority.EXCAVATION);
        require(plannedBlock(priority, 10, 60, 12) == Block.stone.blockID,
                "PlacementPlan enforces excavation below adaptation below structure regardless of call order");

        ExcavationPolicy.Volume asymmetric = new ExcavationPolicy.Volume(ExcavationPolicy.Shape.BOX, null, null, null,
                new BlockPosition(0, 0, 0), new BlockPosition(1, 0, 0), null, 0, 0, 0, null);
        for (StructureTransform.Rotation rotation : StructureTransform.Rotation.values()) {
            for (StructureTransform.Mirror mirror : StructureTransform.Mirror.values()) {
                for (int x = 7; x <= 9; x++) {
                    for (int z = 7; z <= 9; z++) {
                        world.setBlockAndMetadata(x, 50, z, Block.dirt.blockID, 0);
                    }
                }
                StructureTransform transform = new StructureTransform(rotation, mirror);
                StructureFeature transformed = new StructureFeature(template(1), "memory:transform.json",
                        rotation.name().toLowerCase(java.util.Locale.ROOT),
                        mirror.name().toLowerCase(java.util.Locale.ROOT), new StructureProcessors(false,
                                Collections.<Integer, Integer>emptyMap(), 0.0D, null, "reject", "reject"),
                        exactExcavation(0, TerrainReplacementPolicy.Fluid.REJECT, asymmetric));
                PlacementPlan transformedPlan = new PlacementPlan(new BlockPosition(8, 50, 8), 16, 4);
                require(transformed.plan(
                        context(world, 40L + rotation.ordinal() * 3 + mirror.ordinal(), SitePolicy.ANY),
                        new BlockPosition(8, 50, 8), transformedPlan).placed,
                        "Transformed excavation plans successfully");
                BlockPosition expected = transform.apply(1, 0, 0);
                require(plannedBlock(transformedPlan, 8 + expected.x, 50, 8 + expected.z) == 0,
                        "Asymmetric excavation follows " + rotation + "/" + mirror);
            }
        }

        world.setBlockAndMetadata(12, 45, 12, Block.dirt.blockID, 0);
        world.setBlockAndMetadata(13, 45, 12, Block.dirt.blockID, 0);
        Map<String, List<BlockPosition>> masks = new LinkedHashMap<String, List<BlockPosition>>();
        masks.put("tunnel", Arrays.asList(new BlockPosition(0, 0, 0), new BlockPosition(1, 0, 0)));
        TerrainReplacementPolicy.Selector dirtOnly = new TerrainReplacementPolicy.Selector(null,
                new BlockSet(Block.dirt.blockID), null, Collections.<String>emptySet(), Collections.<String>emptySet(),
                false);
        ExcavationPolicy named = new ExcavationPolicy(
                Arrays.asList(new ExcavationPolicy.Volume(ExcavationPolicy.Shape.AUTHORED, null, null, null, null, null,
                        null, 0, 0, 0, "tunnel")),
                dirtOnly, TerrainReplacementPolicy.Fluid.REJECT, 0, 0, 8, masks);
        PlacementPlan namedPlan = new PlacementPlan(new BlockPosition(12, 45, 12), 16, 4);
        require(feature(template(1), exactExcavation(0, named)).plan(context(world, 70L, SitePolicy.ANY),
                new BlockPosition(12, 45, 12), namedPlan).placed
                && plannedBlock(namedPlan, 12, 45, 12) == Block.stone.blockID
                && plannedBlock(namedPlan, 13, 45, 12) == 0,
                "Named authored voxel masks transform into bounded excavation and use compiled block selectors");
    }

    private static TerrainPolicy exactExcavation(int verticalOffset, TerrainReplacementPolicy.Fluid fluids,
            ExcavationPolicy.Volume... volumes) {
        ExcavationPolicy excavation = new ExcavationPolicy(Arrays.asList(volumes),
                TerrainReplacementPolicy.Replace.ORDINARY_TERRAIN, fluids, 0, 0, 128);
        return exactExcavation(verticalOffset, excavation);
    }

    private static TerrainPolicy exactExcavation(int verticalOffset, ExcavationPolicy excavation) {
        return new TerrainPolicy(TerrainPolicy.Mode.EXACT, TerrainSurface.EXACT, TerrainPolicy.Anchor.MEDIAN, 0.5D,
                Integer.MAX_VALUE, Integer.MAX_VALUE, 0.0D, verticalOffset, 0, 0, 0, 0, 0, 0, 0, 1, 0, null, null, null,
                excavation, Collections.<String, TerrainFootprintPolicy>emptyMap());
    }

    private static void verifySitePolicyActivation() {
        require(!SitePolicy.ANY.active(), "The unconstrained any-site policy remains inactive");
        require(new SitePolicy(SitePolicy.Type.ANY, SitePolicy.Scope.ORIGIN, SitePolicy.Medium.ANY, 1, 127, 0, 127,
                0.0D, 1.0D, 0.0D, 0, 127, null).active(), "Depth constraints activate an any-site policy");
        require(new SitePolicy(SitePolicy.Type.ANY, SitePolicy.Scope.SUPPORT_FOOTPRINT, SitePolicy.Medium.ANY, 0, 127,
                0, 127, 0.25D, 1.0D, 0.0D, 0, 127, null).active(),
                "Fluid coverage constraints activate an any-site policy");
        require(new SitePolicy(SitePolicy.Type.ANY, SitePolicy.Scope.CLEARANCE_MASK, SitePolicy.Medium.ANY, 0, 127, 0,
                127, 0.0D, 1.0D, 0.75D, 0, 127, null).active(), "Existing-air constraints activate an any-site policy");
    }

    private static StructureFeature feature(StructureTemplate template, TerrainPolicy terrain) {
        return new StructureFeature(template, "memory:terrain.json", "none", "none", new StructureProcessors(false,
                Collections.<Integer, Integer>emptyMap(), 0.0D, null, "reject", "reject"), terrain);
    }

    private static StructureTemplate template(int width) throws Exception {
        StringBuilder blocks = new StringBuilder();
        for (int x = 0; x < width; x++) {
            if (x > 0) {
                blocks.append(',');
            }
            blocks.append("{\"type\":\"block\",\"pos\":[").append(x).append(",0,0],\"state\":\"stone\"}");
        }
        return StructureTemplate.read(("{\"format\":\"betamoon_structure\","
                + "\"palette\":{\"stone\":{\"block\":\"minecraft:stone\"}},\"elements\":[" + blocks + "]}")
                .getBytes(StandardCharsets.UTF_8));
    }

    private static StructureTemplate conformTemplate() throws Exception {
        return StructureTemplate.read(
                ("{\"format\":\"betamoon_structure\"," + "\"palette\":{\"stone\":{\"block\":\"minecraft:stone\"}},"
                        + "\"elements\":[{\"type\":\"block\",\"pos\":[0,0,0],\"state\":\"stone\"},"
                        + "{\"type\":\"block\",\"pos\":[1,0,0],\"state\":\"stone\"},"
                        + "{\"type\":\"marker\",\"pos\":[0,0,0],\"name\":\"terrain_conform\"},"
                        + "{\"type\":\"marker\",\"pos\":[1,0,0],\"name\":\"terrain_conform\"}]}")
                        .getBytes(StandardCharsets.UTF_8));
    }

    private static FeatureContext context(World world, long seed, SitePolicy site) {
        return new FeatureContext(world, new Random(seed),
                WorldGenKey.parse("test:feature/terrain", WorldGenKind.FEATURE), 32768, null, FeatureOptions.DEFAULT,
                site);
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
                public boolean chunkExists(int x, int z) {
                    return x == 0 && z == 0;
                }

                public Chunk provideChunk(int x, int z) {
                    return chunk;
                }

                public Chunk prepareChunk(int x, int z) {
                    return chunk;
                }

                public void populate(IChunkProvider provider, int x, int z) {
                }

                public boolean saveChunks(boolean force, IProgressUpdate progress) {
                    return true;
                }

                public boolean unload100OldestChunks() {
                    return false;
                }

                public boolean canSave() {
                    return false;
                }

                public String makeString() {
                    return "StructureTerrainTest";
                }
            };
        }

        @Override
        protected IChunkProvider getChunkProvider() {
            return null;
        }

        @Override
        public Chunk getChunkFromChunkCoords(int x, int z) {
            return chunk;
        }

        @Override
        public boolean blockExists(int x, int y, int z) {
            return x >= 0 && x < 16 && z >= 0 && z < 16 && y >= 0 && y < 128;
        }
    }
}
