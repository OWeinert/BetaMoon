package betamoon.gui.control;

import betamoon.gui.framework.GuiInputEvent;
import java.util.Objects;
import org.lwjgl.input.Keyboard;

/** Shared activation state, independent of painting and host actions. */
public final class GuiActivation {
    public enum Timing {
        LEGACY_PRESS, RELEASE
    }

    public static final int CONSUMED = 1;
    public static final int ACTIVATE = 2;
    public static final int CAPTURE = 4;
    public static final int RELEASE = 8;

    private final Timing timing;
    private boolean pointerPressed;
    private int keyPressed = -1;

    public GuiActivation(Timing timing) {
        this.timing = Objects.requireNonNull(timing, "activation timing");
    }

    public boolean isPressed() {
        return pointerPressed || keyPressed >= 0;
    }

    public void cancelKeyboard() {
        keyPressed = -1;
    }

    public int input(GuiInputEvent event, boolean inside, boolean focused) {
        switch (event.getType()) {
            case POINTER_DOWN:
                if (event.getButton() != 0 || !inside) {
                    return 0;
                }
                pointerPressed = true;
                return CONSUMED | (timing == Timing.LEGACY_PRESS ? ACTIVATE : CAPTURE);
            case POINTER_DRAG:
                return pointerPressed && timing == Timing.RELEASE ? CONSUMED : 0;
            case POINTER_UP:
                if (event.getButton() != 0 || !pointerPressed) {
                    return 0;
                }
                pointerPressed = false;
                return timing == Timing.LEGACY_PRESS ? RELEASE : CONSUMED | RELEASE | (inside ? ACTIVATE : 0);
            case POINTER_CANCEL:
                boolean pressed = pointerPressed;
                pointerPressed = false;
                return pressed ? CONSUMED | RELEASE : 0;
            case KEY_TYPED:
                int key = event.getKeyCode();
                if (!focused || !(key == Keyboard.KEY_SPACE || key == Keyboard.KEY_RETURN
                        || key == Keyboard.KEY_NUMPADENTER)) {
                    return 0;
                }
                if (timing == Timing.LEGACY_PRESS) {
                    return CONSUMED | ACTIVATE;
                }
                if (keyPressed < 0) {
                    keyPressed = key;
                }
                return CONSUMED;
            case KEY_UP:
                if (event.getKeyCode() != keyPressed) {
                    return 0;
                }
                keyPressed = -1;
                return CONSUMED | (focused ? ACTIVATE : 0);
            default:
                return 0;
        }
    }
}
