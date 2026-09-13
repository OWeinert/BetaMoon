package betamoon.gui.widget;

import betamoon.gui.framework.GuiAction;
import betamoon.gui.framework.GuiContext;
import betamoon.gui.framework.GuiElement;
import betamoon.gui.framework.GuiInputEvent;
import org.lwjgl.input.Keyboard;

/** Packaged sprite with optional tooltip and action. */
public class GuiIcon extends GuiElement {
    private String texturePath;
    private String tooltip;
    private GuiAction action;
    private float minU;
    private float maxU = 1.0F;
    private int alpha = 255;

    public GuiIcon(String texturePath) {
        this.texturePath = texturePath;
    }

    public void setTexturePath(String texturePath) {
        this.texturePath = texturePath;
    }

    public void setHorizontalSprite(float minU, float maxU) {
        this.minU = minU;
        this.maxU = maxU;
    }

    public void setAlpha(int alpha) {
        this.alpha = Math.max(0, Math.min(255, alpha));
    }

    public void setTooltip(String tooltip) {
        this.tooltip = tooltip;
    }

    public void setAction(GuiAction action) {
        this.action = action;
        setFocusable(action != null);
    }

    @Override
    protected void renderElement(GuiContext context) {
        context.getRenderer().drawPackagedTexture(texturePath, getBounds(), minU, 0.0F, maxU, 1.0F, alpha);
        if (isHovered(context) && tooltip != null && !tooltip.isEmpty()) {
            context.showTooltip(tooltip);
        }
    }

    @Override
    protected boolean onInput(GuiContext context, GuiInputEvent event) {
        boolean pointerActivation = event.getType() == GuiInputEvent.Type.POINTER_DOWN && event.getButton() == 0
                && contains(event.getMouseX(), event.getMouseY());
        boolean keyboardActivation = event.getType() == GuiInputEvent.Type.KEY_TYPED && isFocused()
                && (event.getKeyCode() == Keyboard.KEY_RETURN || event.getKeyCode() == Keyboard.KEY_NUMPADENTER
                        || event.getKeyCode() == Keyboard.KEY_SPACE);
        if ((!pointerActivation && !keyboardActivation) || action == null) {
            return false;
        }
        requestFocus(context);
        if (context.getMinecraft() != null) {
            context.getMinecraft().sndManager.playSoundFX("random.click", 1.0F, 1.0F);
        }
        action.run();
        return true;
    }
}
