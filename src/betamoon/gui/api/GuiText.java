package betamoon.gui.api;

import net.minecraft.src.FontRenderer;

/**
 * Shared text helpers for GUI rendering.
 */
public final class GuiText {
    private GuiText() {
    }

    /**
     * Returns the line height for the current font renderer.
     */
    public static int getLineHeight(FontRenderer font) {
        if (font == null) {
            return 0;
        }
        return font.func_27277_a("A", 10000);
    }

    /**
     * Trims a string to the given pixel width, appending an ellipsis when needed.
     */
    public static String trimToWidth(FontRenderer font, String text, int maxWidth) {
        if (text == null) {
            return "";
        }
        if (font == null || maxWidth <= 0) {
            return "";
        }
        if (font.getStringWidth(text) <= maxWidth) {
            return text;
        }
        int ellipsisWidth = font.getStringWidth("...");
        if (ellipsisWidth >= maxWidth) {
            return "...";
        }
        int targetWidth = maxWidth - ellipsisWidth;
        String trimmed = text;
        while (!trimmed.isEmpty() && font.getStringWidth(trimmed) > targetWidth) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed + "...";
    }

    /**
     * Draws a simple tooltip box near the mouse position.
     */
    public static void drawTooltip(FontRenderer font, int screenWidth, int screenHeight, String text, int mouseX, int mouseY) {
        if (font == null || text == null || text.isEmpty()) {
            return;
        }
        int padding = 4;
        int textWidth = font.getStringWidth(text);
        int textHeight = font.func_27277_a("A", 10000);
        int boxWidth = textWidth + padding * 2;
        int boxHeight = textHeight + padding * 2;
        int x = mouseX + 12;
        int y = mouseY + 8;
        // Keep the tooltip within the screen bounds.
        if (x + boxWidth > screenWidth - 4) {
            x = screenWidth - boxWidth - 4;
        }
        if (y + boxHeight > screenHeight - 4) {
            y = screenHeight - boxHeight - 4;
        }
        // Draw background and 1px border.
        GuiUtils.drawRect(x, y, x + boxWidth, y + boxHeight, 0xCC101010);
        GuiUtils.drawRect(x, y, x + boxWidth, y + 1, 0xFF555555);
        GuiUtils.drawRect(x, y + boxHeight - 1, x + boxWidth, y + boxHeight, 0xFF555555);
        GuiUtils.drawRect(x, y, x + 1, y + boxHeight, 0xFF555555);
        GuiUtils.drawRect(x + boxWidth - 1, y, x + boxWidth, y + boxHeight, 0xFF555555);
        font.drawStringWithShadow(text, x + padding, y + padding, 0xFFFFFF);
    }
}
