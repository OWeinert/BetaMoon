package betamoon.client.control.input;

/** Immutable physical transition captured from the LWJGL event queues. */
public final class InputDeviceEvent {
    public enum Device {
        KEYBOARD("keyboard"), MOUSE_BUTTON("mouse_button"), MOUSE_WHEEL("mouse_wheel");

        private final String luaName;

        Device(String luaName) {
            this.luaName = luaName;
        }

        public String luaName() {
            return luaName;
        }
    }

    private final Device device;
    private final int code;
    private final boolean pressed;
    private final float amount;

    private InputDeviceEvent(Device device, int code, boolean pressed, float amount) {
        this.device = device;
        this.code = code;
        this.pressed = pressed;
        this.amount = amount;
    }

    public static InputDeviceEvent key(int keyCode, boolean pressed) {
        return new InputDeviceEvent(Device.KEYBOARD, keyCode, pressed, pressed ? 1 : 0);
    }

    public static InputDeviceEvent mouseButton(int button, boolean pressed) {
        return new InputDeviceEvent(Device.MOUSE_BUTTON, button, pressed, pressed ? 1 : 0);
    }

    public static InputDeviceEvent mouseWheel(int delta) {
        return new InputDeviceEvent(Device.MOUSE_WHEEL, 0, false, delta);
    }

    public Device device() {
        return device;
    }

    public int code() {
        return code;
    }

    public boolean pressed() {
        return pressed;
    }

    public float amount() {
        return amount;
    }
}
