package betamoon.luaapi.client.ui;

import betamoon.gui.hud.GuiHudDefinition;
import betamoon.gui.hud.GuiHudDefinition.Context;
import betamoon.gui.hud.GuiHudNativePart;
import betamoon.luaapi.utils.LuaDeclarationValues;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.luaj.vm2.LuaValue;

/**
 * HUD declaration parsing does not inspect the client or acquire native
 * presentation/input.
 */
public final class GuiHudParser {
    public static GuiHudDefinition parse(String owner, LuaValue value) {
        String path = "ui.hud";
        LuaDeclarationValues.fields(value, path, "key", "screen", "priority", "visibility", "safeArea", "interactive",
                "regions", "nativeParts");
        LuaValue visibility = value.get("visibility");
        Set<Context> contexts = EnumSet.of(Context.GAMEPLAY);
        boolean debug = true;
        boolean hideGui = false;
        if (!visibility.isnil()) {
            LuaDeclarationValues.fields(visibility, path + ".visibility", "contexts", "debug", "hideGui");
            if (!visibility.get("contexts").isnil()) {
                contexts.clear();
                LuaValue entries = visibility.get("contexts");
                int length = LuaDeclarationValues.length(entries, path + ".visibility.contexts");
                if (length < 1 || length > Context.values().length) {
                    throw LuaDeclarationValues.error(path + ".visibility.contexts", "expected 1..5 unique contexts");
                }
                for (int index = 1; index <= length; index++) {
                    try {
                        Context context = Context.named(LuaDeclarationValues.string(entries.get(index),
                                path + ".visibility.contexts[" + index + "]"));
                        if (!contexts.add(context)) {
                            throw new IllegalArgumentException("Duplicate HUD visibility context");
                        }
                    } catch (IllegalArgumentException invalid) {
                        throw LuaDeclarationValues.error(path + ".visibility.contexts", invalid.getMessage());
                    }
                }
            }
            debug = LuaDeclarationValues.bool(visibility.get("debug"), path + ".visibility.debug", true);
            hideGui = LuaDeclarationValues.bool(visibility.get("hideGui"), path + ".visibility.hideGui", false);
        }
        Map<GuiHudNativePart, GuiHudDefinition.NativePart> parts = new EnumMap<>(GuiHudNativePart.class);
        if (!value.get("nativeParts").isnil()) {
            LuaValue entries = value.get("nativeParts");
            for (String name : LuaDeclarationValues.keys(entries, path + ".nativeParts")) {
                GuiHudNativePart part = GuiHudNativePart.BY_NAME.get(name);
                if (part == null) {
                    throw LuaDeclarationValues.error(path + ".nativeParts", "unknown native HUD part: " + name);
                }
                String partPath = path + ".nativeParts." + name;
                LuaValue declaration = entries.get(name);
                LuaDeclarationValues.fields(declaration, partPath, "hide", "replace");
                try {
                    parts.put(part,
                            new GuiHudDefinition.NativePart(
                                    LuaDeclarationValues.bool(declaration.get("hide"), partPath + ".hide", false),
                                    optional(declaration.get("replace"), partPath + ".replace")));
                } catch (IllegalArgumentException invalid) {
                    throw LuaDeclarationValues.error(partPath, invalid.getMessage());
                }
            }
        }
        try {
            return new GuiHudDefinition(owner, LuaDeclarationValues.string(value.get("key"), path + ".key"),
                    optional(value.get("screen"), path + ".screen"),
                    value.get("priority").isnil()
                            ? 0
                            : LuaDeclarationValues.integer(value.get("priority"), path + ".priority", -10000, 10000),
                    contexts, debug, hideGui,
                    LuaDeclarationValues.bool(value.get("safeArea"), path + ".safeArea", true),
                    LuaDeclarationValues.bool(value.get("interactive"), path + ".interactive", false),
                    regions(value.get("regions"), path + ".regions"), parts);
        } catch (IllegalArgumentException invalid) {
            throw LuaDeclarationValues.error(path, invalid.getMessage());
        }
    }

    private static Set<String> regions(LuaValue value, String path) {
        if (value.isnil()) {
            return Collections.emptySet();
        }
        int length = LuaDeclarationValues.length(value, path);
        if (length > 32) {
            throw LuaDeclarationValues.error(path, "at most 32 interaction regions");
        }
        Set<String> regions = new LinkedHashSet<>();
        for (int index = 1; index <= length; index++) {
            if (!regions.add(LuaDeclarationValues.string(value.get(index), path + "[" + index + "]"))) {
                throw LuaDeclarationValues.error(path, "duplicate interaction region");
            }
        }
        return regions;
    }

    private static String optional(LuaValue value, String path) {
        return value.isnil() ? null : LuaDeclarationValues.string(value, path);
    }

    private GuiHudParser() {
    }
}
