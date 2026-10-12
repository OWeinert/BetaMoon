package betamoon.client.control.input;

import betamoon.content.ContentKey;
import betamoon.content.ContentType;
import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import net.minecraft.src.GameSettings;
import net.minecraft.src.GuiButton;
import net.minecraft.src.GuiOptions;
import net.minecraft.src.GuiScreen;
import org.lwjgl.input.Keyboard;

/** Headless interaction checks for the Controls path and action editor. */
public final class InputControlsGuiTest {
    private InputControlsGuiTest() {
    }

    public static void main(String[] args) throws Exception {
        File file = Files.createTempDirectory("betamoon-controls-gui").resolve("inputs.json").toFile();
        InputPreferenceStore preferences = new InputPreferenceStore(file);
        InputRouter router = new InputRouter(null, preferences);
        InputMapRegistration map = router.register(new InputMapDefinition(
                ContentKey.parseForType("test:gui", ContentType.INPUT_MAP), Arrays.asList(
                        new InputActionDefinition("jump", Arrays.asList(InputBinding.parse("key.j"))),
                        new InputActionDefinition("rotate", Arrays.asList(InputBinding.parse("key.r"))))),
                "gui.lua");
        GuiControlsHub hub = new GuiControlsHub(new GuiOptions(null, new GameSettings()), new GameSettings());
        hub.width = 320;
        hub.height = 240;
        hub.initGui();
        require(button(hub, 1) != null && button(hub, 2) != null,
                "Controls hub exposes native and BetaMoon pages");
        GuiBetaMoonActions editor = new GuiBetaMoonActions(hub, router);
        editor.width = 320;
        editor.height = 240;
        editor.initGui();
        require(GuiControlsHub.isProtectedScreen(editor) && GuiControlsHub.isProtectedScreen(hub),
                "Engine Controls screens bypass script GUI capture");
        require(button(editor, 1006) != null && button(editor, 1011).xPosition + 43 <= editor.width,
                "Small-screen layout keeps action controls on screen");

        editor.actionPerformed(button(editor, 1006));
        editor.keyTyped('k', Keyboard.KEY_K);
        require("key.k".equals(map.bindings("jump").get(0).toString())
                && preferences.count() == 1, "Keyboard capture writes a saved binding");
        editor.actionPerformed(button(editor, 1006));
        editor.captureKey(Keyboard.KEY_K, InputBinding.CONTROL | InputBinding.SHIFT);
        require("ctrl+shift+key.k".equals(map.bindings("jump").get(0).toString()),
                "Modifier capture preserves the full chord");
        editor.actionPerformed(button(editor, 1006));
        editor.keyTyped(' ', Keyboard.KEY_ESCAPE);
        require("ctrl+shift+key.k".equals(map.bindings("jump").get(0).toString()),
                "Escape cancels capture without changing preferences");
        editor.actionPerformed(button(editor, 1006));
        editor.mouseClicked(0, 0, 2);
        require("mouse.button3".equals(map.bindings("jump").get(0).toString()),
                "Mouse button capture uses the physical button");
        editor.actionPerformed(button(editor, 1006));
        editor.captureWheel(120, 0);
        require("mouse.wheel_up".equals(map.bindings("jump").get(0).toString()),
                "Wheel capture saves a directional binding");
        editor.actionPerformed(button(editor, 1006));
        editor.actionPerformed(button(editor, 7));
        require("key.escape".equals(map.bindings("jump").get(0).toString()),
                "Escape can be bound explicitly without losing the capture cancel path");

        editor.actionPerformed(button(editor, 1006));
        editor.keyTyped('r', Keyboard.KEY_R);
        require("key.escape".equals(map.bindings("jump").get(0).toString()) && button(editor, 8) != null,
                "A conflict preserves the current binding and offers an explicit choice");
        editor.actionPerformed(button(editor, 8));
        require("key.r".equals(map.bindings("jump").get(0).toString()),
                "Keep both applies an explicitly accepted conflict");
        editor.actionPerformed(button(editor, 1011));
        require("key.j".equals(map.bindings("jump").get(0).toString()),
                "Per-action Reset restores the current declaration");

        Field searchFocused = GuiBetaMoonActions.class.getDeclaredField("searchFocused");
        searchFocused.setAccessible(true);
        searchFocused.setBoolean(editor, true);
        editor.keyTyped('z', Keyboard.KEY_Z);
        require(button(editor, 1006) == null, "Search filters action rows");
        editor.keyTyped(' ', Keyboard.KEY_ESCAPE);
        editor.actionPerformed(button(editor, 2));
        require(button(editor, 2).displayString.equals("Confirm reset"), "Reset all requires a second click");
        editor.actionPerformed(button(editor, 2));
        require(preferences.count() == 0, "Reset all removes saved map preferences");
        map.close();
        System.out.println("Controls GUI passed: hub, capture, conflicts, reset, search and escape.");
    }

    private static GuiButton button(GuiScreen screen, int id) throws Exception {
        Field field = GuiScreen.class.getDeclaredField("controlList");
        field.setAccessible(true);
        List<?> buttons = (List<?>) field.get(screen);
        for (Object entry : buttons) {
            GuiButton button = (GuiButton) entry;
            if (button.id == id) {
                return button;
            }
        }
        return null;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
