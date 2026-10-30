package betamoon.luaapi.client.ui;

import betamoon.gui.style.GuiStyleSheet;
import betamoon.gui.style.GuiStyleValues;
import betamoon.gui.style.GuiThemeDefinition;
import betamoon.gui.presentation.GuiPropertyDescriptor;
import betamoon.gui.presentation.GuiPropertyDescriptor.Kind;
import betamoon.luaapi.utils.LuaDeclarationValues;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.luaj.vm2.LuaValue;

/**
 * Style literals remain separate from resource resolution and the retained
 * control catalog.
 */
public final class GuiStyleParser {
    public static GuiStyleValues values(LuaValue value, String path) {
        if (value.isnil()) {
            return GuiStyleValues.EMPTY;
        }
        LuaDeclarationValues.fields(value, path, "background", "foreground", "border", "accent", "focus", "selection",
                "padding", "borderWidth", "shadow", "nativeButton", "font", "backgroundDrawable", "contentDrawable");
        Map<String, Object> result = new LinkedHashMap<>();
        for (String name : LuaDeclarationValues.keys(value, path)) {
            switch (name) {
                case "background":
                case "foreground":
                case "border":
                case "accent":
                case "focus":
                case "selection":
                    result.put(name, GuiPropertyValues.read(GuiPropertyDescriptor.value(name, Kind.COLOR, 0xFFFFFFFF),
                            value.get(name)));
                    break;
                case "padding":
                case "borderWidth":
                    result.put(name, LuaDeclarationValues.integer(value.get(name), path + "." + name, 0, 256));
                    break;
                case "shadow":
                case "nativeButton":
                    result.put(name, LuaDeclarationValues.bool(value.get(name), path + "." + name, false));
                    break;
                default:
                    result.put(name, LuaDeclarationValues.string(value.get(name), path + "." + name));
                    break;
            }
        }
        try {
            return new GuiStyleValues(result);
        } catch (IllegalArgumentException invalid) {
            throw LuaDeclarationValues.error(path, invalid.getMessage());
        }
    }

    public static GuiThemeDefinition theme(String owner, LuaValue value) {
        String path = "ui.theme";
        LuaDeclarationValues.fields(value, path, "key", "base", "variants", "templates");
        String key = LuaDeclarationValues.string(value.get("key"), path + ".key");
        path += "[" + key + "]";
        Map<String, GuiThemeDefinition.Rule> variants = new LinkedHashMap<>();
        if (!value.get("variants").isnil()) {
            for (String name : LuaDeclarationValues.keys(value.get("variants"), path + ".variants")) {
                if (variants.size() >= 128) {
                    throw LuaDeclarationValues.error(path, "theme exceeds 128 variants");
                }
                String rulePath = path + ".variants." + name;
                LuaValue rule = value.get("variants").get(name);
                LuaDeclarationValues.fields(rule, rulePath, "extends", "values", "states");
                Map<Integer, GuiStyleValues> states = new LinkedHashMap<>();
                if (!rule.get("states").isnil()) {
                    for (String selector : LuaDeclarationValues.keys(rule.get("states"), rulePath + ".states")) {
                        int mask = selector(selector);
                        if (mask == 0 || states.put(mask,
                                values(rule.get("states").get(selector), rulePath + ".states." + selector)) != null) {
                            throw LuaDeclarationValues.error(rulePath,
                                    "interaction selectors require unique nonempty states");
                        }
                    }
                }
                try {
                    variants.put(name,
                            new GuiThemeDefinition.Rule(
                                    rule.get("extends").isnil()
                                            ? null
                                            : LuaDeclarationValues.string(rule.get("extends"), rulePath + ".extends"),
                                    values(rule.get("values"), rulePath + ".values"), states));
                } catch (IllegalArgumentException invalid) {
                    throw LuaDeclarationValues.error(rulePath, invalid.getMessage());
                }
            }
        }
        Map<String, GuiThemeDefinition.Template> templates = new LinkedHashMap<>();
        if (!value.get("templates").isnil()) {
            for (String name : LuaDeclarationValues.keys(value.get("templates"), path + ".templates")) {
                if (templates.size() >= 64) {
                    throw LuaDeclarationValues.error(path, "theme exceeds 64 templates");
                }
                String templatePath = path + ".templates." + name;
                LuaValue template = value.get("templates").get(name);
                LuaDeclarationValues.fields(template, templatePath, "parts", "paint");
                try {
                    templates.put(name, new GuiThemeDefinition.Template(owner, template.get("paint"),
                            GuiComponentParser.strings(template.get("parts"), templatePath + ".parts", 32)));
                } catch (IllegalArgumentException invalid) {
                    throw LuaDeclarationValues.error(templatePath, invalid.getMessage());
                }
            }
        }
        try {
            return new GuiThemeDefinition(key, owner,
                    value.get("base").isnil() ? "menu" : LuaDeclarationValues.string(value.get("base"), path + ".base"),
                    variants, templates);
        } catch (IllegalArgumentException invalid) {
            throw LuaDeclarationValues.error(path, invalid.getMessage());
        }
    }

    /**
     * Canonical bit selectors also name complete prepared template alternatives.
     */
    public static int selector(String name) {
        if (name.equals("default")) {
            return 0;
        }
        int result = 0;
        for (String part : name.split("\\+", -1)) {
            int bit;
            switch (part) {
                case "hovered":
                    bit = GuiStyleSheet.HOVERED;
                    break;
                case "pressed":
                    bit = GuiStyleSheet.PRESSED;
                    break;
                case "focused":
                    bit = GuiStyleSheet.FOCUSED;
                    break;
                case "selected":
                    bit = GuiStyleSheet.SELECTED;
                    break;
                case "invalid":
                    bit = GuiStyleSheet.INVALID;
                    break;
                case "disabled":
                    bit = GuiStyleSheet.DISABLED;
                    break;
                default:
                    throw LuaDeclarationValues.error("style selector", "unknown interaction state: " + part);
            }
            if ((result & bit) != 0) {
                throw LuaDeclarationValues.error("style selector", "duplicate interaction state");
            }
            result |= bit;
        }
        return result;
    }

    private GuiStyleParser() {
    }
}
