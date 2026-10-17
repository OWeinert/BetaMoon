package betamoon.gui.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.src.GuiButton;

/** A square, inset Minecraft-style checkbox independent of its row label. */
public final class GuiCheckbox extends GuiButton {
    private final boolean selected;

    public GuiCheckbox(int id, int x, int y, boolean selected, boolean editable) {
        super(id, x, y, 20, 20, "");
        this.selected = selected;
        enabled = editable;
    }

    @Override
    public void drawButton(Minecraft minecraft, int mouseX, int mouseY) {
        if (!enabled2) {
            return;
        }
        boolean hovered = enabled && mouseX >= xPosition && mouseY >= yPosition && mouseX < xPosition + width
                && mouseY < yPosition + height;
        int edge = !enabled ? 0xff555555 : hovered ? 0xffffffaa : 0xffaaaaaa;
        drawRect(xPosition, yPosition, xPosition + 20, yPosition + 20, 0xff000000);
        drawRect(xPosition + 1, yPosition + 1, xPosition + 19, yPosition + 19, edge);
        drawRect(xPosition + 2, yPosition + 2, xPosition + 18, yPosition + 18, 0xff171717);
        drawRect(xPosition + 2, yPosition + 2, xPosition + 18, yPosition + 3, 0xff080808);
        if (selected) {
            int color = enabled ? 0xffeeeeee : 0xff666666;
            int size = 10;
            int stroke = 2;
            int left = xPosition + (width - size) / 2;
            int top = yPosition + (height - size) / 2;
            for (int i = 0; i <= size - stroke; i++) {
                drawRect(left + i, top + i, left + i + stroke, top + i + stroke, color);
                int opposite = left + size - stroke - i;
                drawRect(opposite, top + i, opposite + stroke, top + i + stroke, color);
            }
        }
    }
}
