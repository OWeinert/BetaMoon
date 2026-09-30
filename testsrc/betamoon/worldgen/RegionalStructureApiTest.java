package betamoon.worldgen;

import betamoon.luaapi.BetaMoonModule;
import betamoon.luamodloader.LuaScriptRegistry;
import betamoon.worldgen.structure.StructureFeature;
import betamoon.worldgen.structure.StructureProcessors;
import betamoon.worldgen.structure.StructureTemplate;
import betamoon.worldgen.structure.StructureTransform;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.ArrayList;
import net.minecraft.src.Block;
import net.minecraft.src.IChunkProvider;
import net.minecraft.src.World;
import net.minecraft.src.WorldProvider;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.jse.JsePlatform;

/** Verifies regional distribution, connector planning, persistence, and publication validation. */
public final class RegionalStructureApiTest {
    private RegionalStructureApiTest() {
    }

    public static void main(String[] arguments) throws Exception {
        require(Block.stone != null, "Vanilla blocks are initialized");
        Method owner = LuaScriptRegistry.class.getDeclaredMethod("setCurrentScriptFile", String.class);
        owner.setAccessible(true);
        owner.invoke(null, "regional_structure_test.lua");
        WorldGenRegistry.clear();
        try {
            Globals lua = JsePlatform.standardGlobals();
            new BetaMoonModule().call(LuaValue.NIL, lua);
            StructureFeature start = feature("east");
            StructureFeature corridor = feature("west", "east");
            require(!start.generationSignature().equals(corridor.generationSignature()),
                    "Template signatures distinguish saved regional piece definitions");
            require(Math.abs(new StructureTransform(StructureTransform.Rotation.CLOCKWISE_90,
                    StructureTransform.Mirror.NONE).applyYaw(0.0F) - 90.0F) < 0.001F,
                    "Entity marker yaw follows piece rotation");
            try (WorldGenRegistry.PublicationBatch batch = WorldGenRegistry.beginPublication(
                    "regional_structure_test.lua", "Regional structure test")) {
                WorldGenRegistry.addFeature("test:start", WorldGenKind.STRUCTURE,
                        "local_structure", start, Collections.<WorldGenKey>emptyList(), 16, 8);
                WorldGenRegistry.addFeature("test:corridor", WorldGenKind.STRUCTURE,
                        "local_structure", corridor, Collections.<WorldGenKey>emptyList(), 16, 8);
                lua.load("local s=betamoon.worldgen.structures; "
                        + "local regional=s:addRegional{key='test:ruins',start=s:getRequired('test:start'),"
                        + "dimensions='overworld',spacing=24,separation=6,salt=991,height=64,maxDepth=3,"
                        + "maxPieces=8,maxDistance=64,terminationChance=0,markers={entities=false},"
                        + "pieces={{pool='road',structure=s:getRequired('test:corridor'),weight=1}}}; "
                        + "assert(regional:getKey()=='test:structure/ruins' "
                        + "and s:getRegionalRequired('test:ruins'):getKey()==regional:getKey())").call();
                batch.validate();
                batch.publish();
            }
            require(WorldGenRegistry.regionalStructureSnapshot().size() == 1,
                    "Regional declaration publishes with its local structure dependencies");

            RegionalStructureDefinition definition = definition();
            RegionalStructureRegistry.Candidate first = RegionalStructureRegistry.candidate(definition, 12345L,
                    "minecraft:overworld", -3, 7);
            RegionalStructureRegistry.Candidate second = RegionalStructureRegistry.candidate(definition, 12345L,
                    "minecraft:overworld", -3, 7);
            require(first.chunkX == second.chunkX && first.chunkZ == second.chunkZ && first.seed == second.seed,
                    "Random-spread starts are stable for the same seed and region");
            require(Math.floorDiv(first.chunkX, definition.spacing) == -3
                    && Math.floorDiv(first.chunkZ, definition.spacing) == 7,
                    "Negative and positive regions retain their requested grid cell");

            RegionalStructurePlan plan = RegionalStructureRegistry.plan(new TestWorld(), definition,
                    "minecraft:overworld", -3, 7, first);
            require(plan != null && plan.pieces.size() == 4,
                    "Connector expansion respects the root plus maxDepth bounded chain");
            for (int index = 1; index < plan.pieces.size(); index++) {
                require(!plan.pieces.get(index - 1).bounds.intersects(plan.pieces.get(index).bounds),
                        "Connected pieces do not overlap");
            }
            Map<String, String> forwardCandidates = new LinkedHashMap<String, String>();
            Map<String, String> reverseCandidates = new LinkedHashMap<String, String>();
            for (int iteration = 0; iteration < 256; iteration++) {
                int regionX = iteration - 128;
                int regionZ = 127 - iteration;
                forwardCandidates.put(regionX + "," + regionZ, candidate(definition, regionX, regionZ));
            }
            for (int iteration = 255; iteration >= 0; iteration--) {
                int regionX = iteration - 128;
                int regionZ = 127 - iteration;
                reverseCandidates.put(regionX + "," + regionZ, candidate(definition, regionX, regionZ));
            }
            require(forwardCandidates.equals(reverseCandidates),
                    "Candidate starts remain stable across chunk-order permutations");
            RegionalStructurePlan repeated = RegionalStructureRegistry.plan(new TestWorld(), definition,
                    "minecraft:overworld", -3, 7, first);
            require(samePlan(plan, repeated),
                    "Connector graphs remain stable after differently ordered regional queries");
            int chunkX = Math.floorDiv(plan.pieces.get(0).origin.x, 16);
            int chunkZ = Math.floorDiv(plan.pieces.get(0).origin.z, 16);
            plan.complete(chunkX, chunkZ);
            RegionalStructurePlan restored = RegionalStructurePlan.read(plan.write());
            require(restored != null && restored.pieces.size() == plan.pieces.size()
                    && restored.isComplete(chunkX, chunkZ)
                    && restored.pieces.get(0).generationSignature.equals(plan.pieces.get(0).generationSignature),
                    "Saved piece graphs preserve transforms, seeds, bounds, and completion state");
            RegionalStructurePlan.Piece firstPiece = plan.pieces.get(0);
            java.util.List<RegionalStructurePlan.Piece.TerrainChange> terrain =
                    new ArrayList<RegionalStructurePlan.Piece.TerrainChange>();
            terrain.add(new RegionalStructurePlan.Piece.TerrainChange(firstPiece.origin.offset(0, -1, 0),
                    Block.cobblestone.blockID, 0, 0, 0));
            java.util.List<RegionalStructurePlan.Piece> terrainPieces =
                    new ArrayList<RegionalStructurePlan.Piece>(plan.pieces);
            java.util.List<StructureFeature.ConformOffset> conform =
                    Collections.singletonList(new StructureFeature.ConformOffset(0, 0, 1));
            terrainPieces.set(0, new RegionalStructurePlan.Piece(firstPiece.featureKey, firstPiece.origin,
                    firstPiece.transform, firstPiece.seed, firstPiece.depth, firstPiece.bounds,
                    firstPiece.generationSignature, terrain, conform));
            RegionalStructurePlan terrainPlan = new RegionalStructurePlan(plan.definitionKey, plan.dimension,
                    plan.regionX, plan.regionZ, plan.startChunkX, plan.startChunkZ, plan.seed, terrainPieces,
                    "test-placement-signature");
            RegionalStructurePlan restoredTerrain = RegionalStructurePlan.read(terrainPlan.write());
            require(restoredTerrain != null
                    && restoredTerrain.placementSignature.equals("test-placement-signature")
                    && restoredTerrain.pieces.get(0).terrainChanges.size() == 1
                    && restoredTerrain.pieces.get(0).terrainChanges.get(0).expectedBlockId == 0
                    && restoredTerrain.pieces.get(0).conformOffsets.get(0).y == 1,
                    "Saved regional plans preserve terrain mutations, expected world state, and conform offsets");

            expectFailure(new Runnable() {
                @Override
                public void run() {
                    try (WorldGenRegistry.PublicationBatch batch = WorldGenRegistry.beginPublication(
                            "regional_structure_test.lua", "Regional structure test")) {
                        WorldGenRegistry.addRegionalStructure("test:broken", definition.startFeature,
                                definition.dimensions, 16, 4, 0L, "fixed", 64, 2, 4, 64, 0.0D, false,
                                Collections.singletonList(new RegionalStructureDefinition.PieceChoice("missing",
                                        definition.pieces.get(0).feature, 1)));
                        batch.validate();
                    }
                }
            }, "Unknown connector pools are rejected before publication");
            require(WorldGenRegistry.regionalStructureSnapshot().size() == 1,
                    "Failed validation preserves the active regional snapshot");
            System.out.println("Regional structure API checks passed.");
        } finally {
            WorldGenRegistry.clear();
            owner.invoke(null, new Object[]{null});
        }
    }

