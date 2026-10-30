package betamoon.luaapi.client.ui;

import betamoon.gui.component.GuiComponentDefinition;
import betamoon.gui.screen.GuiClientUiRuntime;
import betamoon.gui.screen.GuiScreenActivation;
import betamoon.gui.screen.GuiScreenDefinition;
import betamoon.gui.style.GuiThemeDefinition;
import betamoon.luaapi.utils.LuaCallbackScope;
import betamoon.luaapi.utils.LuaDeclarationValues;
import betamoon.luamodloader.ScriptExecution;
import java.util.Map;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

/**
 * Public facade over independent domain registries; it does not expose a native
 * host factory.
 */
public final class GuiUiApi {
    public static void attach(LuaTable module, String source) {
        LuaTable client = module.get("client").istable() ? module.get("client").checktable() : new LuaTable();
        LuaTable ui = new LuaTable();
        LuaTable components = new LuaTable();
        LuaTable screens = new LuaTable();
        LuaTable themes = new LuaTable();
        themes.set("add", owned(source, args -> {
            LuaCallbackScope.requireUiStateWrite();
            GuiThemeDefinition theme = GuiStyleParser.theme(source, args.arg(args.arg1() == themes ? 2 : 1));
            GuiClientUiRuntime.themes().add(theme);
            LuaTable value = new LuaTable();
            value.set("key", theme.key);
            value.set("base", theme.base);
            return value;
        }));
        for (String method : new String[]{"get", "getRequired"}) {
            themes.set(method, owned(source, args -> {
                String key = LuaDeclarationValues.string(args.arg(args.arg1() == themes ? 2 : 1), "theme key");
                LuaTable value = new LuaTable();
                if (key.equals("menu") || key.equals("minimal") || key.equals("container_legacy")) {
                    value.set("key", key);
                    value.set("base", key);
                    value.set("builtIn", LuaValue.TRUE);
                    return value;
                }
                GuiThemeDefinition theme = method.equals("get")
                        ? GuiClientUiRuntime.themes().get(key)
                        : GuiClientUiRuntime.themes().getRequired(key);
                if (theme == null) {
                    return LuaValue.NIL;
                }
                value.set("key", theme.key);
                value.set("base", theme.base);
                value.set("builtIn", LuaValue.FALSE);
                return value;
            }));
        }
        components.set("add", owned(source, args -> {
            LuaCallbackScope.requireUiStateWrite();
            GuiComponentDefinition definition = GuiComponentParser.parse(source,
                    args.arg(args.arg1() == components ? 2 : 1));
            GuiClientUiRuntime.components().add(definition);
            return component(definition);
        }));
        for (String method : new String[]{"get", "getRequired"}) {
            components.set(method, owned(source, args -> {
                String key = LuaDeclarationValues.string(args.arg(args.arg1() == components ? 2 : 1), "component key");
                GuiComponentDefinition definition = method.equals("get")
                        ? GuiClientUiRuntime.components().get(key)
                        : GuiClientUiRuntime.components().getRequired(key);
                return definition == null ? LuaValue.NIL : component(definition);
            }));
        }
        screens.set("add", owned(source, args -> {
            LuaCallbackScope.requireUiStateWrite();
            GuiScreenDefinition definition = GuiScreenParser.parse(source, args.arg(args.arg1() == screens ? 2 : 1),
                    GuiClientUiRuntime.components());
            GuiClientUiRuntime.screens().add(definition);
            return screen(definition);
        }));
        for (String method : new String[]{"get", "getRequired"}) {
            screens.set(method, owned(source, args -> {
                String key = LuaDeclarationValues.string(args.arg(args.arg1() == screens ? 2 : 1), "screen key");
                GuiScreenDefinition definition = method.equals("get")
                        ? GuiClientUiRuntime.screens().get(key)
                        : GuiClientUiRuntime.screens().getRequired(key);
                return definition == null ? LuaValue.NIL : screen(definition);
            }));
        }
        screens.set("open", owned(source, args -> {
            LuaCallbackScope.requireUiStateWrite();
            int offset = args.arg1() == screens ? 1 : 0;
            String key = LuaDeclarationValues.string(args.arg(offset + 1), "screen key");
            GuiScreenDefinition definition = GuiClientUiRuntime.screens().getRequired(key);
            Map<String, Object>[] options = GuiScreenLuaAccess.options(definition, args.arg(offset + 2));
            return GuiScreenLuaAccess.opened(source, GuiClientUiRuntime.open(source, key, options[0], options[1]),
                    GuiClientUiRuntime.screens()::getRequired);
        }));
        screens.set("current", owned(source, args -> {
            GuiScreenActivation current = GuiClientUiRuntime.current();
            return current == null || !current.owner().equals(source)
                    ? LuaValue.NIL
                    : GuiScreenLuaAccess.instance(source, current, null, GuiClientUiRuntime.screens()::getRequired);
        }));
        ui.set("themes", themes);
        ui.set("components", components);
        ui.set("screens", screens);
        client.set("ui", ui);
        module.set("client", client);
    }

    private static LuaTable component(GuiComponentDefinition definition) {
        LuaTable value = new LuaTable();
        value.set("key", definition.key);
        value.set("label", definition.label);
        return value;
    }

    private static LuaTable screen(GuiScreenDefinition definition) {
        LuaTable value = new LuaTable();
        value.set("key", definition.key);
        value.set("context", definition.context == GuiScreenDefinition.Context.CLIENT ? "client" : "world");
        value.set("pauseGame", LuaValue.valueOf(definition.pauseGame));
        return value;
    }
    private interface Method {
        Varargs invoke(Varargs arguments);
    }
    private static LuaValue owned(String source, Method method) {
        VarArgFunction operation = new VarArgFunction() {
            public Varargs invoke(Varargs arguments) {
                return method.invoke(arguments);
            }
        };
        return new VarArgFunction() {
            public Varargs invoke(Varargs arguments) {
                try {
                    return ScriptExecution.invokeApi(source, operation, arguments);
                } catch (IllegalArgumentException | IllegalStateException invalid) {
                    throw new LuaError(invalid.getMessage());
                }
            }
        };
    }

    private GuiUiApi() {
    }
}
