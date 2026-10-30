package betamoon.luaapi.client.ui;

import betamoon.gui.component.GuiComponentRegistry;
import betamoon.gui.presentation.GuiCapability;
import betamoon.gui.screen.GuiActionDefinition;
import betamoon.gui.screen.GuiScreenDefinition;
import betamoon.luaapi.utils.LuaDeclarationValues;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.luaj.vm2.LuaValue;

/**
 * Strict declaration parsing; no screen activation or native preparation occurs
 * here.
 */
public final class GuiScreenParser {
    public static GuiScreenDefinition parse(String owner, LuaValue value, GuiComponentRegistry components) {
        String path = "ui.screen";
        LuaDeclarationValues.fields(value, path, "key", "context", "pauseGame", "root", "state", "data", "actions",
                "on", "updateInterval", "theme", "resources", "requires");
        String key = LuaDeclarationValues.string(value.get("key"), path + ".key");
        path += "[" + key + "]";
        GuiScreenDefinition.Context context;
        String contextName = value.get("context").isnil()
                ? "client"
                : LuaDeclarationValues.string(value.get("context"), path + ".context");
        if (contextName.equals("client")) {
            context = GuiScreenDefinition.Context.CLIENT;
        } else if (contextName.equals("world")) {
            context = GuiScreenDefinition.Context.WORLD;
        } else {
            throw LuaDeclarationValues.error(path + ".context", "expected client or world");
        }
        Map<String, GuiActionDefinition> actions = new LinkedHashMap<>();
        if (!value.get("actions").isnil()) {
            for (String name : keys(value.get("actions"), path + ".actions", 32)) {
                String actionPath = path + ".actions." + name;
                LuaValue declaration = value.get("actions").get(name);
                LuaDeclarationValues.fields(declaration, actionPath, "scope", "payload", "handler");
                String scope = LuaDeclarationValues.string(declaration.get("scope"), actionPath + ".scope");
                GuiActionDefinition.Scope kind;
                switch (scope) {
                    case "client":
                        kind = GuiActionDefinition.Scope.CLIENT;
                        break;
                    case "world":
                        kind = GuiActionDefinition.Scope.WORLD;
                        break;
                    case "player_data":
                        kind = GuiActionDefinition.Scope.PLAYER_DATA;
                        break;
                    default:
                        throw LuaDeclarationValues.error(actionPath + ".scope",
                                "expected client, world or player_data");
                }
                try {
                    actions.put(name, new GuiActionDefinition(name, kind,
                            GuiComponentParser.schema(declaration.get("payload"), actionPath + ".payload", false),
                            declaration.get("handler")));
                } catch (IllegalArgumentException invalid) {
                    throw LuaDeclarationValues.error(actionPath, invalid.getMessage());
                }
            }
        }
        Map<String, LuaValue> callbacks = new LinkedHashMap<>();
        if (!value.get("on").isnil()) {
            for (String name : keys(value.get("on"), path + ".on", 4)) {
                callbacks.put(name, value.get("on").get(name));
            }
        }
        EnumSet<GuiCapability> requirements = EnumSet.of(GuiCapability.INPUT);
        for (String name : GuiComponentParser.strings(value.get("requires"), path + ".requires", 32)) {
            try {
                requirements.add(GuiCapability.valueOf(name.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException invalid) {
                throw LuaDeclarationValues.error(path + ".requires", "unknown capability: " + name);
            }
        }
        try {
            return new GuiScreenDefinition(owner, key, context,
                    LuaDeclarationValues.bool(value.get("pauseGame"), path + ".pauseGame", false),
                    value.get("updateInterval").isnil()
                            ? 1
                            : LuaDeclarationValues.integer(value.get("updateInterval"), path + ".updateInterval", 1,
                                    1200),
                    value.get("theme").isnil()
                            ? "menu"
                            : LuaDeclarationValues.string(value.get("theme"), path + ".theme"),
                    new GuiAuthoredNodeParser(components).parse(owner, value.get("root"), path + ".root"),
                    GuiComponentParser.schema(value.get("state"), path + ".state", true),
                    GuiComponentParser.schema(value.get("data"), path + ".data", true), actions, callbacks,
                    new GuiResourceParser().parse(value.get("resources"), path + ".resources"), requirements);
        } catch (IllegalArgumentException invalid) {
            throw LuaDeclarationValues.error(path, invalid.getMessage());
        }
    }

    private static List<String> keys(LuaValue value, String path, int maximum) {
        List<String> keys = LuaDeclarationValues.keys(value, path);
        if (keys.size() > maximum) {
            throw LuaDeclarationValues.error(path, "record exceeds its field budget");
        }
        return keys;
    }

    private GuiScreenParser() {
    }
}