    private static RegionalStructureDefinition definition() {
        WorldGenKey start = WorldGenKey.parse("test:start", WorldGenKind.STRUCTURE);
        WorldGenKey corridor = WorldGenKey.parse("test:corridor", WorldGenKind.STRUCTURE);
        return new RegionalStructureDefinition(WorldGenKey.parse("test:ruins", WorldGenKind.STRUCTURE), start,
                "regional_structure_test.lua", "Regional structure test", "test", new LinkedHashSet<String>(
                        Collections.singleton("minecraft:overworld")), 24, 6, 991L, "fixed", 64, 3, 8, 64, 0.0D,
                false, Collections.singletonList(
                        new RegionalStructureDefinition.PieceChoice("road", corridor, 1)));
    }

    private static String candidate(RegionalStructureDefinition definition, int regionX, int regionZ) {
        RegionalStructureRegistry.Candidate value = RegionalStructureRegistry.candidate(definition, 12345L,
                "minecraft:overworld", regionX, regionZ);
        return value.chunkX + "," + value.chunkZ + "," + value.seed;
    }

    private static boolean samePlan(RegionalStructurePlan left, RegionalStructurePlan right) {
        if (left == null || right == null || left.pieces.size() != right.pieces.size()) {
            return false;
        }
        for (int index = 0; index < left.pieces.size(); index++) {
            RegionalStructurePlan.Piece first = left.pieces.get(index);
            RegionalStructurePlan.Piece second = right.pieces.get(index);
            if (!first.featureKey.equals(second.featureKey) || !first.origin.equals(second.origin)
                    || first.transform.rotation != second.transform.rotation
                    || first.transform.mirror != second.transform.mirror || first.seed != second.seed
                    || first.depth != second.depth || !first.bounds.min.equals(second.bounds.min)
                    || !first.bounds.max.equals(second.bounds.max)
                    || !first.generationSignature.equals(second.generationSignature)) {
                return false;
            }
        }
        return true;
    }

