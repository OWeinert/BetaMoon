package betamoon.luaapi.event;

import betamoon.event.Events;
import betamoon.event.context.BlockEventCtx;
import betamoon.event.context.DimensionEventCtx;
import betamoon.event.context.GameEventCtx;
import betamoon.event.context.GuiEventCtx;
import betamoon.event.context.InputEventCtx;
import betamoon.event.context.ItemUseEventCtx;
import betamoon.event.context.PlayerEventCtx;
import betamoon.event.context.WorldEventCtx;
import betamoon.luaapi.BetaMoonModule;
import betamoon.luamodloader.LuaScriptErrors;
import betamoon.luamodloader.LuaScriptRegistry;
import betamoon.luamodloader.ScriptResourceTracker;
import java.lang.reflect.Method;
import net.minecraft.src.ItemStack;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.jse.JsePlatform;

/**
 * Verifies explicit event bindings, context conversion, errors, and cleanup.
 */
public final class EventsApiTest {
    private EventsApiTest() {
    }

    public static void main(String[] arguments) throws Exception {
        Globals lua = JsePlatform.standardGlobals();
        new BetaMoonModule().call(LuaValue.NIL, lua);
        Method setOwner = LuaScriptRegistry.class.getDeclaredMethod("setCurrentScriptFile", String.class);
        setOwner.setAccessible(true);
        setOwner.invoke(null, "events_test.lua");
        LuaScriptErrors.clear();

        lua.load("calls=0; subscriptions={}; names={"
                + "'world_join','world_leave','player_join','player_leave','gui_opened','gui_closed','gui_tick',"
                + "'game_tick','screen_changed','key_input','mouse_input','block_broken','block_placed','item_used',"
                + "'dimension_changed'}; for _,name in ipairs(names) do subscriptions[name]=betamoon.events:on(name,"
                + "function(ctx) assert(type(ctx)=='table'); calls=calls+1 end) end").call();

        publishEveryEvent();
        require(lua.get("calls").checkint() == 15, "An explicit event binding did not deliver its Lua context");

        lua.load("failing=betamoon.events:on('game_tick', function() error('event failure') end)").call();
        setOwner.invoke(null, new Object[]{null});
        Events.GAME_TICK.publish(new GameEventCtx(null, null));
        require(lua.get("calls").checkint() == 16, "A failing listener prevented another listener from running");
        require(LuaScriptErrors.hasWarningFor(null, "events_test.lua"),
                "An event listener error was not attributed to its declaring script");

        lua.load("subscriptions.world_join:unsubscribe(); assert(not subscriptions.world_join.active)").call();
        Events.WORLD_JOIN.publish(new WorldEventCtx(null, null));
        require(lua.get("calls").checkint() == 16, "Explicit unsubscription left the listener active");

        ScriptResourceTracker.unload("events_test.lua");
        Events.GAME_TICK.publish(new GameEventCtx(null, null));
        require(lua.get("calls").checkint() == 16, "Script cleanup left event listeners active");
        require(!lua.get("failing").get("active").toboolean(), "Cleanup did not update the Lua subscription handle");

        expectLuaError(lua, "return betamoon.events:on('unknown', function() end)");
        expectLuaError(lua, "return betamoon.events:on('game_tick', 42)");
        LuaScriptErrors.clear();
        System.out.println("Event bindings passed: names, typed contexts, errors, unsubscribe, and owner cleanup.");
    }

    private static void publishEveryEvent() {
        Events.WORLD_JOIN.publish(new WorldEventCtx(null, null));
        Events.WORLD_LEAVE.publish(new WorldEventCtx(null, null));
        Events.PLAYER_JOIN.publish(new PlayerEventCtx(null, null, null));
        Events.PLAYER_LEAVE.publish(new PlayerEventCtx(null, null, null));
        Events.GUI_OPENED.publish(new GuiEventCtx(null, null));
        Events.GUI_CLOSED.publish(new GuiEventCtx(null, null));
        Events.GUI_TICK.publish(new GuiEventCtx(null, null));
        Events.GAME_TICK.publish(new GameEventCtx(null, null));
        Events.SCREEN_CHANGED.publish(new GuiEventCtx(null, null, null));
        Events.KEY_INPUT.publish(new InputEventCtx(null, 30, true, 'a'));
        Events.MOUSE_INPUT.publish(new InputEventCtx(null, 0, true, 10, 20));
        Events.BLOCK_BROKEN.publish(new BlockEventCtx(null, null, 1, 2, 3, 4, 1, 0));
        Events.BLOCK_PLACED.publish(new BlockEventCtx(null, null, 1, 2, 3, 4, 1, 0));
        Events.ITEM_USE.publish(new ItemUseEventCtx(null, new ItemStack(1, 1, 0)));
        Events.DIMENSION_CHANGE.publish(new DimensionEventCtx(null, 0, -1));
    }

    private static void expectLuaError(Globals lua, String source) {
        try {
            lua.load(source).call();
        } catch (org.luaj.vm2.LuaError expected) {
            return;
        }
        throw new AssertionError("Expected Lua error from: " + source);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
