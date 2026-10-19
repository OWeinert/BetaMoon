package betamoon.dimension;

import betamoon.assets.model.ModelJson;
import betamoon.instrumentation.api.RequiredNativeHooks;
import betamoon.luaapi.world.DimensionsApi;
import betamoon.luaapi.world.WorldGenApi;
import betamoon.teleport.ArrivalInspector;
import betamoon.teleport.ChunkPreparation;
import betamoon.teleport.TeleportLocation;
import betamoon.teleport.TeleportPlatformDefinition;
import betamoon.teleport.TeleportPlatformPlan;
import betamoon.worldgen.BlockPosition;
import betamoon.worldgen.FeatureContext;
import betamoon.worldgen.PlacementPlan;
import betamoon.worldgen.WorldGenKey;
import betamoon.worldgen.WorldGenRegistry;
import betamoon.worldgen.spatial.SpatialRegistry;
import betamoon.worldgen.structure.StructureProtection;
import betamoon.worldgen.terrain.TerrainRegistry;
import betamoon.worldgen.terrain.WorldGenerationRuntime;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.Random;
import net.minecraft.src.Block;
import net.minecraft.src.Chunk;
import net.minecraft.src.ChunkLoader;
import net.minecraft.src.EntityPlayer;
import net.minecraft.src.Item;
import net.minecraft.src.ItemStack;
import net.minecraft.src.MapData;
import net.minecraft.src.ModLoader;
import net.minecraft.src.NBTTagCompound;
import net.minecraft.src.SaveOldDir;
import net.minecraft.src.World;
import net.minecraft.src.WorldInfo;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.lib.jse.JsePlatform;

/**
 * Exercises actual transformed native constructor/cache boundaries, not mocks
 * of the implementation.
 */
public final class DimensionRuntimeTest {
    public static void main(String[] arguments) throws Exception {
        require(Block.stone != null, "Initialize blocks");
        Field modLoaderInitialized = ModLoader.class.getDeclaredField("hasInit");
        modLoaderInitialized.setAccessible(true);
        modLoaderInitialized.setBoolean(null, true);
        declarations();
        Path directory = Files.createTempDirectory("betamoon-dimension-runtime-");
        SaveOldDir save = new SaveOldDir(directory.toFile(), "world", false);
        WorldInfo info = new WorldInfo(42, "world");
        info.setSpawn(0, 65, 0);
        save.saveWorldInfo(info);
        World nativeWorld = new World(save, "world", 42, null);
        DimensionSession session = DimensionQueries.session(nativeWorld);
        Map<String, Object> state = DimensionQueries.state(nativeWorld, DimensionKey.parse("test:flat"));
        require(state.get("state").equals("uninitialized") && session.index.entries().isEmpty(),
                "State queries cannot initialize or allocate dimensions");
        DimensionQueries.previewContext(nativeWorld, DimensionKey.parse("test:flat"));
        require(session.index.entries().isEmpty(), "Detached preview cannot allocate a dimension ID");
        World custom = DimensionRuntime.create(session, DimensionKey.parse("test:flat"));
        require(custom.worldProvider.worldType == 2
                && DimensionIdentity.key(custom).equals(DimensionKey.parse("test:flat")),
                "Native world construction must select the canonical custom provider");
        DimensionRuntime.binding(custom).save.metadata.bootstrapAnchor(0, 64, 0);
        RequiredNativeHooks.require("Dimension preparation", false, "betamoon:dimensions:chunk_budget",
                "betamoon:dimensions:population_budget");
        require(WorldGenerationRuntime.fieldContext(custom).dimensionKey.equals("test:dimension/flat"),
                "Generation field contexts must carry canonical identity");
        ChunkPreparation task = new ChunkPreparation(custom, 25);
        task.reserve(0, 0, true);
        int ticks = 0;
        int previous = 0;
        while (!task.step()) {
            require(task.acquiredCount() - previous <= 2, "A task tick may acquire at most two chunks");
            previous = task.acquiredCount();
            require(++ticks < 30, "Preparation must complete within its bounded work");
        }
        require(task.acquiredCount() == 25 && custom.getChunkFromChunkCoords(0, 0).isTerrainPopulated,
                "Reserved neighbors must be loaded before actual population completes");
        EntityPlayer player = new EntityPlayer(custom) {
            public void func_6420_o() {
            }
        };
        verifyMaps(custom, nativeWorld, player);
        verifyProtectedAir(custom, player);
        verifyCombinedPlatforms(custom, player);
        TeleportLocation arrival = new TeleportLocation(DimensionKey.parse("test:flat"), 8.5, 64, 8.5, 0, 0);
        require(ArrivalInspector.inspect(custom, player, arrival, "safe") == null, "Validate real generated support");
        custom.setBlockWithNotify(8, 64, 8, Block.stone.blockID);
        require("unsafe_destination".equals(ArrivalInspector.inspect(custom, player, arrival, "collision")),
                "A blocked native bounding box must reject arrival");
        custom.setBlockWithNotify(8, 64, 8, Block.waterStill.blockID);
        require("unsafe_destination".equals(ArrivalInspector.inspect(custom, player, arrival, "safe"))
                && ArrivalInspector.inspect(custom, player, arrival, "collision") == null,
                "Safe/collision modes must differ only in hazard checks");
        TeleportLocation unavailable = new TeleportLocation(DimensionKey.parse("test:flat"), 1008.5, 64, 8.5, 0, 0);
        require("chunks_unavailable".equals(ArrivalInspector.inspect(custom, player, unavailable, "safe")),
                "Inspection must refuse unloaded terrain");
        require(!custom.getIChunkProvider().chunkExists(63, 0), "Inspection must never load terrain");
        try {
            new ChunkPreparation(custom, 25).reserve(32768, 0, true);
            throw new AssertionError("Native packed chunk-key aliases must not be accepted as loaded terrain");
        } catch (ChunkPreparation.WorkFailure expected) {
            require(expected.reason.equals("unsupported_environment"), "Report unsafe native cache aliases explicitly");
        }
        DimensionRuntime.binding(custom).save.metadata.initialize(8.5, 64, 8.5, 0, 0);
        NBTTagCompound savedPlayer = new NBTTagCompound();
        savedPlayer.setInteger("Dimension", 2);
        savedPlayer.setString("BetaMoonDimension", "test:dimension/flat");
        WorldInfo reopenInfo = new WorldInfo(42, "reopen");
        reopenInfo.setPlayerNBTTagCompound(savedPlayer);
        save.saveWorldInfo(reopenInfo);
        World reopened = new World(save, "world", 42, null);
        require(DimensionIdentity.key(reopened).equals(DimensionKey.parse("test:flat")) && reopened
                .getRandomSeed() == DimensionRegistry.binding(DimensionKey.parse("test:flat")).definition.seed(42),
                "Saved custom identity/seed must be selected before requesting the native chunk loader");
        require(!Files.exists(directory.resolve("world/region")), "Custom generation cannot write native terrain");
        DimensionSaveFlushTest.verify();
        DimensionReviewRegressionTest.verify();
        System.out.println("Dimension runtime passed: transformed storage selection/reopen, detached queries, "
                + "two-chunk slices, reserved population and loaded-only native collision/fluid checks.");
    }

