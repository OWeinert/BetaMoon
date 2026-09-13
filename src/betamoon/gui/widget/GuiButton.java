package betamoon.gui.widget;

import betamoon.gui.framework.GuiAction;
import betamoon.gui.framework.GuiContext;
import betamoon.gui.framework.GuiElement;
import betamoon.gui.framework.GuiGeometry.Constraints;
import betamoon.gui.framework.GuiGeometry.Size;
import betamoon.gui.framework.GuiInputEvent;
import net.minecraft.client.Minecraft;
import org.lwjgl.input.Keyboard;

/** Retained wrapper around Minecraft's native button renderer and hit behavior. */
public final class GuiButton extends GuiElement {
    private static final int DEFAULT_HEIGHT = 20;

    private final NativeButton nativeButton;
    private GuiAction action;
    private int preferredWidth;

    public GuiButton(String label, int preferredWidth, GuiAction action) {
        nativeButton = new NativeButton(label == null ? "" : label);
        this.preferredWidth = Math.max(0, preferredWidth);
        this.action = action;
        setFocusable(true);
    }

    public GuiButton(String label, GuiAction action) {
        this(label, 0, action);
    }

    public void setLabel(String label) {
        nativeButton.displayString = label == null ? "" : label;
        requestLayout();
    }

    public String getLabel() {
        return nativeButton.displayString;
    }

    public void setAction(GuiAction action) {
        this.action = action;
    }

    public void setPreferredWidth(int preferredWidth) {
        this.preferredWidth = Math.max(0, preferredWidth);
        requestLayout();
    }

    @Override
    protected Size measureElement(GuiContext context, Constraints constraints) {
        int labelWidth = context == null || context.getRenderer() == null ? 0
                : context.getRenderer().textWidth(nativeButton.displayString) + 20;
        return constraints.constrain(new Size(Math.max(preferredWidth, labelWidth), DEFAULT_HEIGHT));
    }

    @Override
    protected void renderElement(GuiContext context) {
        Minecraft minecraft = context.getMinecraft();
        if (minecraft == null) {
            return;
        }
        updateNativeBounds();
        nativeButton.enabled = isEnabled();
        nativeButton.drawButton(minecraft, context.getMouseX(), context.getMouseY());
    }

    @Override
    protected boolean onInput(GuiContext context, GuiInputEvent event) {
        Minecraft minecraft = context.getMinecraft();
        if (event.getType() == GuiInputEvent.Type.POINTER_DOWN && event.getButton() == 0 && minecraft != null) {
            updateNativeBounds();
            if (!nativeButton.mousePressed(minecraft, event.getMouseX(), event.getMouseY())) {
                return false;
            }
            requestFocus(context);
            activate(minecraft);
            return true;
        }
        if (event.getType() == GuiInputEvent.Type.POINTER_UP) {
            nativeButton.mouseReleased(event.getMouseX(), event.getMouseY());
            return false;
        }
        if (event.getType() == GuiInputEvent.Type.KEY_TYPED && isFocused()
                && (event.getKeyCode() == Keyboard.KEY_RETURN || event.getKeyCode() == Keyboard.KEY_NUMPADENTER
                        || event.getKeyCode() == Keyboard.KEY_SPACE)) {
            activate(minecraft);
            return true;
        }
        return false;
    }

    private void activate(Minecraft minecraft) {
        if (!isEnabled()) {
            return;
        }
        if (minecraft != null) {
            minecraft.sndManager.playSoundFX("random.click", 1.0F, 1.0F);
        }
        if (action != null) {
            action.run();
        }
    }

    private void updateNativeBounds() {
        nativeButton.setGeometry(getLeft(), getTop(), getWidth(), getHeight());
    }

    private static final class NativeButton extends net.minecraft.src.GuiButton {
        private NativeButton(String label) {
            super(0, 0, 0, 0, 0, label);
        }

        private void setGeometry(int x, int y, int width, int height) {
            this.xPosition = x;
            this.yPosition = y;
            this.width = width;
            this.height = height;
        }
    }
}
