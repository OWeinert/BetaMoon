package betamoon.gui.framework;

/** Immutable input event translated from Minecraft and LWJGL callbacks. */
public final class GuiInputEvent {
    public enum Type {
        POINTER_DOWN,
        POINTER_UP,
        POINTER_DRAG,
        SCROLL,
        KEY_TYPED
    }

    private final Type type;
    private final int mouseX;
    private final int mouseY;
    private final int button;
    private final int wheelDelta;
    private final boolean shiftDown;
    private final boolean mouseDown;
    private final char typedChar;
    private final int keyCode;

    private GuiInputEvent(Type type, int mouseX, int mouseY, int button, int wheelDelta, boolean shiftDown,
            boolean mouseDown, char typedChar, int keyCode) {
        this.type = type;
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        this.button = button;
        this.wheelDelta = wheelDelta;
        this.shiftDown = shiftDown;
        this.mouseDown = mouseDown;
        this.typedChar = typedChar;
        this.keyCode = keyCode;
    }

    public static GuiInputEvent pointerDown(int mouseX, int mouseY, int button) {
        return new GuiInputEvent(Type.POINTER_DOWN, mouseX, mouseY, button, 0, false, true, '\0', 0);
    }

    public static GuiInputEvent pointerUp(int mouseX, int mouseY, int button) {
        return new GuiInputEvent(Type.POINTER_UP, mouseX, mouseY, button, 0, false, false, '\0', 0);
    }

    public static GuiInputEvent pointerDrag(int mouseX, int mouseY, boolean mouseDown) {
        return new GuiInputEvent(Type.POINTER_DRAG, mouseX, mouseY, 0, 0, false, mouseDown, '\0', 0);
    }

    public static GuiInputEvent scroll(int mouseX, int mouseY, int wheelDelta, boolean shiftDown) {
        return new GuiInputEvent(Type.SCROLL, mouseX, mouseY, -1, wheelDelta, shiftDown, false, '\0', 0);
    }

    public static GuiInputEvent keyTyped(char typedChar, int keyCode, boolean shiftDown) {
        return new GuiInputEvent(Type.KEY_TYPED, 0, 0, -1, 0, shiftDown, false, typedChar, keyCode);
    }

    public Type getType() {
        return type;
    }

    public int getMouseX() {
        return mouseX;
    }

    public int getMouseY() {
        return mouseY;
    }

    public int getButton() {
        return button;
    }

    public int getWheelDelta() {
        return wheelDelta;
    }

    public boolean isShiftDown() {
        return shiftDown;
    }

    public boolean isMouseDown() {
        return mouseDown;
    }

    public char getTypedChar() {
        return typedChar;
    }

    public int getKeyCode() {
        return keyCode;
    }

    public boolean isPointerEvent() {
        return type != Type.KEY_TYPED;
    }
}
