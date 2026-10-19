package betamoon.dimension;

import betamoon.luaapi.entity.LuaEntityActionAccess;
import betamoon.luaapi.utils.LuaCallbackScope;
import betamoon.luaapi.world.DimensionsApi;
import betamoon.luaapi.world.LuaWorldActionAccess;
import betamoon.luaapi.world.TeleportApi;
import betamoon.luamodloader.LuaScriptRegistry;
import betamoon.luamodloader.ScriptResourceTracker;
import betamoon.teleport.ChunkPreparation;
import betamoon.teleport.TeleportRuntime;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import net.minecraft.client.Minecraft;
import net.minecraft.src.Block;
import net.minecraft.src.EntityPlayerSP;
import net.minecraft.src.GuiScreen;
import net.minecraft.src.ModLoader;
import net.minecraft.src.SaveOldDir;
import net.minecraft.src.Session;
import net.minecraft.src.UnexpectedThrowable;
import net.minecraft.src.World;
import net.minecraft.src.WorldInfo;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.lib.jse.JsePlatform;
import sun.misc.Unsafe;

/**
 * Tests the real Lua entry points and scope boundaries with transformed native
 * storage/player classes.
 */
public final class DimensionLuaApiTest {
    public static void main(String[] arguments) throws Exception {
        require(Block.stone != null, "Initialize native blocks");
        Field initialized = ModLoader.class.getDeclaredField("hasInit");
        initialized.setAccessible(true);
        initialized.setBoolean(null, true);
        DimensionRuntimeTest.declarations();
        Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        TestMinecraft minecraft = (TestMinecraft) ((Unsafe) unsafeField.get(null))
                .allocateInstance(TestMinecraft.class);
        SaveOldDir save = new SaveOldDir(Files.createTempDirectory("betamoon-dimension-lua-").toFile(), "world", false);
        WorldInfo info = new WorldInfo(42, "world");
        info.setSpawn(8, 64, 8);
        save.saveWorldInfo(info);
        World world = new World(save, "world", 42, null);
        DimensionRuntime.activate(world);
        ChunkPreparation preparation = new ChunkPreparation(world, 25);
        preparation.reserve(0, 0, true);
        for (int tick = 0; !preparation.step(); tick++) {
            require(tick < 40, "Fixture preparation must be bounded");
        }
        world.setBlockWithNotify(8, 63, 8, Block.stone.blockID);
        world.setBlockWithNotify(8, 64, 8, 0);
        world.setBlockWithNotify(8, 65, 8, 0);
        minecraft.theWorld = world;
        minecraft.thePlayer = new EntityPlayerSP(minecraft, world, new Session("test", "test"), 0);
        minecraft.thePlayer.setLocationAndAngles(8.5, 64, 8.5, 90, 0);
        world.entityJoinedWorld(minecraft.thePlayer);
        Field client = ModLoader.class.getDeclaredField("instance");
        client.setAccessible(true);
        Object previous = client.get(null);
        Method owner = LuaScriptRegistry.class.getDeclaredMethod("setCurrentScriptFile", String.class);
        owner.setAccessible(true);
        client.set(null, minecraft);
        owner.invoke(null, "dimension_lua_test.lua");
        try {
            verify(minecraft);
            FlatDimensionExampleTest.verify(minecraft, owner);
        } finally {
            ScriptResourceTracker.unload("dimension_lua_test.lua");
            owner.invoke(null, new Object[]{null});
            TeleportRuntime.startingWorldLoad();
            client.set(null, previous);
        }
        System.out.println("Dimension Lua API contracts passed");
    }

