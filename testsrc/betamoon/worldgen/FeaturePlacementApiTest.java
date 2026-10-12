package betamoon.worldgen;

import betamoon.luaapi.BetaMoonModule;
import betamoon.luaapi.utils.LuaCallbackScope;
import betamoon.luaapi.world.LuaWorldActionAccess;
import betamoon.luamodloader.LuaScriptRegistry;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Random;
import net.minecraft.src.BiomeGenBase;
import net.minecraft.src.Block;
import net.minecraft.src.Chunk;
import net.minecraft.src.IChunkProvider;
import net.minecraft.src.IProgressUpdate;
import net.minecraft.src.World;
import net.minecraft.src.WorldProvider;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.jse.JsePlatform;

/** Exercises feature parsing, references, dependency validation, and placement ordering. */
public final class FeaturePlacementApiTest {
    private FeaturePlacementApiTest() {
    }

    public static void main(String[] arguments) throws Exception {
        require(Block.stone != null, "Vanilla blocks are initialized");
        Method owner = LuaScriptRegistry.class.getDeclaredMethod("setCurrentScriptFile", String.class);
        owner.setAccessible(true);
        owner.invoke(null, "feature_placement_test.lua");
        WorldGenRegistry.clear();
        try {
            Globals lua = JsePlatform.standardGlobals();
            new BetaMoonModule().call(LuaValue.NIL, lua);
            try (WorldGenRegistry.PublicationBatch batch = WorldGenRegistry.beginPublication(
                    "feature_placement_test.lua", "Feature placement test")) {
                lua.load("local f=betamoon.worldgen.features; local p=betamoon.worldgen.placements; "
                        + "local ore=f:add{key='test:ore',type='ore_vein',block=14,size=6,replace={1}}; "
                        + "local patch=f:add{key='test:patch',type='block_patch',block=37,radius=3,tries=12}; "
                        + "assert(not pcall(function() f:add{key='test:bad_column',type='column',block=1,"
                        + "height=3,direction='sideways'} end)); "
                        + "assert(not pcall(function() betamoon.worldgen.trees:add{key='test:bad_tree',"
                        + "templates={{structure=ore}}} end)); "
                        + "local sequence=f:add{key='test:mixed',type='sequence',features={ore,patch},"
                        + "maxBlocks=512,maxRadius=32}; "
                        + "column=f:add{key='test:column',type='column',block=1,height=3,replace={0}}; "
                        + "assert(ore:getKey()=='test:feature/ore' and f:get('test:ore'):getKey()==ore:getKey()); "
                        + "local first=p:add{key='test:first',feature=sequence,stage='underground_features',"
                        + "dimensions={'overworld'},attempts={perChunk={min=1,max=2},extraChance=0.25},"
                        + "position={height={type='triangular',min=4,max=40}},conditions={minLight=0,maxLight=15}}; "
                        + "local second=p:add{key='test:second',feature=patch,stage='underground_features',"
                        + "after={first},priority=-2,position={height={type='surface',offset=0}}}; "
                        + "assert(second:getFeature():getKey()==patch:getKey()); "
                        + "assert(p:getRequired('test:second'):getKey()=='test:placement/second'); "
                        + "assert(not pcall(function() f:add{key='test:bad',type='disk',block=1,radius=99,"
                        + "replace={1}} end))").call();
                batch.publish();
            }
            require(WorldGenRegistry.featureSnapshot().size() == 4, "Four reusable features were published");
            require(WorldGenRegistry.placementSnapshot().size() == 2, "Two placements were published");
            require("test:placement/first".equals(WorldGenRegistry.placementSnapshot().get(0).key),
                    "Dependency ordering places the prerequisite first");
            require("test:placement/second".equals(WorldGenRegistry.placementSnapshot().get(1).key),
                    "Dependency ordering places the dependent second");
            TestWorld world = new TestWorld();
            try (LuaCallbackScope scope = new LuaCallbackScope(true)) {
                lua.set("liveWorld", LuaWorldActionAccess.create(scope, world, 0, 64, 0));
                lua.load("local candidate=betamoon.worldgen.placements:getRequired('test:second')"
                        + ":locateCandidate(liveWorld,0,0,0); "
                        + "assert(candidate.x==8 and candidate.z==8 and candidate.chunkX==0 "
                        + "and candidate.chunkZ==0 and candidate.loaded); "
                        + "placed=column:place(liveWorld,0,64,0,{seed=17}); "
                        + "assert(placed.placed and placed.blocksChanged==3 and placed.reason==nil)").call();
            }
            require(world.getBlockId(0, 64, 0) == Block.stone.blockID
                    && world.getBlockId(0, 66, 0) == Block.stone.blockID,
                    "Direct feature placement commits the complete plan");
            require(world.renderUpdates == 3 && world.neighborUpdates == 3 && !world.observedPartialUpdate,
                    "Direct feature placement publishes updates only after committing the complete plan");
            PlacementPlan generationPlan = new PlacementPlan(new BlockPosition(4, 64, 4), 1, 1);
            generationPlan.setBlock(4, 64, 4, Block.stone.blockID, 0);
            FeatureContext generationContext = new FeatureContext(world, new Random(23L),
                    WorldGenKey.parse("test:feature/quiet", WorldGenKind.FEATURE), 256, null);
            require(generationPlan.commit(generationContext).placed && world.renderUpdates == 3
                    && world.neighborUpdates == 3,
                    "World-generation commits remain quiet");

            FeatureContext conditionContext = new FeatureContext(world, new Random(29L),
                    WorldGenKey.parse("test:feature/conditions", WorldGenKind.FEATURE), 256, null);
            BlockPosition conditionOrigin = new BlockPosition(4, 64, 4);
            require(PlacementConditions.any().rejection(conditionContext, conditionOrigin) == null,
                    "An empty biome exclusion set does not reject every placement");
            BiomeGenBase originBiome = world.getWorldChunkManager().getBiomeGenAt(
                    conditionOrigin.x, conditionOrigin.z);
            PlacementConditions excludedBiome = new PlacementConditions(null, false, null, null, null, 0, 15,
                    Collections.<String>emptySet(), Collections.singleton(originBiome.biomeName.toLowerCase(
                            java.util.Locale.ROOT)));
            require(FeatureResult.BLOCKED.equals(excludedBiome.rejection(conditionContext, conditionOrigin)),
                    "An explicitly excluded biome still rejects the placement");

            expectFailure(new Runnable() {
                @Override
                public void run() {
                    try (WorldGenRegistry.PublicationBatch batch = WorldGenRegistry.beginPublication(
                            "feature_placement_test.lua", "Feature placement test")) {
                        lua.load("local f=betamoon.worldgen.features; "
                                + "f:add{key='test:cycle_a',type='sequence',features={'test:cycle_b'}}; "
                                + "f:add{key='test:cycle_b',type='sequence',features={'test:cycle_a'}}").call();
                        batch.publish();
                    }
                }
            }, "Feature dependency cycles are rejected");
            require(WorldGenRegistry.featureSnapshot().size() == 4,
                    "Failed feature publication preserves the active snapshot");
            verifyScheduledPlacementWithEmptyExclusions(lua, world);
            System.out.println("Feature/placement API checks passed.");
        } finally {
            WorldGenRegistry.clear();
            owner.invoke(null, new Object[]{null});
        }
    }

