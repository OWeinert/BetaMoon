package betamoon.dimension;

import betamoon.luaapi.BetaMoonModule;
import betamoon.luaapi.block.BlockCallback;
import betamoon.luaapi.block.BlockCallbackRegistry;
import betamoon.luaapi.block.LuaBlockActionContext;
import betamoon.luaapi.item.ItemCallback;
import betamoon.luaapi.item.ItemCallbackRegistry;
import betamoon.luaapi.item.ItemUseHandler;
import betamoon.luaapi.utils.InteractionOutcome;
import betamoon.luaapi.utils.LuaCallbackScope;
import betamoon.luaapi.utils.LuaDetachedData;
import betamoon.luaapi.entity.LuaEntityActionAccess;
import betamoon.luaapi.world.TeleportApi;
import betamoon.luamodloader.LuaScriptRegistry;
import betamoon.luamodloader.ScriptResourceTracker;
import betamoon.teleport.DimensionRestoreTask;
import betamoon.teleport.TeleportClientAccess;
import betamoon.teleport.TeleportRuntime;
import betamoon.teleport.TeleportService;
import betamoon.teleport.TeleportTicket;
import betamoon.worldgen.BiomeGenRegistry;
import betamoon.worldgen.WorldGenRegistry;
import betamoon.worldgen.spatial.SpatialRegistry;
import betamoon.worldgen.terrain.TerrainRegistry;
import java.io.Reader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.src.Block;
import net.minecraft.src.EntityPlayer;
import net.minecraft.src.EntityPlayerSP;
import net.minecraft.src.Item;
import net.minecraft.src.ItemStack;
import net.minecraft.src.ModLoader;
import net.minecraft.src.NBTTagCompound;
import net.minecraft.src.Session;
import net.minecraft.src.World;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;
import org.luaj.vm2.lib.jse.JsePlatform;

/**
 * Real example registration, item callbacks, generated terrain and persisted
 * return travel.
 */
final class FlatDimensionExampleTest {
    private static final String OWNER = "03_adv_21_flat_dimension.lua";
    private static final DimensionKey FLAT = DimensionKey.parse("example:flat_dimension");

    private FlatDimensionExampleTest() {
    }

