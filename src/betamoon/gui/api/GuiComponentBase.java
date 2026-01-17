package betamoon.gui.api;

import net.minecraft.src.FontRenderer;

public abstract class GuiComponentBase implements GuiComponent {
    protected int left;
    protected int top;
    protected int right;
    protected int bottom;

    public void setBounds(int left, int top, int right, int bottom) {
        this.left = left;
        this.top = top;
        this.right = right;
        this.bottom = bottom;
    }

    public void layout(int screenWidth, int screenHeight) {
    }

    public void draw(FontRenderer font, int mouseX, int mouseY, float partialTicks) {
    }

    public boolean mouseClicked(int mouseX, int mouseY, int button) {
        return false;
    }

    public boolean mouseReleased(int mouseX, int mouseY, int button) {
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

    protected boolean isMouseOver(int mouseX, int mouseY) {
        return mouseX >= left && mouseX <= right && mouseY >= top && mouseY <= bottom;
    }
}
