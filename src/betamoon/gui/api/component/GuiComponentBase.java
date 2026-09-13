package betamoon.gui.api.component;

import net.minecraft.src.FontRenderer;

public abstract class GuiComponentBase implements IGuiComponent {
    protected int left;
    protected int top;
    protected int right;
    protected int bottom;

    @Override
    public void setBounds(int left, int top, int right, int bottom) {
        this.left = left;
        this.top = top;
        this.right = right;
        this.bottom = bottom;
    }

    @Override
    public void layout(int screenWidth, int screenHeight) {
    }

    @Override
    public void draw(FontRenderer font, int mouseX, int mouseY, float partialTicks) {
    }

    @Override
    public boolean mouseClicked(int mouseX, int mouseY, int button) {
        return false;
    }

    @Override
    public boolean mouseReleased(int mouseX, int mouseY, int button) {
        return false;
    }

    @Override
    public boolean mouseDragged(int mouseX, int mouseY, boolean mouseDown) {
        return false;
    }

    @Override
    public boolean mouseScrolled(int mouseX, int mouseY, int wheelDelta, boolean shiftDown) {
        return false;
    }

    @Override
    public boolean keyTyped(char typedChar, int keyCode) {
        return false;
    }

    protected boolean isMouseOver(int mouseX, int mouseY) {
        return mouseX >= left && mouseX <= right && mouseY >= top && mouseY <= bottom;
    }
}
