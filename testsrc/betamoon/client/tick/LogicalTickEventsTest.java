package betamoon.client.tick;

import betamoon.BetaMoonEventHandler;
import betamoon.instrumentation.hooks.event.TickEventCallbacks;
import betamoon.luaapi.BetaMoonModule;
import betamoon.luamodloader.LuaScriptRegistry;
import betamoon.luamodloader.ScriptResourceTracker;
import betamoon.system.WorldServiceRuntime;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.minecraft.client.Minecraft;
import net.minecraft.src.GuiScreen;
import net.minecraft.src.IChunkProvider;
import net.minecraft.src.ModLoader;
import net.minecraft.src.UnexpectedThrowable;
import net.minecraft.src.World;
import net.minecraft.src.WorldProvider;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.ZeroArgFunction;
import org.luaj.vm2.lib.jse.JsePlatform;
import sun.misc.Unsafe;

/**
 * Exercises real Lua subscriptions and world services through the native tick
 * bridge callbacks.
 */
public final class LogicalTickEventsTest {
    private static final String OWNER = "logical_tick_events_test.lua";

    public static void main(String[] arguments) throws Exception {
        System.setProperty("betamoon.runtime.side", "unknown");
        Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        TestMinecraft minecraft = (TestMinecraft) ((Unsafe) unsafeField.get(null))
                .allocateInstance(TestMinecraft.class);
        Field instance = ModLoader.class.getDeclaredField("instance");
        instance.setAccessible(true);
        instance.set(null, minecraft);
        Method setOwner = LuaScriptRegistry.class.getDeclaredMethod("setCurrentScriptFile", String.class);
        setOwner.setAccessible(true);
        Globals lua = JsePlatform.standardGlobals();
        new BetaMoonModule().call(LuaValue.NIL, lua);
        lua.set("sourceOwner", new ZeroArgFunction() {
            public LuaValue call() {
                return LuaValue.valueOf(LuaScriptRegistry.getCurrentScriptFile());
            }
        });
        setOwner.invoke(null, OWNER);
        lua.load("games=0; guis=0; worlds=0; services=0; order=''\n"
                + "game=betamoon.events:on('game_tick',function(ctx) games=games+1; order=order..'g'; "
                + "assert(sourceOwner()=='logical_tick_events_test.lua'); menuTick=ctx.worldInfo==nil end)\n"
                + "gui=betamoon.events:on('gui_tick',function(ctx) guis=guis+1; order=order..'u'; assert(ctx.name) end)\n"
                + "world=betamoon.events:on('world_tick',function(ctx) worlds=worlds+1; order=order..'w'; "
                + "assert(ctx.name=='logical_tick_test' and ctx.info) end)\n"
                + "betamoon.systems:add{key='test:system/logical_clock',tick={interval=2},"
                + "onTick=function(ctx) assert(sourceOwner()=='logical_tick_events_test.lua'); "
                + "services=services+1; order=order..'s' end}").call();
        setOwner.invoke(null, new Object[]{null});

        minecraft.currentScreen = new GuiScreen();
        for (int tick = 0; tick < 3; tick++) {
            TickEventCallbacks.begin(minecraft);
            TickEventCallbacks.completed(minecraft);
        }
        counts(lua, 3, 3, 0, 0);
        require(lua.get("menuTick").toboolean(), "Menu game ticks must have no world information");
        require(LuaScriptRegistry.getCurrentScriptFile() == null, "Event dispatch must restore the previous owner");

        TestWorld local = new TestWorld();
        minecraft.theWorld = local;
        minecraft.currentScreen = null;
        local.setWorldTime(6000L);
        lua.set("order", "");
        for (int tick = 0; tick < 4; tick++) {
            TickEventCallbacks.advanceWorld(local);
            TickEventCallbacks.completed(minecraft);
        }
        counts(lua, 7, 3, 4, 2);
        require(lua.get("order").tojstring().equals("wgswgwgswg"),
                "World services and world events must precede completed client events");

        // These calls represent completed client ticks while native pause guards skip
        // world.tick().
        minecraft.currentScreen = new GuiScreen();
        minecraft.isGamePaused = true;
        for (int tick = 0; tick < 5; tick++) {
            TickEventCallbacks.completed(minecraft);
        }
        counts(lua, 12, 8, 4, 2);
        require(local.advances == 4, "Paused client ticks must not manufacture world simulation updates");

        minecraft.isGamePaused = false;
        local.setWorldTime(-1000L);
        for (int tick = 0; tick < 2; tick++) {
            TickEventCallbacks.advanceWorld(local);
            TickEventCallbacks.completed(minecraft);
        }
        counts(lua, 14, 10, 6, 3);

        BetaMoonEventHandler rendered = new BetaMoonEventHandler();
        for (int frame = 0; frame < 40; frame++) {
            rendered.handleGameEvents(minecraft);
            rendered.handleGuiEvents(minecraft, minecraft.currentScreen);
        }
        counts(lua, 14, 10, 6, 3);

        TestWorld remote = new TestWorld();
        remote.multiplayerWorld = true;
        minecraft.theWorld = remote;
        minecraft.isGamePaused = true;
        TickEventCallbacks.advanceWorld(remote);
        TickEventCallbacks.completed(minecraft);
        counts(lua, 15, 11, 7, 3);
        require(remote.advances == 1, "Remote world updates must retain virtual dispatch through the bridge");
        ClientTickEvents.worldAdvanced(minecraft, local);
        counts(lua, 15, 11, 7, 3);

        minecraft.theWorld = null;
        TickEventCallbacks.completed(minecraft);
        counts(lua, 16, 12, 7, 3);
        require(lua.get("menuTick").toboolean(), "Leaving a world must restore menu context");
        lua.load("game:unsubscribe(); gui:unsubscribe(); world:unsubscribe()").call();
        TickEventCallbacks.completed(minecraft);
        counts(lua, 16, 12, 7, 3);
        WorldServiceRuntime.unload(local);
        ScriptResourceTracker.unload(OWNER);
        instance.set(null, null);
        System.out
                .println("Logical ticks passed: menus, paused and non-pausing GUI, remote worlds, render independence,"
                        + " mutable world time, event order, callback ownership and unsubscribe.");
    }

    private static void counts(Globals lua, int game, int gui, int world, int services) {
        require(lua.get("games").toint() == game && lua.get("guis").toint() == gui && lua.get("worlds").toint() == world
                && lua.get("services").toint() == services,
                "Unexpected logical event/service callback counts: " + lua.get("games") + "/" + lua.get("guis") + "/"
                        + lua.get("worlds") + "/" + lua.get("services"));
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static final class TestMinecraft extends Minecraft {
        private TestMinecraft() {
            super(null, null, null, 1, 1, false);
        }

        public void displayUnexpectedThrowable(UnexpectedThrowable error) {
            throw new AssertionError(error);
        }
    }

    private static final class TestWorld extends World {
        private int advances;

        private TestWorld() {
            super(null, "logical_tick_test", new WorldProvider() {
            }, 0L);
        }

        protected IChunkProvider getChunkProvider() {
            return null;
        }

        public void tick() {
            advances++;
        }
    }
}
