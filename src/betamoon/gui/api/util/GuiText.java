package betamoon.gui.api.util;

import java.util.Collections;
import java.util.List;
import net.minecraft.src.FontRenderer;
import org.lwjgl.opengl.GL11;

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
    public static void drawTooltip(FontRenderer font, int screenWidth, int screenHeight, String text, int mouseX,
            int mouseY) {
        if (font == null || text == null || text.isEmpty()) {
            return;
        }
        drawTooltipLines(font, screenWidth, screenHeight, Collections.singletonList(text), mouseX, mouseY);
    }

    /**
     * Draws an overlay independently of the lighting and depth state left by a
     * container.
     */
    public static void drawTooltipLines(FontRenderer font, int screenWidth, int screenHeight, List<String> lines,
            int mouseX, int mouseY) {
        if (font == null || lines == null || lines.isEmpty()) {
            return;
        }
        int padding = 4;
        int textWidth = 0;
        for (int i = 0; i < lines.size(); i++) {
            textWidth = Math.max(textWidth, font.getStringWidth(lines.get(i)));
        }
        int lineHeight = getLineHeight(font);
        int textHeight = lines.size() * (lineHeight + 2) - 2;
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
        x = Math.max(4, x);
        y = Math.max(4, y);
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT
                | GL11.GL_TEXTURE_BIT);
        try {
            // GuiContainer re-enables these before returning from drawScreen.
            // Depth testing can hide the glyphs behind the tooltip's own background.
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDepthMask(false);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_ALPHA_TEST);
            GL11.glAlphaFunc(GL11.GL_GREATER, 0.1F);
            GuiUtils.drawRect(x, y, x + boxWidth, y + boxHeight, GuiColors.TOOLTIP_BG);
            GuiUtils.drawRect(x, y, x + boxWidth, y + 1, GuiColors.TOOLTIP_BORDER);
            GuiUtils.drawRect(x, y + boxHeight - 1, x + boxWidth, y + boxHeight, GuiColors.TOOLTIP_BORDER);
            GuiUtils.drawRect(x, y, x + 1, y + boxHeight, GuiColors.TOOLTIP_BORDER);
            GuiUtils.drawRect(x + boxWidth - 1, y, x + boxWidth, y + boxHeight, GuiColors.TOOLTIP_BORDER);
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            for (int i = 0; i < lines.size(); i++) {
                font.drawStringWithShadow(lines.get(i), x + padding, y + padding + i * (lineHeight + 2),
                        GuiColors.TEXT_PRIMARY);
            }
        } finally {
            GL11.glPopAttrib();
        }
    }

    /**
     * Draws centered text inside a rectangle, applying a scale factor.
     */
    public static void drawCenteredScaledString(FontRenderer font, String text, int left, int top, int width,
            int height, int color, float scale) {
        if (font == null || text == null || text.isEmpty()) {
            return;
        }
        if (scale <= 0.0F) {
            scale = 1.0F;
        }
        int textWidth = font.getStringWidth(text);
        int textHeight = getLineHeight(font);
        if (textHeight <= 0) {
            textHeight = 8;
        }
        GL11.glPushMatrix();
        float centerX = left + width / 2.0F;
        float centerY = top + height / 2.0F;
        GL11.glTranslatef(centerX, centerY, 0.0F);
        GL11.glScalef(scale, scale, 1.0F);
        font.drawString(text, -textWidth / 2, -textHeight / 2, color);
        GL11.glPopMatrix();
    }
}
