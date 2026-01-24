package betamoon.event.context;

public final class InputEventCtx extends EventContext {
    private final boolean keyEvent;
    private final int keyCode;
    private final char keyChar;
    private final int mouseButton;
    private final int mouseX;
    private final int mouseY;
    private final boolean pressed;
    private final PressAction action;

    public InputEventCtx(net.minecraft.client.Minecraft minecraft, int keyCode, boolean pressed, char keyChar) {
        super(minecraft);
        this.keyEvent = true;
        this.keyCode = keyCode;
        this.keyChar = keyChar;
        this.mouseButton = -1;
        this.mouseX = 0;
        this.mouseY = 0;
        this.pressed = pressed;
        this.action = pressed ? PressAction.PRESSED : PressAction.RELEASED;
    }

    public InputEventCtx(net.minecraft.client.Minecraft minecraft, int keyCode, PressAction action, char keyChar) {
        super(minecraft);
        this.keyEvent = true;
        this.keyCode = keyCode;
        this.keyChar = keyChar;
        this.mouseButton = -1;
        this.mouseX = 0;
        this.mouseY = 0;
        this.action = action;
        this.pressed = action != PressAction.RELEASED;
    }

    public InputEventCtx(net.minecraft.client.Minecraft minecraft, int mouseButton, boolean pressed, int mouseX, int mouseY) {
        super(minecraft);
        this.keyEvent = false;
        this.keyCode = -1;
        this.keyChar = '\0';
        this.mouseButton = mouseButton;
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        this.pressed = pressed;
        this.action = pressed ? PressAction.PRESSED : PressAction.RELEASED;
    }

    public InputEventCtx(net.minecraft.client.Minecraft minecraft, int mouseButton, PressAction action, int mouseX, int mouseY) {
        super(minecraft);
        this.keyEvent = false;
        this.keyCode = -1;
        this.keyChar = '\0';
        this.mouseButton = mouseButton;
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        this.action = action;
        this.pressed = action != PressAction.RELEASED;
    }

    public boolean isKeyEvent() {
        return keyEvent;
    }

    public boolean isMouseEvent() {
        return !keyEvent;
    }

    public int getKeyCode() {
        return keyCode;
    }

    public char getKeyChar() {
        return keyChar;
    }

    public int getMouseButton() {
        return mouseButton;
    }

    public int getMouseX() {
        return mouseX;
    }

    public int getMouseY() {
        return mouseY;
    }

    public boolean isPressed() {
        return pressed;
    }

    public boolean isReleased() {
        return !pressed;
    }

    public PressAction getAction() {
        return action;
    }
}
