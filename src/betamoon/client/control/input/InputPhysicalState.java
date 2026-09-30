package betamoon.client.control.input;

import java.util.HashSet;
import java.util.Set;
import org.lwjgl.input.Keyboard;

/** Mutable device state owned only by the game-thread input router. */
final class InputPhysicalState {
    private final Set<Integer> keys = new HashSet<Integer>();
    private final Set<Integer> mouseButtons = new HashSet<Integer>();

    void apply(InputDeviceEvent event) {
        if (event.device() == InputDeviceEvent.Device.KEYBOARD) {
            update(keys, event.code(), event.pressed());
        } else if (event.device() == InputDeviceEvent.Device.MOUSE_BUTTON) {
            update(mouseButtons, event.code(), event.pressed());
        }
    }

    private static void update(Set<Integer> values, int code, boolean pressed) {
        if (pressed) {
            values.add(Integer.valueOf(code));
        } else {
            values.remove(Integer.valueOf(code));
        }
    }

    boolean isKeyDown(int keyCode) {
        return keys.contains(Integer.valueOf(keyCode));
    }

    boolean isMouseButtonDown(int button) {
        return mouseButtons.contains(Integer.valueOf(button));
    }

    int modifiers() {
        int result = 0;
        if (isKeyDown(Keyboard.KEY_LSHIFT) || isKeyDown(Keyboard.KEY_RSHIFT)) {
            result |= InputBinding.SHIFT;
        }
        if (isKeyDown(Keyboard.KEY_LCONTROL) || isKeyDown(Keyboard.KEY_RCONTROL)) {
            result |= InputBinding.CONTROL;
        }
        if (isKeyDown(Keyboard.KEY_LMENU) || isKeyDown(Keyboard.KEY_RMENU)) {
            result |= InputBinding.ALT;
        }
        return result;
    }

    void clear() {
        keys.clear();
        mouseButtons.clear();
    }
}
