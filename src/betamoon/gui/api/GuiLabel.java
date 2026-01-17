package betamoon.gui.api;

import net.minecraft.src.FontRenderer;

public final class GuiLabel extends GuiComponentBase {
    private String text;
    private int color = GuiColors.TEXT_PRIMARY;
    private float scale = 1.0F;
    private boolean centered;

    public GuiLabel(String text) {
        this.text = text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public void setColor(int color) {
        this.color = color;
    }

    public void setScale(float scale) {
        this.scale = scale;
    }

    public void setCentered(boolean centered) {
        this.centered = centered;
    }

    public void draw(FontRenderer font, int mouseX, int mouseY, float partialTicks) {
        if (font == null || text == null) {
            return;
        }
        if (centered) {
            int centerX = left + (right - left) / 2;
            GuiUtils.drawScaledCenteredString(font, text, centerX, top, color, scale);
        } else {
            GuiUtils.drawScaledString(font, text, left, top, color, scale);
        }
    }
}
