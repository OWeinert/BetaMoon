package betamoon.luaapi.client.ui;

import betamoon.gui.framework.GuiGeometry.Rect;
import betamoon.gui.inventory.GuiInventoryProfile;
import betamoon.gui.inventory.GuiInventoryProfile.Position;
import betamoon.gui.inventory.GuiInventorySchema;
import betamoon.luaapi.utils.LuaDeclarationValues;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.luaj.vm2.LuaValue;

/** Inventory metadata parsing never touches a native screen or transaction. */
public final class GuiInventoryParser {
    public static GuiInventoryProfile parse(String owner, LuaValue value) {
        String path = "ui.inventory";
        LuaDeclarationValues.fields(value, path, "key", "screen", "priority", "panel", "groups", "positions",
                "background", "backgroundImage", "slotFrames", "craftingLabel", "preview");
        LuaValue panel = value.get("panel");
        if (!panel.isnil()) {
            LuaDeclarationValues.fields(panel, path + ".panel", "width", "height");
        }
        String background = value.get("background").isnil()
                ? "panel"
                : LuaDeclarationValues.string(value.get("background"), path + ".background");
        try {
            return new GuiInventoryProfile(owner, LuaDeclarationValues.string(value.get("key"), path + ".key"),
                    optional(value.get("screen"), path + ".screen"),
                    integer(value.get("priority"), path + ".priority", -10000, 10000, 0),
                    panel.isnil() ? 0 : integer(panel.get("width"), path + ".panel.width", 36, 4096, 0),
                    panel.isnil() ? 0 : integer(panel.get("height"), path + ".panel.height", 36, 4096, 0),
                    positions(value.get("groups"), path + ".groups", GuiInventorySchema.GROUPS.keySet()),
                    positions(value.get("positions"), path + ".positions", GuiInventorySchema.BY_ID.keySet()),
                    background, optional(value.get("backgroundImage"), path + ".backgroundImage"),
                    LuaDeclarationValues.bool(value.get("slotFrames"), path + ".slotFrames",
                            !background.equals("native")),
                    LuaDeclarationValues.bool(value.get("craftingLabel"), path + ".craftingLabel", true),
                    preview(value.get("preview"), path + ".preview"));
        } catch (IllegalArgumentException invalid) {
            throw LuaDeclarationValues.error(path, invalid.getMessage());
        }
    }

    private static Map<String, Position> positions(LuaValue value, String path, Set<String> allowed) {
        if (value.isnil()) {
            return Collections.emptyMap();
        }
        Map<String, Position> result = new LinkedHashMap<>();
        for (String key : LuaDeclarationValues.keys(value, path)) {
            if (!allowed.contains(key)) {
                throw LuaDeclarationValues.error(path, "unknown semantic inventory position: " + key);
            }
            LuaValue position = value.get(key);
            LuaDeclarationValues.fields(position, path + "." + key, "x", "y");
            result.put(key,
                    new Position(LuaDeclarationValues.integer(position.get("x"), path + "." + key + ".x", 1, 4096),
                            LuaDeclarationValues.integer(position.get("y"), path + "." + key + ".y", 1, 4096)));
        }
        return result;
    }

    private static Rect preview(LuaValue value, String path) {
        if (value.isnil()) {
            return Rect.fromPositionAndSize(21, 15, 60, 60);
        }
        LuaDeclarationValues.fields(value, path, "visible", "x", "y", "width", "height");
        boolean visible = LuaDeclarationValues.bool(value.get("visible"), path + ".visible", true);
        Rect bounds = Rect.fromPositionAndSize(integer(value.get("x"), path + ".x", 0, 4096, 21),
                integer(value.get("y"), path + ".y", 0, 4096, 15),
                integer(value.get("width"), path + ".width", 1, 256, 60),
                integer(value.get("height"), path + ".height", 1, 256, 60));
        return visible ? bounds : Rect.EMPTY;
    }

    private static int integer(LuaValue value, String path, int minimum, int maximum, int fallback) {
        return value.isnil() ? fallback : LuaDeclarationValues.integer(value, path, minimum, maximum);
    }

    private static String optional(LuaValue value, String path) {
        return value.isnil() ? null : LuaDeclarationValues.string(value, path);
    }

    private GuiInventoryParser() {
    }
}
