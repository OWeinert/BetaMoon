package betamoon.luaapi.client.ui;

import betamoon.gui.hud.GuiNotification;
import betamoon.gui.hud.GuiNotificationDefinition;
import betamoon.gui.hud.GuiNotificationOptions;
import betamoon.gui.hud.GuiNotificationQueue;
import betamoon.gui.hud.GuiNotificationRuntime;
import betamoon.luaapi.utils.LuaCallbackScope;
import betamoon.luamodloader.ScriptExecution;
import betamoon.luamodloader.ScriptResourceTracker;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.jse.JsePlatform;

/**
 * Strict public notification metadata, typed updates, scope and owned-handle
 * lifetime contracts.
 */
public final class GuiNotificationLuaAccessTest {
    private static final String OWNER = "notification-lua-access-test";

    public static void run() {
        Globals lua = JsePlatform.standardGlobals();
        LuaTable module = new LuaTable();
        GuiUiApi.attach(module, OWNER);
        lua.set("betamoon", module);
        try {
            lua.load("local ui=betamoon.client.ui; ui.screens:add{key='test:lua-notice-content',"
                    + "data={message={type='string',default='Initial'},count={type='integer',default=0,min=0,max=5}},"
                    + "root={type='text',text='Notice'}}; "
                    + "local info=ui.notifications:add{key='test:lua-notice',screen='test:lua-notice-content'}; "
                    + "assert(info.width==160 and info.height==40 and info.corner=='top_right' and not info.interactive); "
                    + "assert(ui.notifications:get('test:missing')==nil); "
                    + "assert(ui.notifications:getRequired(info.key).screen=='test:lua-notice-content'); "
                    + "assert(not pcall(function()ui.notifications:getRequired('test:missing') end))").call();
            GuiNotificationDefinition definition = GuiNotificationRuntime.registry().getRequired("test:lua-notice");
            GuiNotificationOptions defaults = GuiNotificationParser.options(definition, LuaValue.NIL, null);
            require(defaults.durationMillis == 5000 && defaults.restartDuration
                    && defaults.data.get("message").equals("Initial"),
                    "Notification defaults bypassed the screen schema");
            for (String fields : new String[]{"unknown=true", "duration='1'", "duration=0.49", "duration=61",
                    "duration=0/0", "corner='center'", "urgency='HIGH'", "lifetime='World'", "dedupKey=''",
                    "data={missing=1}", "data={count=6}", "restartDuration=1"}) {
                try {
                    GuiNotificationParser.options(definition, lua.load("return {" + fields + "}").call(), null);
                    throw new AssertionError("Invalid notification options accepted: " + fields);
                } catch (LuaError expected) {
                    // The complete candidate is rejected before queue/model mutation.
                }
            }
            GuiNotificationQueue queue = new GuiNotificationQueue(() -> 0);
            GuiNotification notification = queue.show(definition, defaults, null, null).notification;
            lua.set("notice", GuiNotificationLuaAccess.handle(OWNER, notification));
            lua.load("assert(notice:status()=='queued' and notice:instance()==nil and not notice:inputAvailable()); "
                    + "local ok,reason=notice:update{data={count=2}}; assert(ok and reason==nil); "
                    + "assert(not pcall(function()notice:update{data={count=6}} end)); "
                    + "assert(not notice:isCursorMode()); notice:leaveCursorMode()").call();
            require(((Number) notification.options().data.get("count")).intValue() == 2
                    && notification.options().data.get("message").equals("Initial")
                    && !notification.options().restartDuration,
                    "Partial update lost typed defaults or restarted duration implicitly");
            try (LuaCallbackScope scope = LuaCallbackScope.binding()) {
                lua.load("assert(notice:status()=='queued'); " + "assert(not pcall(function()notice:update{} end)); "
                        + "assert(not pcall(function()notice:dismiss() end)); "
                        + "assert(not pcall(function()notice:enterCursorMode() end))").call();
                try {
                    GuiNotificationRuntime.show(definition, defaults);
                    throw new AssertionError("Read-only binding invoked notification show before client readiness");
                } catch (RuntimeException expected) {
                    // Readiness does not bypass mutation scope checks.
                }
            }
            try {
                ScriptExecution.withOwner("foreign-notification-owner", () -> lua.get("notice").get("dismiss").call());
                throw new AssertionError("Foreign script dismissed another owner's notification");
            } catch (LuaError expected) {
                // Owner checks happen before API reattribution.
            }
            lua.load("notice:dismiss(); assert(notice:status()=='dismissed'); "
                    + "local ok,reason=notice:update{}; assert(not ok and reason=='inactive')").call();
            ScriptResourceTracker.unload(OWNER);
            lua.load("assert(betamoon.client.ui.notifications:get('test:lua-notice')==nil); "
                    + "local ok,reason=notice:update{}; assert(not ok and reason=='inactive')").call();
            require(queue.size() == 0, "Terminal notification handle retained a queue entry");
        } finally {
            ScriptResourceTracker.unload(OWNER);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
