package betamoon.gui.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.src.GuiButton;
import org.lwjgl.opengl.GL11;

/**
 * Native button appearance with tiled middle pixels for widths beyond the
 * vanilla atlas strip.
 */
public class GuiMinecraftButton extends GuiButton {
    private int preparedTexture = -1;

    public void setPreparedTexture(int texture) {
        preparedTexture = texture;
    }

    public GuiMinecraftButton(int id, int x, int y, int width, int height, String label) {
        super(id, x, y, width, height, label);
    }

    @Override
    public void drawButton(Minecraft minecraft, int mouseX, int mouseY) {
        if (!enabled2) {
            return;
        }
        GL11.glBindTexture(GL11.GL_TEXTURE_2D,
                preparedTexture >= 0 ? preparedTexture : minecraft.renderEngine.getTexture("/gui/gui.png"));
        GL11.glColor4f(1, 1, 1, 1);
        boolean hovered = mouseX >= xPosition && mouseY >= yPosition && mouseX < xPosition + width
                && mouseY < yPosition + height;
        int textureY = 46 + getHoverState(hovered) * 20;
        int edge = Math.min(2, width / 2);
        drawTexturedModalRect(xPosition, yPosition, 0, textureY, edge, height);
        for (int offset = edge; offset < width - edge;) {
            int span = Math.min(196, width - edge - offset);
            drawTexturedModalRect(xPosition + offset, yPosition, 2, textureY, span, height);
            offset += span;
        }
        drawTexturedModalRect(xPosition + width - edge, yPosition, 200 - edge, textureY, edge, height);
        mouseDragged(minecraft, mouseX, mouseY);
        int color = !enabled ? 0xa0a0a0 : hovered ? 0xffffa0 : 0xe0e0e0;
        drawCenteredString(minecraft.fontRenderer, displayString, xPosition + width / 2, yPosition + (height - 8) / 2,
                color);
    }
}
