package betamoon.luaapi.client.ui;

import betamoon.client.tick.ClientTickEvents;
import betamoon.gui.hud.GuiHudActivation;
import betamoon.gui.hud.GuiHudDefinition;
import betamoon.gui.hud.GuiHudNativePart;
import betamoon.gui.hud.GuiHudRuntime;
import betamoon.gui.screen.GuiClientUiRuntime;
import betamoon.gui.screen.GuiScreenActivation;
import betamoon.luaapi.utils.LuaDeclarationValues;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

/**
 * Owned HUD metadata and presentation claims over the existing screen/component
 * system.
 */
public final class GuiHudLuaAccess {
    static LuaTable registry(String owner) {
        LuaTable access = new LuaTable();
        access.set("add", GuiUiApi.owned(owner, args -> {
            GuiHudDefinition definition = GuiHudParser.parse(owner, args.arg(args.arg1() == access ? 2 : 1));
            requireScreen(owner, definition.screen);
            for (GuiHudDefinition.NativePart part : definition.nativeParts.values()) {
                requireScreen(owner, part.replace);
            }
            GuiHudRuntime.registry().add(definition);
            return metadata(definition);
        }));
        for (String method : new String[]{"get", "getRequired"}) {
            access.set(method, GuiUiApi.owned(owner, args -> {
                String key = LuaDeclarationValues.string(args.arg(args.arg1() == access ? 2 : 1), "HUD key");
                GuiHudDefinition definition = method.equals("get")
                        ? GuiHudRuntime.registry().get(key)
                        : GuiHudRuntime.registry().getRequired(key);
                return definition == null ? LuaValue.NIL : metadata(definition);
            }));
        }
        access.set("capabilities", GuiPresentationLuaAccess.function(owner, null, false,
                args -> GuiReadOnlyValues.fromPublished(GuiHudRuntime.capabilities())));
        access.set("partCapabilities", GuiPresentationLuaAccess.function(owner, null, false,
                args -> GuiReadOnlyValues.fromPublished(GuiHudRuntime.partCapabilities())));
        access.set("activate", GuiUiApi.owned(owner, args -> {
            ClientTickEvents.requireAvailable();
            String key = LuaDeclarationValues.string(args.arg(args.arg1() == access ? 2 : 1), "HUD key");
            return activation(owner, GuiHudRuntime.registry().activate(owner, key));
        }));
        return access;
    }

    private static void requireScreen(String owner, String key) {
        if (key != null && !GuiClientUiRuntime.screens().getRequired(key).owner.equals(owner)) {
            throw new IllegalArgumentException("HUD screen belongs to another owner");
        }
    }

    private static LuaTable metadata(GuiHudDefinition definition) {
        LuaTable value = new LuaTable();
        value.set("key", definition.key);
        value.set("priority", definition.priority);
        value.set("interactive", LuaValue.valueOf(definition.interactive));
        if (definition.screen != null) {
            value.set("screen", definition.screen);
        }
        return value;
    }

    static LuaTable activation(String owner, GuiHudActivation activation) {
        LuaTable value = GuiPresentationLuaAccess.activation(owner, activation);
        value.set("instance", GuiPresentationLuaAccess.function(owner, null, false, args -> {
            LuaValue requested = args.arg(args.arg1() == value ? 2 : 1);
            GuiHudNativePart part = null;
            if (!requested.isnil()) {
                part = GuiHudNativePart.BY_NAME.get(LuaDeclarationValues.string(requested, "HUD native part"));
                if (part == null) {
                    throw new IllegalArgumentException("Unknown native HUD part");
                }
            }
            GuiScreenActivation instance = GuiHudRuntime.instance(activation, part);
            return instance == null
                    ? LuaValue.NIL
                    : GuiScreenLuaAccess.instance(owner, instance, null, GuiClientUiRuntime.screens()::getRequired);
        }));
        value.set("parts", GuiPresentationLuaAccess.function(owner, null, false,
                args -> GuiReadOnlyValues.fromPublished(GuiHudRuntime.parts(activation))));
        value.set("inputAvailable", GuiPresentationLuaAccess.function(owner, null, false,
                args -> LuaValue.valueOf(GuiHudRuntime.inputAvailable(activation))));
        value.set("enterCursorMode", GuiPresentationLuaAccess.function(owner, null, true, args -> {
            String reason = GuiHudRuntime.enterCursorMode(activation);
            return reason == null
                    ? LuaValue.TRUE
                    : LuaValue.varargsOf(new LuaValue[]{LuaValue.FALSE, LuaValue.valueOf(reason)});
        }));
        value.set("leaveCursorMode", GuiPresentationLuaAccess.function(owner, null, true, args -> {
            GuiHudRuntime.stopInteraction(activation);
            return LuaValue.NIL;
        }));
        value.set("isCursorMode", GuiPresentationLuaAccess.function(owner, null, false,
                args -> LuaValue.valueOf(GuiHudRuntime.cursorMode(activation))));
        return value;
    }

    private GuiHudLuaAccess() {
    }
}
