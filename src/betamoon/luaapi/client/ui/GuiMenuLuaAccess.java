package betamoon.luaapi.client.ui;

import betamoon.gui.menu.GuiMenuActivation;
import betamoon.gui.menu.GuiMenuDefinition;
import betamoon.gui.menu.GuiMenuRuntime;
import betamoon.gui.screen.GuiActionQueue;
import betamoon.gui.screen.GuiClientUiRuntime;
import betamoon.luaapi.utils.LuaCallbackScope;
import betamoon.luaapi.utils.LuaDeclarationValues;
import betamoon.client.tick.ClientTickEvents;
import java.util.function.Function;
import java.util.function.Predicate;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

/**
 * Owned declarations and scoped typed actions; no native host or arbitrary
 * invocation is exposed.
 */
public final class GuiMenuLuaAccess {
    static LuaTable registry(String owner) {
        LuaTable access = new LuaTable();
        access.set("add", GuiUiApi.owned(owner, args -> {
            LuaValue value = args.arg(args.arg1() == access ? 2 : 1);
            String path = "ui.menu";
            LuaDeclarationValues.fields(value, path, "key", "target", "screen", "region", "replace", "priority",
                    "before", "after");
            boolean replace = LuaDeclarationValues.bool(value.get("replace"), path + ".replace", false);
            GuiMenuDefinition definition = new GuiMenuDefinition(owner,
                    LuaDeclarationValues.string(value.get("key"), path + ".key"),
                    LuaDeclarationValues.string(value.get("target"), path + ".target"),
                    LuaDeclarationValues.string(value.get("screen"), path + ".screen"),
                    value.get("region").isnil()
                            ? replace ? "content" : "actions"
                            : LuaDeclarationValues.string(value.get("region"), path + ".region"),
                    replace,
                    value.get("priority").isnil()
                            ? 0
                            : LuaDeclarationValues.integer(value.get("priority"), path + ".priority", -10000, 10000),
                    GuiComponentParser.strings(value.get("before"), path + ".before", 32),
                    GuiComponentParser.strings(value.get("after"), path + ".after", 32));
            if (!GuiClientUiRuntime.screens().getRequired(definition.screen).owner.equals(owner)) {
                throw new IllegalArgumentException("Menu screen belongs to another owner");
            }
            GuiMenuRuntime.registry().add(definition);
            return metadata(definition);
        }));
        for (String method : new String[]{"get", "getRequired"}) {
            access.set(method, GuiUiApi.owned(owner, args -> {
                String key = LuaDeclarationValues.string(args.arg(args.arg1() == access ? 2 : 1), "menu key");
                GuiMenuDefinition definition = method.equals("get")
                        ? GuiMenuRuntime.registry().get(key)
                        : GuiMenuRuntime.registry().getRequired(key);
                return definition == null ? LuaValue.NIL : metadata(definition);
            }));
        }
        access.set("activate", GuiUiApi.owned(owner, args -> {
            ClientTickEvents.requireAvailable();
            String key = LuaDeclarationValues.string(args.arg(args.arg1() == access ? 2 : 1), "menu key");
            GuiMenuActivation activation = GuiMenuRuntime.registry().activate(owner, key);
            return GuiPresentationLuaAccess.activation(owner, activation);
        }));
        return access;
    }

    private static LuaTable metadata(GuiMenuDefinition definition) {
        LuaTable value = new LuaTable();
        value.set("key", definition.key);
        value.set("target", definition.target);
        value.set("screen", definition.screen);
        value.set("region", definition.region);
        value.set("replace", LuaValue.valueOf(definition.replacement));
        value.set("priority", definition.priority);
        return value;
    }

    public static LuaTable context(String owner, LuaCallbackScope scope, String target, String key,
            Predicate<String> enabled, Function<String, GuiActionQueue.Submission> request) {
        LuaTable value = new LuaTable();
        value.set("target", target);
        value.set("key", key);
        value.set("enabled", GuiPresentationLuaAccess.function(owner, scope, false, args -> {
            scope.requireActive();
            return LuaValue.valueOf(enabled
                    .test(LuaDeclarationValues.string(args.arg(args.arg1() == value ? 2 : 1), "native menu action")));
        }));
        value.set("action", GuiPresentationLuaAccess.function(owner, scope, true, args -> {
            scope.requireUiStateMutable();
            GuiActionQueue.Submission result = request
                    .apply(LuaDeclarationValues.string(args.arg(args.arg1() == value ? 2 : 1), "native menu action"));
            return result.ticket == null
                    ? LuaValue.varargsOf(new LuaValue[]{LuaValue.NIL, LuaValue.valueOf(result.reason)})
                    : GuiScreenLuaAccess.ticket(owner, result.ticket);
        }));
        return value;
    }

    private GuiMenuLuaAccess() {
    }
}
