package betamoon.luaapi.client.ui;

import betamoon.client.tick.ClientTickEvents;
import betamoon.gui.hud.GuiNotification;
import betamoon.gui.hud.GuiNotificationDefinition;
import betamoon.gui.hud.GuiNotificationOptions;
import betamoon.gui.hud.GuiNotificationQueue;
import betamoon.gui.hud.GuiNotificationRuntime;
import betamoon.gui.screen.GuiClientUiRuntime;
import betamoon.gui.screen.GuiScreenActivation;
import betamoon.luaapi.utils.LuaDeclarationValues;
import java.util.Locale;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

/**
 * Owned client notifications compose existing registered screens and their
 * typed models.
 */
public final class GuiNotificationLuaAccess {
    static LuaTable registry(String owner) {
        LuaTable access = new LuaTable();
        access.set("add", GuiUiApi.owned(owner, args -> {
            GuiNotificationDefinition definition = GuiNotificationParser.definition(owner,
                    args.arg(args.arg1() == access ? 2 : 1));
            if (!GuiClientUiRuntime.screens().getRequired(definition.screen).owner.equals(owner)) {
                throw new IllegalArgumentException("Notification screen belongs to another owner");
            }
            GuiNotificationRuntime.registry().add(definition);
            return metadata(definition);
        }));
        for (String method : new String[]{"get", "getRequired"}) {
            access.set(method, GuiUiApi.owned(owner, args -> {
                String key = LuaDeclarationValues.string(args.arg(args.arg1() == access ? 2 : 1), "notification key");
                GuiNotificationDefinition definition = method.equals("get")
                        ? GuiNotificationRuntime.registry().get(key)
                        : GuiNotificationRuntime.registry().getRequired(key);
                return definition == null ? LuaValue.NIL : metadata(definition);
            }));
        }
        access.set("show", GuiUiApi.owned(owner, args -> {
            ClientTickEvents.requireAvailable();
            int offset = args.arg1() == access ? 1 : 0;
            GuiNotificationDefinition definition = GuiNotificationRuntime.registry()
                    .getRequired(LuaDeclarationValues.string(args.arg(offset + 1), "notification key"));
            if (!definition.owner.equals(owner)) {
                throw new IllegalArgumentException("Notification template belongs to another owner");
            }
            GuiNotificationOptions options = GuiNotificationParser.options(definition, args.arg(offset + 2), null);
            GuiNotificationQueue.ShowResult result = GuiNotificationRuntime.show(definition, options);
            return result.notification == null ? refused(result.reason) : handle(owner, result.notification);
        }));
        return access;
    }

    private static LuaTable metadata(GuiNotificationDefinition definition) {
        LuaTable value = new LuaTable();
        value.set("key", definition.key);
        value.set("screen", definition.screen);
        value.set("width", definition.width);
        value.set("height", definition.height);
        value.set("corner", definition.corner.name);
        value.set("interactive", LuaValue.valueOf(definition.interactive));
        return value;
    }

    static LuaTable handle(String owner, GuiNotification notification) {
        LuaTable value = new LuaTable();
        value.set("id", LuaValue.valueOf(notification.id));
        value.set("key", notification.definition.key);
        value.set("status", GuiPresentationLuaAccess.function(owner, null, false,
                args -> LuaValue.valueOf(notification.state().name().toLowerCase(Locale.ROOT))));
        value.set("reason", GuiPresentationLuaAccess.function(owner, null, false,
                args -> notification.reason() == null ? LuaValue.NIL : LuaValue.valueOf(notification.reason())));
        value.set("update", GuiPresentationLuaAccess.function(owner, null, true, args -> {
            if (!notification.live()) {
                return booleanResult("inactive");
            }
            GuiNotificationOptions options = GuiNotificationParser.options(notification.definition,
                    args.arg(args.arg1() == value ? 2 : 1), notification.options());
            String reason = notification.updateReason(options);
            return booleanResult(reason);
        }));
        value.set("dismiss", GuiPresentationLuaAccess.function(owner, null, true, args -> {
            notification.dismiss();
            return LuaValue.NIL;
        }));
        value.set("instance", GuiPresentationLuaAccess.function(owner, null, false, args -> {
            GuiScreenActivation instance = notification.instance();
            return instance == null
                    ? LuaValue.NIL
                    : GuiScreenLuaAccess.instance(owner, instance, null, GuiClientUiRuntime.screens()::getRequired);
        }));
        value.set("inputAvailable", GuiPresentationLuaAccess.function(owner, null, false,
                args -> LuaValue.valueOf(notification.inputAvailable())));
        value.set("enterCursorMode", GuiPresentationLuaAccess.function(owner, null, true,
                args -> booleanResult(notification.enterCursorMode())));
        value.set("leaveCursorMode", GuiPresentationLuaAccess.function(owner, null, true, args -> {
            notification.leaveCursorMode();
            return LuaValue.NIL;
        }));
        value.set("isCursorMode", GuiPresentationLuaAccess.function(owner, null, false,
                args -> LuaValue.valueOf(notification.isCursorMode())));
        return value;
    }

    private static Varargs booleanResult(String reason) {
        return reason == null
                ? LuaValue.TRUE
                : LuaValue.varargsOf(new LuaValue[]{LuaValue.FALSE, LuaValue.valueOf(reason)});
    }

    private static Varargs refused(String reason) {
        return LuaValue.varargsOf(new LuaValue[]{LuaValue.NIL, LuaValue.valueOf(reason)});
    }

    private GuiNotificationLuaAccess() {
    }
}
