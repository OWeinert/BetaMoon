package betamoon.client.control.input;

import betamoon.gui.GuiTabbedControls;
import betamoon.io.BetaMoonDirectories;
import betamoon.luaapi.LuaApiUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.src.EntityPlayerSP;
import net.minecraft.src.MovementInputFromOptions;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.Display;

/** Connects verified native event seams to the game-thread input router. */
public final class ClientInputRuntime {
    private static final PlayerControllerRuntime CONTROLLERS = new PlayerControllerRuntime((registration, stage,
            error) -> LuaApiUtils.warnForScript(registration.owner(), "Input controller", registration.definition().key
                    + "." + stage.luaName() + " activation released after error: " + safeMessage(error)));
    private static final InputPreferenceStore PREFERENCES = new InputPreferenceStore(
            BetaMoonDirectories.controlsFileForInstance(Minecraft.getMinecraftDir()));
    private static final InputRouter ROUTER = new InputRouter((context, action, error) -> LuaApiUtils
            .warnForScript(context.owner(), "Input", context.registration().definition().key() + "." + action
                    + " context disabled after error: " + safeMessage(error)),
            PREFERENCES);
    private static boolean keyboardConsumed;
    private static boolean mouseConsumed;
    private static Minecraft currentMinecraft;

    private ClientInputRuntime() {
    }

    public static InputRouter router() {
        return ROUTER;
    }

    public static Minecraft client() {
        return currentMinecraft;
    }

    public static PlayerControllerRuntime controllers() {
        return CONTROLLERS;
    }

    public static void beginCycle(Minecraft minecraft) {
        currentMinecraft = minecraft;
        keyboardConsumed = false;
        mouseConsumed = false;
        if (minecraft == null) {
            ROUTER.beginCycle(null, null, null, false);
            CONTROLLERS.beginCycle(null, null, null, false);
            return;
        }
        boolean focused = !Display.isCreated() || Display.isActive();
        ROUTER.beginCycle(minecraft.theWorld, minecraft.thePlayer, minecraft.currentScreen, focused);
        CONTROLLERS.beginCycle(minecraft.theWorld, minecraft.thePlayer, minecraft.currentScreen, focused);
    }

    public static boolean nextKeyboardEvent() {
        boolean available = Keyboard.next();
        keyboardConsumed = false;
        if (available) {
            int keyCode = Keyboard.getEventKey();
            if (isProtectedScreen()) {
                return true;
            }
            keyboardConsumed = ROUTER.accept(InputDeviceEvent.key(keyCode, Keyboard.getEventKeyState()));
            keyboardConsumed |= capturesGuiInput();
            keyboardConsumed |= ROUTER.captures(InputFamily.INVENTORY) && isInventoryKey(keyCode);
            if (!keyboardConsumed && Keyboard.getEventKeyState() && isInventoryKey(keyCode) && currentMinecraft != null
                    && currentMinecraft.currentScreen == null) {
                keyboardConsumed = !CONTROLLERS.allowInventory(currentMinecraft.thePlayer, inventoryAction(keyCode),
                        keyCode);
            }
        }
        return available;
    }

    public static boolean keyboardEventState() {
        return Keyboard.getEventKeyState() && !keyboardConsumed;
    }

    public static boolean nextMouseEvent() {
        boolean available = Mouse.next();
        mouseConsumed = false;
        if (available) {
            if (isProtectedScreen()) {
                return true;
            }
            int wheel = Mouse.getEventDWheel();
            if (wheel != 0) {
                mouseConsumed |= ROUTER.accept(InputDeviceEvent.mouseWheel(wheel));
                mouseConsumed |= ROUTER.captures(InputFamily.INVENTORY);
                if (!mouseConsumed && currentMinecraft != null && currentMinecraft.currentScreen == null) {
                    mouseConsumed = !CONTROLLERS.allowInventory(currentMinecraft.thePlayer, "hotbarWheel", wheel);
                }
            }
            int button = Mouse.getEventButton();
            if (button >= 0) {
                mouseConsumed |= ROUTER.accept(InputDeviceEvent.mouseButton(button, Mouse.getEventButtonState()));
            }
            mouseConsumed |= capturesGuiInput();
        }
        return available;
    }

    public static boolean mouseEventButtonState() {
        return Mouse.getEventButtonState() && !mouseConsumed;
    }

    public static boolean consumesCurrentKeyboardEvent() {
        return keyboardConsumed;
    }

    public static boolean consumesCurrentMouseEvent() {
        return mouseConsumed;
    }

    public static int mouseEventWheel() {
        return mouseConsumed ? 0 : Mouse.getEventDWheel();
    }

    private static boolean capturesGuiInput() {
        return currentMinecraft != null && currentMinecraft.currentScreen != null && !isProtectedScreen()
                && ROUTER.captures(InputFamily.GUI);
    }

    private static boolean isProtectedScreen() {
        return currentMinecraft != null && GuiTabbedControls.isProtectedScreen(currentMinecraft.currentScreen);
    }

    private static boolean isInventoryKey(int keyCode) {
        if (currentMinecraft == null || currentMinecraft.gameSettings == null) {
            return false;
        }
        return keyCode == currentMinecraft.gameSettings.keyBindInventory.keyCode
                || keyCode == currentMinecraft.gameSettings.keyBindDrop.keyCode
                || keyCode >= Keyboard.KEY_1 && keyCode <= Keyboard.KEY_9;
    }

    private static String inventoryAction(int keyCode) {
        if (currentMinecraft != null && currentMinecraft.gameSettings != null) {
            if (keyCode == currentMinecraft.gameSettings.keyBindInventory.keyCode) {
                return "open";
            }
            if (keyCode == currentMinecraft.gameSettings.keyBindDrop.keyCode) {
                return "drop";
            }
        }
        return "hotbar";
    }

    public static boolean consumesMovementKey(EntityPlayerSP player, int keyCode, boolean pressed) {
        return pressed && (keyboardConsumed || ROUTER.captures(InputFamily.MOVEMENT)
                || ROUTER.isPhysicalConsumed(InputDeviceEvent.Device.KEYBOARD, keyCode));
    }

    public static boolean consumesWorldAction(int button) {
        return ROUTER.captures(InputFamily.WORLD_ACTIONS)
                || ROUTER.isPhysicalConsumed(InputDeviceEvent.Device.MOUSE_BUTTON, button);
    }

    public static boolean allowsControllerWorldAction(Minecraft minecraft, int button, boolean held) {
        return minecraft == null || CONTROLLERS.allowWorldAction(minecraft.thePlayer, button, held);
    }

    public static boolean appliesControllerLook(EntityPlayerSP player, float yawDelta, float pitchDelta) {
        return CONTROLLERS.applyLook(player, yawDelta, pitchDelta);
    }

    public static void applyControllerMovement(MovementInputFromOptions input, EntityPlayerSP player) {
        CONTROLLERS.movement(input, player);
    }

    public static boolean consumesLook() {
        return ROUTER.captures(InputFamily.LOOK);
    }

    private static String safeMessage(Throwable error) {
        if (error == null) {
            return "unknown error";
        }
        String message = error.getMessage();
        return message == null || message.trim().length() == 0 ? error.getClass().getSimpleName() : message;
    }
}
