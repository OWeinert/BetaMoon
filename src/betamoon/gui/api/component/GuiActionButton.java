package betamoon.gui.api.component;

import net.minecraft.client.Minecraft;
import net.minecraft.src.FontRenderer;
import net.minecraft.src.GuiButton;

public final class GuiActionButton extends GuiButton implements IGuiComponent {
    private IGuiAction action;
    private Minecraft minecraft;

    public GuiActionButton(String label, IGuiAction action) {
        super(0, 0, 0, 0, 0, label);
        this.action = action;
        this.displayString = label;
    }

    public void setLabel(String label) {
        this.displayString = label;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void setAction(IGuiAction action) {
        this.action = action;
    }

    public void setMinecraft(Minecraft minecraft) {
        this.minecraft = minecraft;
    }

    public void setBounds(int left, int top, int right, int bottom) {
        this.xPosition = left;
        this.yPosition = top;
        this.width = Math.max(0, right - left);
        this.height = Math.max(0, bottom - top);
    }

    public void layout(int screenWidth, int screenHeight) {
    }

    public void draw(FontRenderer font, int mouseX, int mouseY, float partialTicks) {
        if (minecraft != null) {
            drawButton(minecraft, mouseX, mouseY);
        }
    }

    public boolean mouseClicked(int mouseX, int mouseY, int button) {
        if (!enabled || button != 0) {
            return false;
        }
        if (minecraft == null || !mousePressed(minecraft, mouseX, mouseY)) {
            return false;
        }
        minecraft.sndManager.playSoundFX("random.click", 1.0F, 1.0F);
        if (action != null) {
            action.onPress();
        }
        return true;
    }

    public boolean mouseReleased(int mouseX, int mouseY, int button) {
        mouseReleased(mouseX, mouseY);
        return false;
    }

    public boolean mouseDragged(int mouseX, int mouseY, boolean mouseDown) {
        return false;
    }

    public boolean mouseScrolled(int mouseX, int mouseY, int wheelDelta, boolean shiftDown) {
        return false;
    }

    public boolean keyTyped(char typedChar, int keyCode) {
        return false;
    }
}
