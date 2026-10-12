package betamoon.client.control.input;

import betamoon.luaapi.LuaApiUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.src.EntityPlayerSP;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.Display;

/** Connects verified native event seams to the game-thread input router. */
public final class ClientInputRuntime {
    private static final InputRouter ROUTER = new InputRouter((context, action, error) -> LuaApiUtils
            .warnForScript(context.owner(), "Input", context.registration().definition().key() + "." + action
                    + " context disabled after error: " + safeMessage(error)));
    private static boolean keyboardConsumed;
    private static boolean mouseConsumed;
    private static Minecraft currentMinecraft;

    private ClientInputRuntime() {
    }

    public static InputRouter router() {
        return ROUTER;
    }

    public static void beginCycle(Minecraft minecraft) {
        currentMinecraft = minecraft;
        keyboardConsumed = false;
        mouseConsumed = false;
        if (minecraft == null) {
            ROUTER.beginCycle(null, null, null, false);
            return;
        }
        boolean focused = !Display.isCreated() || Display.isActive();
        ROUTER.beginCycle(minecraft.theWorld, minecraft.thePlayer, minecraft.currentScreen, focused);
    }

    public static boolean nextKeyboardEvent() {
        boolean available = Keyboard.next();
        keyboardConsumed = false;
        if (available) {
            int keyCode = Keyboard.getEventKey();
            keyboardConsumed = ROUTER.accept(InputDeviceEvent.key(keyCode, Keyboard.getEventKeyState()));
            keyboardConsumed |= capturesGuiInput();
            keyboardConsumed |= ROUTER.captures(InputFamily.INVENTORY) && isInventoryKey(keyCode);
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
            int wheel = Mouse.getEventDWheel();
            if (wheel != 0) {
                mouseConsumed |= ROUTER.accept(InputDeviceEvent.mouseWheel(wheel));
                mouseConsumed |= ROUTER.captures(InputFamily.INVENTORY);
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
        return currentMinecraft != null && currentMinecraft.currentScreen != null && ROUTER.captures(InputFamily.GUI);
    }

    private static boolean isInventoryKey(int keyCode) {
        if (currentMinecraft == null || currentMinecraft.gameSettings == null) {
            return false;
        }
        return keyCode == currentMinecraft.gameSettings.keyBindInventory.keyCode
                || keyCode == currentMinecraft.gameSettings.keyBindDrop.keyCode
                || keyCode >= Keyboard.KEY_1 && keyCode <= Keyboard.KEY_9;
    }

    public static boolean consumesMovementKey(EntityPlayerSP player, int keyCode) {
        return keyboardConsumed || ROUTER.captures(InputFamily.MOVEMENT)
                || ROUTER.isPhysicalConsumed(InputDeviceEvent.Device.KEYBOARD, keyCode);
    }

    public static boolean consumesWorldAction(int button) {
        return ROUTER.captures(InputFamily.WORLD_ACTIONS)
                || ROUTER.isPhysicalConsumed(InputDeviceEvent.Device.MOUSE_BUTTON, button);
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