    private static void verifyProtectedAir(World world, EntityPlayer player) throws Exception {
        PlacementPlan plan = new PlacementPlan(new BlockPosition(10, 70, 10), 1, 0);
        require(plan.protect(10, 70, 10), "Explicit protected-air tags must fit the feature's bounds");
        FeatureContext context = new FeatureContext(world, new Random(42),
                WorldGenKey.parseFeature("test:feature/protected"), 64, key -> null);
        require(plan.preview(context).placed && !StructureProtection.contains(world, 10, 70, 10),
                "Previewing a protected cell must remain read-only");
        require(plan.commit(context).placed && WorldGenRegistry.reservedStructurePosition(world, 10, 70, 10),
                "Committed explicit protection must reserve air as well as solid cells");
        NBTTagCompound saved = new NBTTagCompound();
        ChunkLoader.storeChunkInCompound(world.getChunkFromChunkCoords(0, 0), world, saved);
        Chunk restored = ChunkLoader.loadChunkIntoWorldFromCompound(world, saved);
        NBTTagCompound written = new NBTTagCompound();
        ChunkLoader.storeChunkInCompound(restored, world, written);
        require(Arrays.equals(saved.getByteArray("BetaMoonProtectedStructureCells"),
                written.getByteArray("BetaMoonProtectedStructureCells")),
                "Native chunk saves must retain protected-air ownership");
        TeleportPlatformDefinition platform = new TeleportPlatformDefinition(
                ModelJson.read("{\"block\":1,\"width\":3,\"y\":70}".getBytes(StandardCharsets.UTF_8)), "platform");
        try {
            TeleportPlatformPlan.plan(world, player,
                    new TeleportLocation(DimensionIdentity.key(world), 10.5, 71, 10.5, 0, 0), platform);
            throw new AssertionError("An automatic platform must not fill a protected local-structure air cell");
        } catch (IOException expected) {
            require(expected.getMessage().startsWith("unsafe_destination"),
                    "Protected cells must explain refused platforms");
        }
        saved.setByteArray("BetaMoonProtectedStructureCells", new byte[4097]);
        boolean refused = false;
        try {
            ChunkLoader.loadChunkIntoWorldFromCompound(world, saved);
        } catch (Error invalid) {
            refused = invalid.getMessage().startsWith("save_failed");
        }
        require(refused, "Corrupt protection must escape native catch-and-regenerate logic");
    }

