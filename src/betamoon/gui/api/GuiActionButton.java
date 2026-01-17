package betamoon.gui.api;

import net.minecraft.src.FontRenderer;
import net.minecraft.src.GuiButton;
import net.minecraft.client.Minecraft;

public final class GuiActionButton extends GuiComponentBase {
    public interface Action {
        void onPress();
    }

    private String label;
    private boolean enabled = true;
    private Action action;
    private Minecraft minecraft;
    private GuiButton vanillaButton;
    private int lastWidth = -1;
    private int lastHeight = -1;

    public GuiActionButton(String label, Action action) {
        this.label = label;
        this.action = action;
        this.vanillaButton = new GuiButton(0, 0, 0, 0, 0, label);
    }

    public void setLabel(String label) {
        this.label = label;
        if (vanillaButton != null) {
            vanillaButton.displayString = label;
        }
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (vanillaButton != null) {
            vanillaButton.enabled = enabled;
        }
    }

    public void setAction(Action action) {
        this.action = action;
    }

    public void setMinecraft(Minecraft minecraft) {
        this.minecraft = minecraft;
    }

    public void setBounds(int left, int top, int right, int bottom) {
        super.setBounds(left, top, right, bottom);
        int width = Math.max(0, right - left);
        int height = Math.max(0, bottom - top);
        if (vanillaButton == null || lastWidth != width || lastHeight != height) {
            vanillaButton = new GuiButton(0, left, top, width, height, label == null ? "" : label);
            lastWidth = width;
            lastHeight = height;
        } else {
            vanillaButton.xPosition = left;
            vanillaButton.yPosition = top;
        }
        vanillaButton.enabled = enabled;
    }

    public void draw(FontRenderer font, int mouseX, int mouseY, float partialTicks) {
        if (minecraft != null) {
            vanillaButton.enabled = enabled;
            vanillaButton.displayString = label == null ? "" : label;
            vanillaButton.drawButton(minecraft, mouseX, mouseY);
            return;
        }
        // Fallback when Minecraft instance is unavailable.
        boolean hovered = isMouseOver(mouseX, mouseY);
        int bg = enabled ? (hovered ? GuiColors.BUTTON_BG_HOVER : GuiColors.BUTTON_BG) : GuiColors.BUTTON_BG_DISABLED;
        GuiUtils.drawRect(left, top, right, bottom, bg);
        if (font != null && label != null) {
            int textWidth = font.getStringWidth(label);
            int textX = left + (right - left - textWidth) / 2;
            int textY = top + (bottom - top - 8) / 2;
            font.drawStringWithShadow(label, textX, textY, GuiColors.BUTTON_TEXT);
        }
    }

    public boolean mouseClicked(int mouseX, int mouseY, int button) {
        if (!enabled || button != 0) {
            return false;
        }
        if (!isMouseOver(mouseX, mouseY)) {
            return false;
        }
        if (action != null) {
            action.onPress();
        }
        return true;
    }
}
