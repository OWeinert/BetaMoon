package betamoon.gui.widget;

import betamoon.gui.framework.GuiGeometry.Rect;
import betamoon.gui.style.GuiNativeButtonSkin;
import net.minecraft.client.Minecraft;
import net.minecraft.src.GuiButton;

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
        boolean hovered = mouseX >= xPosition && mouseY >= yPosition && mouseX < xPosition + width
                && mouseY < yPosition + height;
        GuiNativeButtonSkin.drawLegacy(
                preparedTexture >= 0 ? preparedTexture : minecraft.renderEngine.getTexture(GuiNativeButtonSkin.TEXTURE),
                Rect.fromPositionAndSize(xPosition, yPosition, width, height), enabled, hovered);
        mouseDragged(minecraft, mouseX, mouseY);
        int color = !enabled ? 0xa0a0a0 : hovered ? 0xffffa0 : 0xe0e0e0;
        drawCenteredString(minecraft.fontRenderer, displayString, xPosition + width / 2, yPosition + (height - 8) / 2,
                color);
    }
}