    private static void verify(TestMinecraft minecraft) throws Exception {
        Globals lua = JsePlatform.standardGlobals();
        LuaTable module = new LuaTable();
        DimensionsApi.attach(module);
        TeleportApi.attach(module);
        lua.set("betamoon", module);
        lua.load("local d=betamoon.dimensions:getRequired('test:flat'); assert(d:getKey()=='test:dimension/flat');"
                + "local a=d:describe(); a.spawn.x=99; assert(d:describe().spawn.x==0);"
                + "assert(d:describe().environment.beds~=nil); assert(betamoon.dimensions:get('missing:world')==nil);"
                + "assert(not pcall(function() betamoon.dimensions:getRequired('missing:world') end));"
                + "local p=assert(betamoon.teleport:mapCoordinates('overworld','nether',-16.5,72,33));"
                + "assert(p.x==-2.0625 and p.z==4.125 and p.y==72);"
                + "assert(p.dimensionKey=='minecraft:nether' and p.dimension==nil and p.yaw==0 and p.pitch==0);"
                + "assert(not pcall(function() betamoon.teleport:mapCoordinates('overworld','nether',0/0,72,0) end));"
                + "for _,entry in ipairs(betamoon.dimensions:list()) do assert(entry.key and entry.displayName) end")
                .call();
        LuaTable retained;
        try (LuaCallbackScope scope = new LuaCallbackScope(false, true)) {
            retained = LuaEntityActionAccess.create(scope, minecraft.thePlayer, minecraft.thePlayer);
            lua.set("player", retained);
            lua.set("world", LuaWorldActionAccess.create(scope, minecraft.theWorld, 8, 64, 8));
            lua.load("assert(world:getDimensionKey()=='minecraft:overworld');"
                    + "assert(world:getDimensionReference():getKey()==world:getDimensionKey());"
                    + "assert(betamoon.dimensions:getRequired('test:flat'):getState(world).state=='uninitialized');"
                    + "local p=assert(betamoon.teleport:inspectArrival(world,8.5,64,8.5,{type='exact'}));"
                    + "assert(p.x==8.5 and p.y==64);"
                    + "assert(p.dimensionKey=='minecraft:overworld' and p.dimension==nil and p.z==8.5 "
                    + "and p.yaw~=nil and p.pitch~=nil);"
                    + "local p,reason=betamoon.teleport:inspectArrival(world,1008.5,64,8.5,{type='exact'});"
                    + "assert(p==nil and reason=='chunks_unavailable');"
                    + "assert(not pcall(function() world:setBlock(8,64,8,1) end));"
                    + "assert(not pcall(function() player:setPosition(9,64,9) end));"
                    + "assert(not pcall(function() betamoon.teleport:request(player,'test:flat',{bogus=true}) end));"
                    + "assert(not pcall(function() betamoon.teleport:request(player,'test:flat',{onComplete=7}) end));"
                    + "completed=0; ticket=assert(betamoon.teleport:request(player,'test:flat',{onComplete=function(result) "
                    + "completed=completed+1; assert(result.reason=='cancelled'); assert(result.player and result.world);"
                    + "callbackPlayer=result.player end})); assert(ticket:getState()=='queued');"
                    + "local t,r=betamoon.teleport:request(player,'test:flat'); assert(t==nil and r=='busy');"
                    + "assert(ticket:cancel()); assert(ticket:getResult()==nil)").call();
            require(!TeleportRuntime.startingWorldLoad(), "A queued transfer must refuse concurrent world replacement");
        }
        lua.load("assert(not pcall(function() betamoon.teleport:request(player,'test:flat') end))").call();
        TeleportRuntime.tick(minecraft);
        lua.load("assert(completed==1 and ticket:getState()=='cancelled');"
                + "local r=ticket:getResult(); assert(r.reason=='cancelled' and r.player==nil and r.world==nil);"
                + "r.reason='changed'; assert(ticket:getResult().reason=='cancelled');"
                + "assert(not pcall(function() callbackPlayer:getPosition() end))").call();
        TeleportRuntime.tick(minecraft);
        lua.load("assert(completed==1)").call();
        require(!minecraft.theWorld.getIChunkProvider().chunkExists(63, 0),
                "Inspection must not acquire missing terrain");
        require(DimensionQueries.session(minecraft.theWorld).index.entries().isEmpty(),
                "Queries and cancelled queues cannot allocate IDs");
        try (LuaCallbackScope scope = new LuaCallbackScope(false)) {
            lua.set("player", LuaEntityActionAccess.create(scope, minecraft.thePlayer, minecraft.thePlayer));
            lua.load("assert(not pcall(function() betamoon.teleport:request(player,'test:flat') end))").call();
        }
    }

    private static void require(boolean value, String message) {
        if (!value) {
            throw new AssertionError(message);
        }
    }

    private static final class TestMinecraft extends Minecraft {
        private TestMinecraft() {
            super(null, null, null, 320, 240, false);
        }

        public void displayGuiScreen(GuiScreen screen) {
            currentScreen = screen;
        }

        public void displayUnexpectedThrowable(UnexpectedThrowable failure) {
            throw new AssertionError(failure);
        }
    }
}