    static void verify(Minecraft minecraft, Method setOwner) throws Exception {
        String previous = LuaScriptRegistry.getCurrentScriptFile();
        setOwner.invoke(null, OWNER);
        // RegisterBlock needs the startup-discovered native list, without starting a
        // renderer.
        Field blocks = ModLoader.class.getDeclaredField("field_blockList");
        blocks.setAccessible(true);
        Object previousBlocks = blocks.get(null);
        blocks.set(null, Session.class.getDeclaredField("registeredBlocksList"));
        Client client = new Client(minecraft);
        TeleportRuntime.startingWorldLoad();
        setRuntime("minecraft", minecraft);
        setRuntime("service", new TeleportService(client));
        try {
            load();
            Item item = Item.itemsList[5051];
            require(item != null && item.getMaxDamage() == 0 && !item.getHasSubtypes(),
                    "The real key must have neither durability nor damage variants");
            require(ItemCallbackRegistry.get(5051).use.consume == 0, "The real key cannot consume itself");
            World source = minecraft.theWorld;
            source.setWorldTime(100000);
            source.setBlockWithNotify(8, 64, 10, 247);
            minecraft.thePlayer.inventory.mainInventory[0] = new ItemStack(item, 1, 0);
            minecraft.thePlayer.inventory.currentItem = 0;
            require(use(minecraft, 8, 63, 8) == InteractionOutcome.PASS,
                    "Using the key on ordinary stone must not teleport");
            require(!TeleportRuntime.busy(), "A non-anchor interaction must not enqueue work");
            require(use(minecraft, 8, 64, 10) == InteractionOutcome.HANDLED, "The anchor must queue entry");
            require(minecraft.theWorld == source, "The item callback cannot switch worlds recursively");
            finish(minecraft, client, 1);
            verifyReturnPoint(minecraft);
            require(DimensionIdentity.key(minecraft.theWorld).equals(FLAT) && minecraft.thePlayer.boundingBox.minY == 4,
                    "The player must arrive at the named dimension's exact spawn");
            World flat = minecraft.theWorld;
            verifyCooldown(minecraft);
            require(flat.getBlockId(0, 3, 0) == 247 && flat.getBlockId(0, 4, 0) == 0 && flat.getBlockId(0, 5, 0) == 0,
                    "The generated anchor and headroom must precede arrival");
            for (int x : new int[]{-16, -1, 1, 16}) {
                require(flat.getBlockId(x, 0, 0) == Block.bedrock.blockID
                        && flat.getBlockId(x, 1, 0) == Block.dirt.blockID
                        && flat.getBlockId(x, 2, 0) == Block.dirt.blockID
                        && flat.getBlockId(x, 3, 0) == Block.grass.blockID && flat.getBlockId(x, 4, 0) == 0,
                        "Superflat layers must be identical across chunk boundaries");
            }
            try (LuaBlockActionContext context = new LuaBlockActionContext(flat, 0, 3, 0, minecraft.thePlayer,
                    minecraft.thePlayer.getCurrentEquippedItem(), 1, false)) {
                require(!BlockCallbackRegistry.get(247).callbacks.call(BlockCallback.CAN_BREAK, context, LuaValue.NIL)
                        .toboolean(), "The generated return anchor must be protected from survival mining");
            }
            verifyKey(minecraft);
            DimensionSession session = DimensionQueries.session(flat);
            SavedDimensionInfo saved = SavedDimensionInfo.inspect(session.paths.root());
            DimensionRestoreTask restored = new DimensionRestoreTask(session, saved);
            for (int tick = 0; !restored.step(); tick++) {
                require(tick < 100, "Example reopen must remain bounded");
            }
            EntityPlayerSP player = new EntityPlayerSP(minecraft, restored.world(), new Session("test", "test"), 0);
            player.readFromNBT(saved.player);
            restored.world().entityJoinedWorld(player);
            DimensionRuntime.releaseInactive(flat);
            DimensionRuntime.activate(restored.world());
            minecraft.theWorld = restored.world();
            minecraft.thePlayer = player;
            idle(minecraft);
            require(use(minecraft, 0, 3, 0) == InteractionOutcome.HANDLED,
                    "A saved arrival must retain its return route");
            finish(minecraft, client, 2);
            require(DimensionIdentity.key(minecraft.theWorld).equals(DimensionKey.OVERWORLD)
                    && Math.abs(minecraft.thePlayer.posX - 8.5) < 0.001
                    && Math.abs(minecraft.thePlayer.posZ - 8.5) < 0.001,
                    "Returning after reload must recover the actual departure rather than Overworld spawn");
            verifyKey(minecraft);
            idle(minecraft);
            require(use(minecraft, 8, 64, 10) == InteractionOutcome.HANDLED,
                    "The same key must support repeated trips");
            finish(minecraft, client, 3);
            session.navigation.clear(OWNER, "flat_dimension");
            idle(minecraft);
            require(use(minecraft, 0, 3, 0) == InteractionOutcome.HANDLED,
                    "Missing history must allow an explicit safe Overworld fallback");
            finish(minecraft, client, 4);
            require(DimensionIdentity.key(minecraft.theWorld).equals(DimensionKey.OVERWORLD),
                    "The fallback must reach the real Overworld");
            verifyKey(minecraft);
            System.out.println("Flat Dimension example passed: real declarations, layered terrain, generated anchor, "
                    + "non-consuming undamaged key, scoped item entry, saved return and missing-history fallback.");
        } finally {
            ScriptResourceTracker.unload(OWNER);
            TeleportRuntime.startingWorldLoad();
            blocks.set(null, previousBlocks);
            setOwner.invoke(null, previous);
        }
    }

    private static void load() throws Exception {
        Globals lua = JsePlatform.standardGlobals();
        new BetaMoonModule().call(LuaValue.NIL, lua);
        lua.get("betamoon").get("chat").set("send", new VarArgFunction() {
            public Varargs invoke(Varargs args) {
                return NONE;
            }
        });
        try (Reader reader = Files.newBufferedReader(Paths.get("examples", OWNER), StandardCharsets.UTF_8)) {
            lua.load(reader, OWNER).call();
        }
        try (SpatialRegistry.Batch spatial = SpatialRegistry.begin(OWNER);
                TerrainRegistry.Batch terrain = TerrainRegistry.begin(OWNER);
                DimensionRegistry.Batch dimensions = DimensionRegistry.begin(OWNER);
                WorldGenRegistry.PublicationBatch generation = WorldGenRegistry.beginPublication(OWNER,
                        "Flat Dimension");
                BiomeGenRegistry.PublicationBatch biomes = BiomeGenRegistry.beginPublication(OWNER, "Flat Dimension")) {
            lua.get("modInit").call();
            spatial.validate();
            generation.validate();
            biomes.validate();
            terrain.validate();
            dimensions.validate();
            generation.publish();
            biomes.publish();
            spatial.publish();
            terrain.publish();
            dimensions.publish();
        }
    }

    private static void setRuntime(String name, Object value) throws Exception {
        Field field = TeleportRuntime.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(null, value);
    }

