package betamoon.luaapi.input;

import betamoon.client.control.input.ClientHotkeys;
import betamoon.client.control.input.ClientInputRuntime;
import betamoon.client.control.input.InputDeviceEvent;
import betamoon.client.control.input.InputFamily;
import betamoon.luaapi.BetaMoonModule;
import betamoon.luamodloader.LuaScriptRegistry;
import betamoon.luamodloader.ScriptExecution;
import betamoon.luamodloader.ScriptResourceTracker;
import java.lang.reflect.Field;
import java.util.Map;
import net.minecraft.src.BaseMod;
import net.minecraft.src.KeyBinding;
import net.minecraft.src.ModLoader;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.jse.JsePlatform;
import org.lwjgl.input.Keyboard;

/** Exercises the public named-action API and owner cleanup. */
public final class InputLuaApiTest {
    private static final String OWNER = "input_api_test.lua";

    private InputLuaApiTest() {
    }

    public static void main(String[] args) {
        Globals lua = JsePlatform.standardGlobals();
        try {
            TestMod mod = new TestMod();
            ClientHotkeys.initialize(mod);
            new BetaMoonModule(OWNER).call(LuaValue.NIL, lua);
            String source = "trace=''\n"
                    + "controls=betamoon.input:map{key='mymod:gameplay',priority=10,captures={'movement'},actions={"
                    + "jump={default='key.j'},rotate={default='ctrl+key.r'},zoom='mouse.wheel'}}\n"
                    + "assert(controls.key=='mymod:input_map/gameplay')\n"
                    + "controls:on('jump',function(ctx) trace=trace..'default:'..ctx.phase..','; "
                    + "assert(ctx.map==controls.key and ctx.action=='jump'); "
                    + "return betamoon.callbackResults.handled end)\n"
                    + "controls:on('zoom',function(ctx) trace=trace..'zoom:'..ctx.amount..','; return 'handled' end)\n"
                    + "layer=controls:activate{priority=50,captures={look=true}}\n"
                    + "layer:on('jump',function(ctx) trace=trace..'high:'..ctx.phase..','; return 'pass' end)\n"
                    + "assert(controls:getBindings('jump')[1]=='key.j')\n"
                    + "assert(not pcall(function() controls:rebind('jump','ctrl+key.r') end))\n"
                    + "function checkPressed() local s=controls:getState('jump'); "
                    + "assert(s.pressed and s.held and not s.released and s.amount==1) end\n"
                    + "function checkHeld() local s=controls:getState('jump'); "
                    + "assert(not s.pressed and s.held and not s.released) end\n"
                    + "function checkReleased() local s=controls:getState('jump'); "
                    + "assert(not s.pressed and not s.held and s.released) end\n"
                    + "hotkey=betamoon.input:hotkey{key='mymod:open_menu',label='Open Menu',default='key.g'}\n"
                    + "assert(hotkey.key=='mymod:hotkey/open_menu' and hotkey:getBinding()=='key.g' "
                    + "and hotkey.label=='Open Menu' and not hotkey.repeatWhileHeld)\n"
                    + "hotkey:on(function(event) trace=trace..'hotkey:'..event.binding..','; "
                    + "assert(event.key==hotkey.key and event.keyCode==34) end)\n"
                    + "assert(not pcall(function() betamoon.input:hotkey{key='mymod:bad',label='Bad',"
                    + "default='ctrl+key.g'} end))\n";
            ScriptExecution.invoke(OWNER, lua.load(source, OWNER), LuaValue.NONE);

            LuaHotkey hotkey = (LuaHotkey) lua.get("hotkey");
            requireModLoaderRegistration(mod, hotkey.nativeBinding(), false);
            require(ClientHotkeys.dispatch(hotkey.nativeBinding()), "ModLoader delivery reaches the Lua hotkey");
            require(lua.get("trace").checkjstring().equals("hotkey:key.g,"),
                    "Lua receives native hotkey identity and the current binding");

            Object world = new Object();
            Object player = new Object();
            ClientInputRuntime.router().beginCycle(world, player, null, true);
            require(ClientInputRuntime.router().accept(InputDeviceEvent.key(Keyboard.KEY_J, true)),
                    "Handled Lua actions consume native input");
            lua.get("checkPressed").call();
            require("hotkey:key.g,high:pressed,default:pressed,".equals(lua.get("trace").checkjstring()),
                    "Lua contexts use global priority before default handling");
            require(ClientInputRuntime.router().captures(InputFamily.MOVEMENT)
                    && ClientInputRuntime.router().captures(InputFamily.LOOK),
                    "Lua capture declarations reach native-family routing");

            ClientInputRuntime.router().beginCycle(world, player, null, true);
            lua.get("checkHeld").call();
            ClientInputRuntime.router().accept(InputDeviceEvent.key(Keyboard.KEY_J, false));
            lua.get("checkReleased").call();
            ClientInputRuntime.router().accept(InputDeviceEvent.mouseWheel(-120));
            require(lua.get("trace").checkjstring().endsWith("zoom:-120,"), "Lua receives signed wheel pulses");

            ScriptExecution.invoke(OWNER,
                    lua.load("controls:rebind('jump','key.k'); assert(controls:getBindings('jump')[1]=='key.k'); "
                            + "controls:resetBinding('jump'); assert(controls:getBindings('jump')[1]=='key.j'); "
                            + "layer:close(); assert(not layer:isActive())", OWNER),
                    LuaValue.NONE);
            require(!ClientInputRuntime.router().captures(InputFamily.LOOK),
                    "Closing an explicit Lua context releases its captures");

            ScriptResourceTracker.unload(OWNER);
            require(ClientInputRuntime.router().registrationCount() == 0
                    && ClientInputRuntime.router().contextCount() == 0, "Script unload removes Lua maps and contexts");
            require(ClientHotkeys.activeCount() == 0 && ClientHotkeys.retainedCount() == 1,
                    "Script unload detaches hotkeys while retaining their native Controls slots");
            System.out.println("Input Lua API passed: maps, native hotkeys, callbacks, state, rebinding and cleanup.");
        } finally {
            ScriptResourceTracker.unloadAll();
            LuaScriptRegistry.clear();
            ClientInputRuntime.router().clear();
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void requireModLoaderRegistration(BaseMod mod, KeyBinding binding, boolean repeat) {
        try {
            Field field = ModLoader.class.getDeclaredField("keyList");
            field.setAccessible(true);
            Map<?, ?> registrations = (Map<?, ?>) field.get(null);
            Map<?, ?> keys = (Map<?, ?>) registrations.get(mod);
            boolean[] state = keys == null ? null : (boolean[]) keys.get(binding);
            require(state != null && state[0] == repeat,
                    "The Lua hotkey is wired to ModLoader.RegisterKey with its repeat policy");
        } catch (ReflectiveOperationException error) {
            throw new AssertionError("Could not inspect ModLoader hotkey registration", error);
        }
    }

    private static final class TestMod extends BaseMod {
        @Override
        public String Version() {
            return "test";
        }
    }
}
