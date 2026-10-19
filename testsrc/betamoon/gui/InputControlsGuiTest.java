package betamoon.gui;

import betamoon.client.control.input.InputRouter;
import betamoon.client.control.input.InputPreferenceStore;
import betamoon.client.control.input.InputBinding;
import betamoon.client.control.input.InputMapRegistration;
import betamoon.client.control.input.InputMapDefinition;
import betamoon.client.control.input.InputActionDefinition;
import betamoon.client.control.input.InputActivationMode;

import betamoon.content.ContentKey;
import betamoon.content.ContentType;
import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.src.GameSettings;
import net.minecraft.src.GuiButton;
import net.minecraft.src.GuiOptions;
import net.minecraft.src.GuiScreen;
import net.minecraft.src.KeyBinding;
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
                ContentKey.parseForType("test:gui", ContentType.INPUT_MAP),
                Arrays.asList(new InputActionDefinition("jump", Arrays.asList(InputBinding.parse("key.j"))),
                        new InputActionDefinition("rotate", Arrays.asList(InputBinding.parse("key.r"))),
                        new InputActionDefinition("dash", Arrays.asList(InputBinding.parse("key.d"))),
                        new InputActionDefinition("sprint", Arrays.asList(InputBinding.parse("key.s")), "Sprint",
                                "Move faster", true, EnumSet.of(InputActivationMode.HOLD, InputActivationMode.TOGGLE),
                                InputActivationMode.HOLD),
                        new InputActionDefinition("use", Arrays.asList(InputBinding.parse("key.u"))),
                        new InputActionDefinition("interact", Arrays.asList(InputBinding.parse("key.i"))))),
                "gui.lua");
        GameSettings settings = new GameSettings();
        GuiOptions parent = new GuiOptions(null, settings);
        GuiTabbedControls minecraftTab = new GuiTabbedControls(parent, settings);
        minecraftTab.width = 320;
        minecraftTab.height = 240;
        minecraftTab.initGui();
        require(button(minecraftTab, GuiTabbedControls.MINECRAFT_TAB) != null
                && button(minecraftTab, GuiTabbedControls.BETAMOON_TAB) != null && button(minecraftTab, 0) != null
                && button(minecraftTab, 200) != null, "Minecraft bindings remain the default Controls tab");
        require(!button(minecraftTab, GuiTabbedControls.MINECRAFT_TAB).enabled
                && button(minecraftTab, GuiTabbedControls.BETAMOON_TAB).enabled,
                "The active Minecraft tab is distinguishable and BetaMoon is selectable");
        GameSettings manyKeys = new GameSettings();
        manyKeys.keyBindings = Arrays.copyOf(manyKeys.keyBindings, 16);
        for (int index = 10; index < manyKeys.keyBindings.length; index++) {
            manyKeys.keyBindings[index] = new KeyBinding("key.extra" + index, index + 20);
        }
        GuiTabbedControls scrollingTab = new GuiTabbedControls(parent, manyKeys);
        scrollingTab.width = 320;
        scrollingTab.height = 240;
        scrollingTab.initGui();
        require(!button(scrollingTab, 14).enabled2
                && button(scrollingTab, 200).yPosition > button(scrollingTab, 10).yPosition,
                "Overflow bindings stay clear of Done and the tabs");
        scrollingTab.scrollBindingRows(2);
        require(button(scrollingTab, 14).enabled2 && !button(scrollingTab, 0).enabled2,
                "Scrolling reaches later native and mod hotkeys");
        GuiBetaMoonActions editor = new GuiBetaMoonActions(parent, settings, router);
        editor.width = 320;
        editor.height = 240;
        editor.initGui();
        require(Keyboard.areRepeatEventsEnabled(), "The searchable Controls tab requests held-key repeat events");
        require(GuiTabbedControls.isProtectedScreen(editor) && GuiTabbedControls.isProtectedScreen(minecraftTab),
                "Engine Controls screens bypass script GUI capture");
        require(button(editor, GuiTabbedControls.MINECRAFT_TAB).enabled
                && !button(editor, GuiTabbedControls.BETAMOON_TAB).enabled
                && "+".equals(button(editor, 1001).displayString) && "-".equals(button(editor, 1002).displayString)
                && "R".equals(button(editor, 1003).displayString) && button(editor, 1004) == null
                && button(editor, 1000).yPosition == button(editor, 1003).yPosition
                && button(editor, 1005).yPosition > button(editor, 1000).yPosition
                && button(editor, 1003).xPosition + 22 <= editor.width
                && "Hold".equals(button(editor, 1019).displayString)
                && button(editor, 1019).xPosition + 58 <= editor.width,
                "Each binding occupies one row and fixed-mode actions omit the mode button");
        Field scroll = GuiBetaMoonActions.class.getDeclaredField("scroll");
        scroll.setAccessible(true);
        editor.captureWheel(-120, 0);
        require(scroll.getInt(editor) == 1 && button(editor, 1000) != null,
                "Mouse wheel reaches later binding rows on a small screen");
        editor.captureWheel(120, 0);
        require(scroll.getInt(editor) == 0, "Mouse wheel returns to the first action");

        editor.actionPerformed(button(editor, 1000));
        editor.keyTyped('k', Keyboard.KEY_K);
        require("key.k".equals(map.bindings("jump").get(0).toString()) && preferences.count() == 1,
                "Keyboard capture writes a saved binding");
        editor.actionPerformed(button(editor, 1000));
        editor.captureKey(Keyboard.KEY_K, InputBinding.CONTROL | InputBinding.SHIFT);
        require("ctrl+shift+key.k".equals(map.bindings("jump").get(0).toString()),
                "Modifier capture preserves the full chord");
        editor.actionPerformed(button(editor, 1000));
        editor.keyTyped(' ', Keyboard.KEY_ESCAPE);
        require("ctrl+shift+key.k".equals(map.bindings("jump").get(0).toString()),
                "Escape cancels capture without changing preferences");
        editor.actionPerformed(button(editor, 1000));
        editor.mouseClicked(0, 0, 2);
        require("mouse.button3".equals(map.bindings("jump").get(0).toString()),
                "Mouse button capture uses the physical button");
        editor.actionPerformed(button(editor, 1000));
        editor.captureWheel(120, 0);
        require("mouse.wheel_up".equals(map.bindings("jump").get(0).toString()),
                "Wheel capture saves a directional binding");
        editor.actionPerformed(button(editor, 1000));
        editor.actionPerformed(button(editor, 7));
        require("key.escape".equals(map.bindings("jump").get(0).toString()),
                "Escape can be bound explicitly without losing the capture cancel path");

        editor.actionPerformed(button(editor, 1000));
        editor.keyTyped('r', Keyboard.KEY_R);
        require("key.escape".equals(map.bindings("jump").get(0).toString()) && button(editor, 8) != null,
                "A conflict preserves the current binding and offers an explicit choice");
        editor.actionPerformed(button(editor, 8));
        require("key.r".equals(map.bindings("jump").get(0).toString()),
                "Use anyway applies an explicitly accepted conflict");
        editor.actionPerformed(button(editor, 1003));
        require("key.j".equals(map.bindings("jump").get(0).toString()),
                "Per-action Reset restores the current declaration");
        editor.actionPerformed(button(editor, 1000));
        require(!button(editor, GuiTabbedControls.MINECRAFT_TAB).enabled && button(editor, 10).enabled,
                "Capture clearly disables navigation and offers a Cancel binding button");
        editor.actionPerformed(button(editor, 10));
        require("key.j".equals(map.bindings("jump").get(0).toString()),
                "Cancel binding leaves the current input unchanged");
        editor.actionPerformed(button(editor, 1001));
        editor.captureKey(Keyboard.KEY_K, 0);
        require(map.bindings("jump").size() == 2 && button(editor, 1005) != null
                && button(editor, 1005).yPosition != button(editor, 1000).yPosition,
                "Add binding creates exactly one additional row");
        editor.actionPerformed(button(editor, 1005));
        editor.captureKey(Keyboard.KEY_L, 0);
        require("key.j".equals(map.bindings("jump").get(0).toString())
                && "key.l".equals(map.bindings("jump").get(1).toString()),
                "Clicking the second binding changes only that input");
        editor.actionPerformed(button(editor, 1007));
        require(map.bindings("jump").size() == 1 && "key.j".equals(map.bindings("jump").get(0).toString()),
                "Remove clears the selected input");
        require("Ctrl + Shift + K".equals(InputBinding.parse("ctrl+shift+key.k").displayName())
                && "Left mouse".equals(InputBinding.parse("mouse.left").displayName()),
                "Binding names use player-facing key and mouse labels");
        editor.actionPerformed(button(editor, 1019));
        require(map.mode("sprint") == InputActivationMode.TOGGLE && "Toggle".equals(button(editor, 1019).displayString),
                "The compact mode button shows and updates Hold/Toggle state");
        editor.actionPerformed(button(editor, 1015));
        editor.captureWheel(120, 0);
        require("mouse.wheel_up".equals(map.bindings("sprint").get(0).toString()),
                "Scrolling during capture saves a wheel binding even for a Toggle action");
        InputMapRegistration restored = new InputRouter(null, new InputPreferenceStore(file)).register(map.definition(),
                "gui.lua");
        require(restored.mode("sprint") == InputActivationMode.TOGGLE
                && "mouse.wheel_up".equals(restored.bindings("sprint").get(0).toString()),
                "A Toggle wheel binding survives preference reload");
        restored.close();

        editor.mouseClicked(100, 60, 0);
        require("".equals(editor.searchDisplayText()), "Focusing search preserves its empty value");
        editor.keyTyped('z', Keyboard.KEY_Z);
        require("z".equals(editor.searchDisplayText()) && button(editor, 1000) == null,
                "Typing keeps the search caret visible and filters binding rows");
        editor.keyTyped('o', Keyboard.KEY_O);
        editor.keyTyped('\b', Keyboard.KEY_BACK);
        editor.keyTyped('\b', Keyboard.KEY_BACK);
        editor.keyTyped('\b', Keyboard.KEY_BACK);
        require("".equals(editor.searchDisplayText()) && button(editor, 1000) != null,
                "Repeated Backspace clears search and restores the unfiltered actions");
        editor.keyTyped(' ', Keyboard.KEY_ESCAPE);
        editor.actionPerformed(button(editor, 2));
        require(button(editor, 2).displayString.equals("Confirm reset"), "Reset all requires a second click");
        editor.actionPerformed(button(editor, 2));
        require(preferences.count() == 0, "Reset all removes saved map preferences");
        editor.onGuiClosed();
        require(!Keyboard.areRepeatEventsEnabled(),
                "Closing the searchable Controls tab restores ordinary keyboard handling");
        map.close();
        System.out.println("Controls GUI passed: tabs, capture, conflicts, reset, search and escape.");
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