    private static StructureFeature feature(String... facings) throws Exception {
        StringBuilder markers = new StringBuilder();
        for (int index = 0; index < facings.length; index++) {
            if (index > 0) {
                markers.append(',');
            }
            markers.append("{\"type\":\"marker\",\"pos\":[0,0,0],\"name\":\"connector\",\"value\":")
                    .append("{\"pool\":\"road\",\"facing\":\"").append(facings[index]).append("\"}}");
        }
        StructureTemplate template = StructureTemplate.read(("{"
                + "\"format\":\"betamoon_structure\","
                + "\"palette\":{\"stone\":{\"block\":\"minecraft:stone\"}},"
                + "\"elements\":[{\"type\":\"block\",\"pos\":[0,0,0],\"state\":\"stone\"}"
                + (markers.length() == 0 ? "" : "," + markers) + "]}"
                ).getBytes(StandardCharsets.UTF_8));
        return new StructureFeature(template, "memory:test.json", "none", "none",
                new StructureProcessors(false, Collections.<Integer, Integer>emptyMap(), 0.0D, null, "reject",
                        "reject"));
    }

    private static void expectFailure(Runnable action, String message) {
        try {
            action.run();
            throw new AssertionError(message);
        } catch (LuaError expected) {
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static final class TestWorld extends World {
        private TestWorld() {
            super(null, "regional_structure_test", new WorldProvider() {
            }, 12345L);
        }

        @Override
        protected IChunkProvider getChunkProvider() {
            return null;
        }
    }
}
