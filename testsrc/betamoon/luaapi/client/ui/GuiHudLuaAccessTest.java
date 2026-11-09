package betamoon.luaapi.client.ui;

import betamoon.gui.hud.GuiHudActivation;
import betamoon.gui.hud.GuiHudRuntime;
import betamoon.luaapi.utils.LuaCallbackScope;
import betamoon.luamodloader.ScriptExecution;
import betamoon.luamodloader.ScriptResourceTracker;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.lib.jse.JsePlatform;

/**
 * Public registry fields, ownership and inactive claim contracts without a
 * native host.
 */
public final class GuiHudLuaAccessTest {
    public static void run() {
        String owner = "hud-lua-access-test";
        Globals lua = JsePlatform.standardGlobals();
        LuaTable module = new LuaTable();
        GuiUiApi.attach(module, owner);
        lua.set("betamoon", module);
        try {
            lua.load("local ui=betamoon.client.ui; "
                    + "ui.screens:add{key='test:lua-hud-content',root={type='text',text='Status'}}; "
                    + "local info=ui.huds:add{key='test:lua-hud',screen='test:lua-hud-content',"
                    + "nativeParts={crosshair={hide=true}}}; "
                    + "assert(info.priority==0 and not info.interactive and info.screen=='test:lua-hud-content'); "
                    + "assert(ui.huds:get('test:absent')==nil); assert(ui.huds:getRequired(info.key).key==info.key); "
                    + "assert(not pcall(function()ui.huds:getRequired('test:absent')end)); "
                    + "assert(not pcall(function()ui.huds:capabilities().crosshair=true end))").call();
            GuiHudActivation claim = GuiHudRuntime.registry().activate(owner, "test:lua-hud");
            lua.set("claim", GuiHudLuaAccess.activation(owner, claim));
            lua.load("assert(claim:status()=='queued' and claim:instance()==nil and not claim:inputAvailable()); "
                    + "local ok,reason=claim:enterCursorMode(); assert(not ok and reason=='not_ready'); "
                    + "assert(not claim:isCursorMode()); claim:leaveCursorMode(); "
                    + "assert(claim:parts().crosshair.status=='queued'); "
                    + "assert(not pcall(function()claim:parts().crosshair.status='hidden' end)); "
                    + "assert(not pcall(function()claim:instance('targeting') end))").call();
            try (LuaCallbackScope scope = LuaCallbackScope.binding()) {
                lua.load("assert(claim:parts().crosshair.status=='queued'); "
                        + "assert(not pcall(function()claim:enterCursorMode() end)); "
                        + "assert(not pcall(function()claim:close() end))").call();
            }
            try {
                ScriptExecution.withOwner("another-hud-owner", () -> lua.get("claim").get("parts").call());
                throw new AssertionError("Foreign script used an owned HUD claim handle");
            } catch (LuaError expected) {
                // Handles preserve their original owner attribution.
            }
            ScriptResourceTracker.unload(owner);
            lua.load("assert(claim:status()=='closed' and claim:parts().crosshair.status=='closed'); "
                    + "assert(betamoon.client.ui.huds:get('test:lua-hud')==nil)").call();
        } finally {
            ScriptResourceTracker.unload(owner);
        }
    }
}
