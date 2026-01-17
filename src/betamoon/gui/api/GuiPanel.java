package betamoon.gui.api;

import net.minecraft.src.FontRenderer;

public class GuiPanel extends GuiContainer {
    private int color = GuiColors.POPUP_PANEL;

    public GuiPanel() {
    }

    public GuiPanel(int color) {
        this.color = color;
    }

    public void setColor(int color) {
        this.color = color;
    }

    public void draw(FontRenderer font, int mouseX, int mouseY, float partialTicks) {
        GuiUtils.drawRect(left, top, right, bottom, color);
        super.draw(font, mouseX, mouseY, partialTicks);
    }
}
