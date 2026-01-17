package betamoon.gui.api;

import net.minecraft.src.FontRenderer;

public final class GuiLine extends GuiComponentBase {
    private final boolean vertical;
    private int color;

    public GuiLine(boolean vertical, int color) {
        this.vertical = vertical;
        this.color = color;
    }

    public void setColor(int color) {
        this.color = color;
    }

    public void draw(FontRenderer font, int mouseX, int mouseY, float partialTicks) {
        if (vertical) {
            GuiUtils.drawVerticalLine(top, bottom, left, color);
        } else {
            GuiUtils.drawHorizontalLine(left, right, top, color);
        }
    }
}