    private static void verifyMaps(World custom, World nativeWorld, EntityPlayer player) {
        ItemStack stack = new ItemStack(Item.mapItem, 1);
        Item.mapItem.onCreated(stack, custom, player);
        MapData map = Item.mapItem.func_28012_a(stack, custom);
        require(DimensionIdentity.key(custom).equals(DimensionMapState.key(map)),
                "Native map creation must retain canonical custom ownership");
        NBTTagCompound saved = new NBTTagCompound();
        map.writeToNBT(saved);
        require(saved.getString("BetaMoonDimension").equals("test:dimension/flat") && saved.getByte("dimension") == 1,
                "Custom map compatibility bytes must never alias the native Overworld or Nether");
        MapData restored = new MapData("restored");
        restored.readFromNBT(saved);
        require(DimensionMapState.key(restored).equals(DimensionIdentity.key(custom)), "Map ownership must reopen");
        MapData previous = DimensionMapState.enter(restored);
        try {
            int numeric = custom.worldProvider.worldType;
            custom.worldProvider.worldType = 256;
            try {
                require(DimensionMapState.worldComparison(custom.worldProvider) == restored.field_28178_d,
                        "Canonical maps must work beyond the native byte range");
            } finally {
                custom.worldProvider.worldType = numeric;
            }
            require(DimensionMapState.worldComparison(nativeWorld.worldProvider) == Integer.MIN_VALUE,
                    "Custom map terrain cannot update in the Overworld");
            require(DimensionMapState.playerComparison(player) == restored.field_28178_d,
                    "Matching custom player markers must remain visible");
            player.setWorld(nativeWorld);
            try {
                require(DimensionMapState.playerComparison(player) == Integer.MIN_VALUE,
                        "Player markers must be hidden in other dimensions");
            } finally {
                player.setWorld(custom);
            }
        } finally {
            DimensionMapState.exit(previous);
        }
        saved.setShort("width", (short) 129);
        try {
            new MapData("invalid").readFromNBT(saved);
            throw new AssertionError("Malformed map bounds must not reach the unsafe native array loop");
        } catch (IllegalStateException expected) {
            require(expected.getMessage().contains("map dimensions"), "Saved map validation must explain the problem");
        }
    }

    private static void verifyCombinedPlatforms(World world, EntityPlayer player) throws Exception {
        TeleportPlatformDefinition definition = new TeleportPlatformDefinition(
                ModelJson.read("{\"block\":1,\"width\":3,\"y\":100}".getBytes(StandardCharsets.UTF_8)), "platform");
        DimensionKey key = DimensionIdentity.key(world);
        TeleportPlatformPlan first = TeleportPlatformPlan.plan(world, player,
                new TeleportLocation(key, 8.5, 101, 8.5, 0, 0), definition);
        first.apply(world);
        TeleportPlatformPlan next = TeleportPlatformPlan.plan(world, player,
                new TeleportLocation(key, 12.5, 101, 8.5, 0, 0), definition);
        TeleportPlatformPlan combined = first.combine(next);
        next.apply(world);
        TeleportPlatformPlan restored = TeleportPlatformPlan.read(combined.write());
        world.setBlockAndMetadata(8, 100, 8, Block.oreDiamond.blockID, 0);
        restored.rollback(world);
        require(world.getBlockId(8, 100, 8) == Block.oreDiamond.blockID && world.getBlockId(9, 100, 8) == 0
                && world.getBlockId(12, 100, 8) == 0,
                "Combined saved platform undo must restore both plans while preserving intervening edits");
    }

    static void declarations() {
        Globals lua = JsePlatform.standardGlobals();
        LuaTable module = new LuaTable();
        DimensionsApi.attach(module);
        WorldGenApi.attach(module);
        lua.set("betamoon", module);
        try (SpatialRegistry.Batch spatial = SpatialRegistry.begin("dimensions.lua");
                TerrainRegistry.Batch terrain = TerrainRegistry.begin("dimensions.lua");
                DimensionRegistry.Batch dimensions = DimensionRegistry.begin("dimensions.lua")) {
            lua.load("local source=betamoon.worldgen.terrain.sources:add{key='test:flat',type='flat',"
                    + "layers={{block=1,depth=64}}};"
                    + "local profile=betamoon.worldgen.generatorProfiles:add{key='test:flat',"
                    + "dimensions={overworld={terrain=source,surfaces='none',population={native='none'}}}};"
                    + "betamoon.dimensions:add{key='test:flat',generator={type='profile',profile=profile}};"
                    + "local empty=betamoon.worldgen.terrain.sources:add{key='test:empty',type='void'};"
                    + "local void=betamoon.worldgen.generatorProfiles:add{key='test:empty',dimensions={overworld={"
                    + "terrain=empty,surfaces='none',population={native='none'}}}};"
                    + "betamoon.dimensions:add{key='test:empty',generator={type='profile',profile=void}};"
                    + "betamoon.dimensions:add{key='test:local',generator={type='profile',profile=profile},respawn={type='local'}};"
                    + "betamoon.dimensions:add{key='test:return',generator={type='profile',profile=profile},respawn={type='return'}};"
                    + "betamoon.dimensions:add{key='test:cycle_a',generator={type='profile',profile=profile},"
                    + "respawn={type='dimension',dimension='test:cycle_b'}};"
                    + "betamoon.dimensions:add{key='test:cycle_b',generator={type='profile',profile=profile},"
                    + "respawn={type='dimension',dimension='test:cycle_a'}};").call();
            spatial.validate();
            terrain.validate();
            dimensions.validate();
            spatial.publish();
            terrain.publish();
            dimensions.publish();
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