    private static void verifyScheduledPlacementWithEmptyExclusions(Globals lua, TestWorld world) {
        WorldGenRegistry.clear();
        try (WorldGenRegistry.PublicationBatch batch = WorldGenRegistry.beginPublication(
                "feature_placement_test.lua", "Feature placement test")) {
            lua.load("local feature=betamoon.worldgen.features:add{key='test:scheduled_column',type='column',"
                    + "block=1,height=1,replace={0}}; "
                    + "betamoon.worldgen.placements:add{key='test:scheduled_column',feature=feature,attempts=1,"
                    + "position={height={type='fixed',value=80},horizontal='grid',gridSpacing=16}}").call();
            batch.publish();
        }

        WorldGenRegistry.generateSurface(world, new Random(31L), 0, 0);
        WorldGenRegistry.PlacementDescription placement = WorldGenRegistry.placementSnapshot().get(0);
        require(world.getBlockId(8, 80, 8) == Block.stone.blockID,
                "A scheduled placement with no biome exclusions changes the world");
        require(placement.accepted == 1 && placement.rejected == 0 && placement.blocksChanged == 1,
                "Scheduled placement diagnostics record the accepted attempt");
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
        private final Chunk chunk;
        private int renderUpdates;
        private int neighborUpdates;
        private boolean observedPartialUpdate;

        private TestWorld() {
            super(null, "worldgen_feature_test", new WorldProvider() {
            }, 12345L);
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
                    return "WorldgenTest";
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

        @Override
        public void markBlockNeedsUpdate(int x, int y, int z) {
            renderUpdates++;
            observedPartialUpdate |= getBlockId(0, 64, 0) != Block.stone.blockID
                    || getBlockId(0, 65, 0) != Block.stone.blockID
                    || getBlockId(0, 66, 0) != Block.stone.blockID;
        }

        @Override
        public void notifyBlocksOfNeighborChange(int x, int y, int z, int blockId) {
            neighborUpdates++;
        }
    }
}
