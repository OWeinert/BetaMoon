package betamoon.gui.loading;

import betamoon.gui.presentation.GuiPropertyDescriptor;
import betamoon.gui.presentation.GuiPropertyDescriptor.Kind;
import betamoon.gui.style.GuiArgbColor;
import betamoon.luaapi.client.ui.GuiPropertyValues;
import betamoon.luaapi.client.ui.GuiLoadingParser;
import betamoon.luaapi.asset.AssetsApi;
import betamoon.teleport.TeleportOptions;
import betamoon.luamodloader.ScriptResourceTracker;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.Globals;
import org.luaj.vm2.lib.jse.JsePlatform;

/**
 * Headless loading appearance validation, detached canonical metadata and
 * shared color compatibility.
 */
public final class GuiLoadingAppearanceTest {
    private static final String OWNER = "loading-appearance-test";

    public static void main(String[] args) {
        try {
            metadata();
            colors();
            GuiLoadingGeometryTest.run();
            luaBoundary();
        } finally {
            ScriptResourceTracker.unload(OWNER);
        }
        System.out.println("Loading metadata passed: strict cosmetic fields, registered asset identities, canonical "
                + "detachment, owner expiry and unchanged authored UI color normalization.");
    }

    private static void metadata() {
        GuiLoadingAppearance empty = appearance(Collections.emptyMap());
        require(empty.mode == GuiLoadingAppearance.Mode.TILE && empty.tileSize == 32 && empty.background == null
                && empty.tint == 0xFF404040 && empty.titleColor == 0xFFFFFFFF && empty.barColor == 0xFF80FF80,
                "Empty appearance changed native Minecraft loading defaults");
        Map<String, Object> declared = fields("background", "test:loading/moon", "tint", "#80112233", "titleColor",
                "#00FFFFFF", "barColor", 0xFF224466L);
        GuiLoadingAppearance image = appearance(declared);
        declared.put("background", "test:changed");
        require(image.mode == GuiLoadingAppearance.Mode.COVER
                && image.background.getKey().toString().equals("test:loading/moon") && image.tint == 0x80112233
                && image.titleColor == 0x00FFFFFF,
                "Registered loading image identity or exact ARGB values were not detached");
        GuiLoadingAppearance restored = new GuiLoadingAppearance(OWNER, image.declaration());
        require(restored.declaration().equals(image.declaration()), "Canonical loading metadata did not round-trip");
        fail(() -> image.declaration().put("mode", "tile"));
        appearance(fields("background", "test:loading/moon", "mode", "tile", "tileSize", 64));
        appearance(fields("mode", "stretch", "barBackgroundColor", "#102030"));
        for (Map<String, Object> invalid : Arrays.asList(fields("unknown", true), fields("mode", "COVER"),
                fields("background", "images/moon.png"), fields("mode", "cover", "tileSize", 32), fields("tileSize", 7),
                fields("tileSize", 8.5), fields("tileSize", 1025), fields("tint", "white"),
                fields("textColor", Double.NaN), fields("titleColor", 0x100000000L))) {
            fail(() -> appearance(invalid));
        }
        require(image.available(), "Live loading metadata owner was unavailable");
        ScriptResourceTracker.unload(OWNER);
        require(!image.available() && GuiLoadingAppearance.MINECRAFT.available(),
                "Owner expiry retained custom appearance or invalidated the native fallback");
    }

    private static void colors() {
        GuiPropertyDescriptor descriptor = GuiPropertyDescriptor.value("color", Kind.COLOR, 0xFFFFFFFF);
        for (Object value : new Object[]{"#112233", "#00112233", "#80112233", "#FFFFFFFF", 0, -1, Integer.MIN_VALUE,
                0xFFFFFFFFL}) {
            LuaValue lua = value instanceof Number
                    ? LuaValue.valueOf(((Number) value).doubleValue())
                    : LuaValue.valueOf((String) value);
            require((Integer) GuiPropertyValues.read(descriptor, lua) == GuiArgbColor.read(value),
                    "Shared headless/UI color normalization differed for " + value);
        }
        for (Object invalid : new Object[]{"#FFF", "red", "16777215", Double.NaN, Double.POSITIVE_INFINITY, 1.5,
                0x100000000L, (long) Integer.MIN_VALUE - 1}) {
            fail(() -> GuiArgbColor.read(invalid));
            LuaValue lua = invalid instanceof Number
                    ? LuaValue.valueOf(((Number) invalid).doubleValue())
                    : LuaValue.valueOf((String) invalid);
            fail(() -> GuiPropertyValues.read(descriptor, lua));
        }
        fail(() -> GuiPropertyValues.read(descriptor, LuaValue.TRUE));
    }

    private static GuiLoadingAppearance appearance(Map<String, Object> values) {
        return new GuiLoadingAppearance(OWNER, values);
    }

    private static void luaBoundary() {
        Globals lua = JsePlatform.standardGlobals();
        LuaTable api = new LuaTable();
        AssetsApi.attach(api);
        lua.set("betamoon", api);
        LuaValue declaration = lua.load("image=betamoon.assets.textures:getRequired('minecraft:image/gui/background');"
                + "return {loading={background=image,tint='#00112233'}}").call();
        Map<String, Object> detached = GuiLoadingParser.snapshot(declaration, "teleport.options");
        TeleportOptions request = new TeleportOptions(detached, false, OWNER);
        TeleportOptions returning = new TeleportOptions(detached, true, OWNER);
        require(request.loading.background.equals(returning.loading.background)
                && request.loading.background.getKey().toString().equals("minecraft:image/gui/background")
                && request.loading.tint == 0x00112233
                && declaration.get("loading").get("background") == lua.get("image"),
                "Loading texture references must normalize without mutating Lua or changing request/return behavior");
        LuaValue model = lua
                .load("return {loading={background=betamoon.assets.models:getRequired('minecraft:block/full_block')}}")
                .call();
        fail(() -> GuiLoadingParser.snapshot(model, "dimension"));
        fail(() -> GuiLoadingParser.snapshot(lua.load("local v={};v.loading=v; return v").call(), "dimension"));
        fail(() -> GuiLoadingParser.snapshot(lua.load("return {loading={titleColor=function() end}}").call(),
                "dimension"));
        fail(() -> new TeleportOptions(fields("loading", fields("interactive", true)), false, OWNER));
    }

    private static Map<String, Object> fields(Object... pairs) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index < pairs.length; index += 2) {
            result.put((String) pairs[index], pairs[index + 1]);
        }
        return result;
    }

    private static void fail(Runnable operation) {
        try {
            operation.run();
        } catch (RuntimeException expected) {
            return;
        }
        throw new AssertionError("Invalid loading/color operation was accepted");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