    private static InteractionOutcome use(Minecraft minecraft, int x, int y, int z) {
        return ItemCallbackRegistry.interact(ItemCallback.USE_FIRST, minecraft.thePlayer.getCurrentEquippedItem(),
                minecraft.thePlayer, minecraft.theWorld, x, y, z, 1, null);
    }

    private static void finish(Minecraft minecraft, Client client, int expected) {
        for (int tick = 0; TeleportRuntime.busy(); tick++) {
            require(tick < 250 && client.recovery == null, "Example teleport must finish safely: " + client.recovery);
            TeleportRuntime.tick(minecraft);
        }
        require(client.results.size() == expected
                && Boolean.TRUE.equals(client.results.get(expected - 1).get("success")),
                "Example request failed: " + client.results);
        Globals lua = JsePlatform.standardGlobals();
        lua.set("result", LuaDetachedData.copy(client.results.get(expected - 1)));
        lua.load("for _,p in ipairs({result.source,result.destination}) do "
                + "assert(type(p.dimensionKey)=='string' and p.dimension==nil);"
                + "assert(type(p.x)=='number' and type(p.y)=='number' and type(p.z)=='number');"
                + "assert(type(p.yaw)=='number' and type(p.pitch)=='number') end").call();
    }

    private static void verifyReturnPoint(Minecraft minecraft) {
        Globals lua = JsePlatform.standardGlobals();
        LuaTable module = new LuaTable();
        TeleportApi.attach(module);
        lua.set("betamoon", module);
        try (LuaCallbackScope scope = new LuaCallbackScope(false, true)) {
            lua.set("player", LuaEntityActionAccess.create(scope, minecraft.thePlayer, minecraft.thePlayer));
            lua.load("local p=assert(betamoon.teleport:getReturnPoint(player,'flat_dimension'));"
                    + "assert(p.dimensionKey=='minecraft:overworld' and p.dimension==nil);"
                    + "assert(type(p.id)=='string' and p.channel=='flat_dimension');"
                    + "assert(type(p.x)=='number' and type(p.y)=='number' and type(p.z)=='number');"
                    + "assert(type(p.yaw)=='number' and type(p.pitch)=='number')").call();
        }
    }

    private static void idle(Minecraft minecraft) {
        minecraft.theWorld.setWorldTime(minecraft.theWorld.getWorldTime() + 20);
        for (int tick = 0; tick < 20; tick++) {
            TeleportRuntime.tick(minecraft);
        }
    }

    private static void verifyKey(Minecraft minecraft) {
        ItemStack key = minecraft.thePlayer.getCurrentEquippedItem();
        require(key != null && key.itemID == 5051 && key.stackSize == 1 && key.getItemDamage() == 0,
                "Entry, reload and return must preserve the key without consumption or damage");
    }

    private static void verifyCooldown(Minecraft minecraft) {
        World world = minecraft.theWorld;
        EntityPlayer player = minecraft.thePlayer;
        ItemStack key = player.getCurrentEquippedItem();
        require(ItemUseHandler.suppressDefaultUse(key, world, player),
                "World switching must preserve the remaining cooldown");
        EntityPlayer replacement = new EntityPlayerSP(minecraft, world, new Session("other", "other"), 0);
        replacement.entityId = player.entityId;
        require(!ItemUseHandler.suppressDefaultUse(key, world, replacement),
                "A replacement player with a reused native ID cannot inherit another object's cooldown");
        world.setWorldTime(world.getWorldTime() + 19);
        require(ItemUseHandler.suppressDefaultUse(key, world, player), "Rebasing must not expire a cooldown early");
        world.setWorldTime(world.getWorldTime() + 1);
        require(!ItemUseHandler.suppressDefaultUse(key, world, player),
                "Independent dimension clocks cannot extend the cooldown");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static final class Client implements TeleportClientAccess {
        private final Minecraft minecraft;
        private final List<Map<String, Object>> results = new ArrayList<Map<String, Object>>();
        private String recovery;

        private Client(Minecraft minecraft) {
            this.minecraft = minecraft;
        }

        public World world() {
            return minecraft.theWorld;
        }

        public EntityPlayer player() {
            return minecraft.thePlayer;
        }

        public void beginPreparation(TeleportTicket ticket, String name) {
        }

        public void closeContainer() {
        }

        public void finishPreparation(boolean success) {
        }

        public void switchWorld(World destination, EntityPlayer player, NBTTagCompound target) {
            world().setEntityDead(player);
            player.setWorld(destination);
            player.readFromNBT(target);
            player.isDead = false;
            destination.entityJoinedWorld(player);
            minecraft.theWorld = destination;
        }

        public void publish(Map<String, Object> result) {
            results.add(result);
        }

        public void recovery(String detail) {
            recovery = detail;
        }
    }
}
