package betamoon.gui.api.component;

import net.minecraft.src.FontRenderer;

public interface IGuiComponent {
    void setBounds(int left, int top, int right, int bottom);

    void layout(int screenWidth, int screenHeight);

    void draw(FontRenderer font, int mouseX, int mouseY, float partialTicks);

    boolean mouseClicked(int mouseX, int mouseY, int button);

    boolean mouseReleased(int mouseX, int mouseY, int button);

    boolean mouseDragged(int mouseX, int mouseY, boolean mouseDown);

    boolean mouseScrolled(int mouseX, int mouseY, int wheelDelta, boolean shiftDown);

    boolean keyTyped(char typedChar, int keyCode);
}
