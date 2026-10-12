package betamoon.client.control.input;

import java.util.Locale;
import org.lwjgl.input.Keyboard;

/** Parsed, immutable device binding with optional modifier requirements. */
public final class InputBinding {
    public static final int SHIFT = 1;
    public static final int CONTROL = 2;
    public static final int ALT = 4;

    private final InputDeviceEvent.Device device;
    private final int code;
    private final int wheelDirection;
    private final int modifiers;
    private final String value;

    private InputBinding(InputDeviceEvent.Device device, int code, int wheelDirection, int modifiers, String value) {
        this.device = device;
        this.code = code;
        this.wheelDirection = wheelDirection;
        this.modifiers = modifiers;
        this.value = value;
    }

    public static InputBinding parse(String value) {
        if (value == null || value.trim().length() == 0) {
            throw new IllegalArgumentException("Input binding is required");
        }
        String[] parts = value.trim().toLowerCase(Locale.ENGLISH).split("\\+");
        int modifiers = 0;
        String devicePart = null;
        for (String part : parts) {
            if ("shift".equals(part)) {
                modifiers |= SHIFT;
            } else if ("ctrl".equals(part) || "control".equals(part)) {
                modifiers |= CONTROL;
            } else if ("alt".equals(part)) {
                modifiers |= ALT;
            } else if (devicePart == null) {
                devicePart = part;
            } else {
                throw new IllegalArgumentException("Input binding has more than one device: " + value);
            }
        }
        if (devicePart == null) {
            throw new IllegalArgumentException("Input binding requires a key, mouse button, or wheel: " + value);
        }
        return parseDevice(devicePart, modifiers, value);
    }

    private static InputBinding parseDevice(String part, int modifiers, String original) {
        if (part.startsWith("key.")) {
            String name = part.substring("key.".length());
            int keyCode = Keyboard.getKeyIndex(name.toUpperCase(Locale.ENGLISH));
            if (keyCode == Keyboard.KEY_NONE && !"none".equals(name)) {
                throw new IllegalArgumentException("Unknown keyboard key in input binding: " + original);
            }
            return new InputBinding(InputDeviceEvent.Device.KEYBOARD, keyCode, 0, modifiers,
                    canonicalModifiers(modifiers) + "key." + name);
        }
        if (part.startsWith("mouse.")) {
            String name = part.substring("mouse.".length());
            if ("wheel".equals(name) || "wheel_up".equals(name) || "wheel_down".equals(name)) {
                int direction = "wheel_up".equals(name) ? 1 : "wheel_down".equals(name) ? -1 : 0;
                return new InputBinding(InputDeviceEvent.Device.MOUSE_WHEEL, 0, direction, modifiers,
                        canonicalModifiers(modifiers) + "mouse." + name);
            }
            int button = mouseButton(name, original);
            return new InputBinding(InputDeviceEvent.Device.MOUSE_BUTTON, button, 0, modifiers,
                    canonicalModifiers(modifiers) + "mouse." + name);
        }
        throw new IllegalArgumentException("Input binding must start with key. or mouse.: " + original);
    }

    private static int mouseButton(String name, String original) {
        if ("left".equals(name)) {
            return 0;
        }
        if ("right".equals(name)) {
            return 1;
        }
        if ("middle".equals(name)) {
            return 2;
        }
        if (name.startsWith("button")) {
            try {
                int displayed = Integer.parseInt(name.substring("button".length()));
                if (displayed >= 1 && displayed <= 32) {
                    return displayed - 1;
                }
            } catch (NumberFormatException ignored) {
            }
        }
        throw new IllegalArgumentException("Unknown mouse button in input binding: " + original);
    }

    private static String canonicalModifiers(int modifiers) {
        StringBuilder result = new StringBuilder();
        if ((modifiers & CONTROL) != 0) {
            result.append("ctrl+");
        }
        if ((modifiers & SHIFT) != 0) {
            result.append("shift+");
        }
        if ((modifiers & ALT) != 0) {
            result.append("alt+");
        }
        return result.toString();
    }

    boolean isActive(InputPhysicalState state) {
        if ((state.modifiers() & modifiers) != modifiers || device == InputDeviceEvent.Device.MOUSE_WHEEL) {
            return false;
        }
        return device == InputDeviceEvent.Device.KEYBOARD ? state.isKeyDown(code) : state.isMouseButtonDown(code);
    }

    boolean matchesWheel(InputDeviceEvent event, int currentModifiers) {
        if (device != InputDeviceEvent.Device.MOUSE_WHEEL || event.device() != device || event.amount() == 0
                || (currentModifiers & modifiers) != modifiers) {
            return false;
        }
        return wheelDirection == 0 || wheelDirection > 0 == event.amount() > 0;
    }

    boolean matchesDevice(InputDeviceEvent event, int currentModifiers) {
        if (event == null || device != event.device() || (currentModifiers & modifiers) != modifiers) {
            return false;
        }
        if (device == InputDeviceEvent.Device.MOUSE_WHEEL) {
            return matchesWheel(event, currentModifiers);
        }
        return code == event.code();
    }

    public InputDeviceEvent.Device device() {
        return device;
    }

    public int code() {
        return code;
    }

    public int modifiers() {
        return modifiers;
    }

    public boolean conflictsWith(InputBinding other) {
        if (other == null || device != other.device || code != other.code) {
            return false;
        }
        if (device != InputDeviceEvent.Device.MOUSE_WHEEL) {
            return true;
        }
        return wheelDirection == 0 || other.wheelDirection == 0 || wheelDirection == other.wheelDirection;
    }

    @Override
    public String toString() {
        return value;
    }
}
