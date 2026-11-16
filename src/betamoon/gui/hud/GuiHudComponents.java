package betamoon.gui.hud;

import betamoon.gui.component.GuiComponentRegistry;
import betamoon.luaapi.client.ui.GuiComponentParser;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.jse.JsePlatform;

/**
 * Engine-owned components use the same typed author grammar and compiler as
 * scripts.
 */
public final class GuiHudComponents {
    public static final String OWNER = "<engine-ui>";
    private static final String[] NAMES = {"health", "armor", "breath", "hotbar", "crosshair"};

    public static void install(GuiComponentRegistry registry) {
        boolean missing = false;
        for (String name : NAMES) {
            missing |= registry.get("betamoon:hud/" + name) == null;
        }
        if (!missing) {
            return;
        }
        LuaValue declarations = JsePlatform.standardGlobals().load(source(), "@betamoon/gui/player_components.lua")
                .call();
        for (int index = 1; index <= declarations.length(); index++) {
            LuaValue declaration = declarations.get(index);
            if (registry.get(declaration.get("key").tojstring()) == null) {
                registry.add(GuiComponentParser.parse(OWNER, declaration));
            }
        }
    }

    private static String source() {
        try (InputStream stream = sourceStream()) {
            if (stream == null) {
                throw new IllegalStateException("Bundled player HUD components are missing");
            }
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int length;
            while ((length = stream.read(buffer)) != -1) {
                bytes.write(buffer, 0, length);
            }
            return new String(bytes.toByteArray(), StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot read bundled HUD components", failure);
        }
    }

    private static InputStream sourceStream() {
        // Gradle resources use the classpath root; the RetroMCP delivery keeps its
        // resources/ prefix.
        InputStream stream = GuiHudComponents.class.getResourceAsStream("/betamoon/gui/player_components.lua");
        return stream == null
                ? GuiHudComponents.class.getResourceAsStream("/resources/betamoon/gui/player_components.lua")
                : stream;
    }

    private GuiHudComponents() {
    }
}
