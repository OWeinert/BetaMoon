package betamoon.gui.framework;

import betamoon.gui.framework.GuiGeometry.Rect;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.src.FontRenderer;
import net.minecraft.src.Tessellator;
import org.lwjgl.opengl.GL11;

/** State-safe rendering facade used by every framework element. */
public final class GuiRenderer {
    private static final int DRAW_STATE_BITS = GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT | GL11.GL_COLOR_BUFFER_BIT
            | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_TEXTURE_BIT;

    private final GuiTheme theme;
    private final Deque<Rect> clips = new ArrayDeque<Rect>();
    private Minecraft minecraft;
    private FontRenderer font;
    private int screenWidth;
    private int screenHeight;
    private int displayWidth;
    private int displayHeight;

    public GuiRenderer(GuiTheme theme) {
        if (theme == null) {
            throw new IllegalArgumentException("GUI theme cannot be null");
        }
        this.theme = theme;
    }

    public GuiTheme getTheme() {
        return theme;
    }

    public FontRenderer getFont() {
        return font;
    }

    void beginFrame(Minecraft minecraft, FontRenderer font, int screenWidth, int screenHeight, int displayWidth,
            int displayHeight) {
        this.minecraft = minecraft;
        this.font = font;
        this.screenWidth = Math.max(0, screenWidth);
        this.screenHeight = Math.max(0, screenHeight);
        this.displayWidth = Math.max(0, displayWidth);
        this.displayHeight = Math.max(0, displayHeight);
        clips.clear();
    }

    void endFrame() {
        while (!clips.isEmpty()) {
            popClip();
        }
    }

    public int lineHeight() {
        return lineHeight(font);
    }

    public int textWidth(String text) {
        return font == null || text == null ? 0 : font.getStringWidth(text);
    }

    public int wrappedTextHeight(String text, int width) {
        return font == null || text == null || text.isEmpty() || width <= 0 ? 0 : font.func_27277_a(text, width);
    }

    /** Returns whether a point is inside the active nested clip region. */
    public boolean isPointVisible(int x, int y) {
        return clips.isEmpty() || clips.peek().contains(x, y);
    }

    public String trimToWidth(String text, int maximumWidth) {
        return trimToWidth(font, text, maximumWidth);
    }

    public void drawText(String text, int x, int y, int color) {
        if (font != null && text != null && !text.isEmpty()) {
            font.drawStringWithShadow(text, x, y, color);
        }
    }

    public void drawWrappedText(String text, int x, int y, int width, int color) {
        if (font != null && text != null && !text.isEmpty() && width > 0) {
            font.func_27278_a(text, x, y, width, color);
        }
    }

    public void drawScaledText(String text, int x, int y, int color, float scale) {
        if (font == null || text == null || text.isEmpty()) {
            return;
        }
        float safeScale = scale <= 0.0F ? 1.0F : scale;
        GL11.glPushMatrix();
        try {
            GL11.glScalef(safeScale, safeScale, 1.0F);
            font.drawStringWithShadow(text, (int) (x / safeScale), (int) (y / safeScale), color);
        } finally {
            GL11.glPopMatrix();
        }
    }

    public void drawScaledCenteredText(String text, int centerX, int y, int color, float scale) {
        if (font == null || text == null || text.isEmpty()) {
            return;
        }
        float safeScale = scale <= 0.0F ? 1.0F : scale;
        GL11.glPushMatrix();
        try {
            GL11.glScalef(safeScale, safeScale, 1.0F);
            int scaledX = (int) (centerX / safeScale);
            int scaledY = (int) (y / safeScale);
            font.drawStringWithShadow(text, scaledX - font.getStringWidth(text) / 2, scaledY, color);
        } finally {
            GL11.glPopMatrix();
        }
    }

    public void drawScaledCenteredText(String text, Rect bounds, int color, float scale, boolean shadow) {
        if (font == null || text == null || text.isEmpty() || bounds == null || bounds.isEmpty()) {
            return;
        }
        float safeScale = scale <= 0.0F ? 1.0F : scale;
        int textWidth = font.getStringWidth(text);
        int textHeight = lineHeight();
        GL11.glPushMatrix();
        try {
            GL11.glTranslatef(bounds.getLeft() + bounds.getWidth() / 2.0F,
                    bounds.getTop() + bounds.getHeight() / 2.0F, 0.0F);
            GL11.glScalef(safeScale, safeScale, 1.0F);
            if (shadow) {
                font.drawStringWithShadow(text, -textWidth / 2, -textHeight / 2, color);
            } else {
                font.drawString(text, -textWidth / 2, -textHeight / 2, color);
            }
        } finally {
            GL11.glPopMatrix();
        }
    }

    public void drawScaledUnderlinedText(String text, int x, int y, int color, float scale) {
        drawScaledText(text, x, y, color, scale);
        float safeScale = scale <= 0.0F ? 1.0F : scale;
        int underlineEnd = x + (int) (textWidth(text) * safeScale);
        int underlineY = y + (int) (8 * safeScale) + 1;
        drawRect(new Rect(x, underlineY, underlineEnd, underlineY + 1), color | 0xFF000000);
    }

    public void drawRect(Rect rect, int color) {
        if (rect == null || rect.isEmpty()) {
            return;
        }
        drawRect(rect.getLeft(), rect.getTop(), rect.getRight(), rect.getBottom(), color);
    }

    public void drawRect(int left, int top, int right, int bottom, int color) {
        if (right <= left || bottom <= top) {
            return;
        }
        GL11.glPushAttrib(DRAW_STATE_BITS);
        try {
            float alpha = (float) (color >> 24 & 255) / 255.0F;
            float red = (float) (color >> 16 & 255) / 255.0F;
            float green = (float) (color >> 8 & 255) / 255.0F;
            float blue = (float) (color & 255) / 255.0F;
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glColor4f(red, green, blue, alpha);
            Tessellator tessellator = Tessellator.instance;
            tessellator.startDrawingQuads();
            tessellator.addVertex(right, top, 0.0D);
            tessellator.addVertex(left, top, 0.0D);
            tessellator.addVertex(left, bottom, 0.0D);
            tessellator.addVertex(right, bottom, 0.0D);
            tessellator.draw();
        } finally {
            GL11.glPopAttrib();
        }
    }

    public void drawHorizontalLine(int left, int right, int y, int color) {
        drawRect(Math.min(left, right), y, Math.max(left, right), y + 1, color);
    }

    public void drawVerticalLine(int top, int bottom, int x, int color) {
        drawRect(x, Math.min(top, bottom), x + 1, Math.max(top, bottom), color);
    }

    public void drawPackagedTexture(String path, Rect bounds) {
        drawPackagedTexture(path, bounds, 0.0F, 0.0F, 1.0F, 1.0F, 255);
    }

    public void drawPackagedTexture(String path, Rect bounds, float minU, float minV, float maxU, float maxV,
            int alpha) {
        if (minecraft == null || path == null || bounds == null || bounds.isEmpty()) {
            return;
        }
        GL11.glPushAttrib(DRAW_STATE_BITS);
        try {
            minecraft.renderEngine.bindTexture(minecraft.renderEngine.getTexture(path));
            drawBoundTextureContents(bounds, minU, minV, maxU, maxV, alpha);
        } finally {
            GL11.glPopAttrib();
        }
    }

    public void drawTexture(GuiTextureCache.Texture texture, Rect bounds) {
        if (texture == null || bounds == null || bounds.isEmpty()) {
            return;
        }
        GL11.glPushAttrib(DRAW_STATE_BITS);
        try {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture.getTextureId());
            drawBoundTextureContents(bounds, 0.0F, 0.0F, 1.0F, 1.0F, 255);
        } finally {
            GL11.glPopAttrib();
        }
    }

    public void drawBoundTexture(Rect bounds, float minU, float minV, float maxU, float maxV, int alpha) {
        if (bounds == null || bounds.isEmpty()) {
            return;
        }
        GL11.glPushAttrib(DRAW_STATE_BITS);
        try {
            drawBoundTextureContents(bounds, minU, minV, maxU, maxV, alpha);
        } finally {
            GL11.glPopAttrib();
        }
    }

    private static void drawBoundTextureContents(Rect bounds, float minU, float minV, float maxU, float maxV,
            int alpha) {
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, Math.max(0, Math.min(255, alpha)) / 255.0F);
        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        tessellator.addVertexWithUV(bounds.getLeft(), bounds.getBottom(), 0.0D, minU, maxV);
        tessellator.addVertexWithUV(bounds.getRight(), bounds.getBottom(), 0.0D, maxU, maxV);
        tessellator.addVertexWithUV(bounds.getRight(), bounds.getTop(), 0.0D, maxU, minV);
        tessellator.addVertexWithUV(bounds.getLeft(), bounds.getTop(), 0.0D, minU, minV);
        tessellator.draw();
    }

    public void pushClip(Rect requestedClip) {
        Rect clip = clips.isEmpty() ? requestedClip : clips.peek().intersect(requestedClip);
        clips.push(clip);
        GL11.glPushAttrib(GL11.GL_SCISSOR_BIT);
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        applyClip(clip);
    }

    public void popClip() {
        if (clips.isEmpty()) {
            throw new IllegalStateException("GUI clip stack is empty");
        }
        clips.pop();
        GL11.glPopAttrib();
    }

    private void applyClip(Rect clip) {
        if (clip == null || screenWidth <= 0 || screenHeight <= 0 || displayWidth <= 0 || displayHeight <= 0) {
            GL11.glScissor(0, 0, 0, 0);
            return;
        }
        int x = clip.getLeft() * displayWidth / screenWidth;
        int y = (screenHeight - clip.getBottom()) * displayHeight / screenHeight;
        int width = clip.getWidth() * displayWidth / screenWidth;
        int height = clip.getHeight() * displayHeight / screenHeight;
        GL11.glScissor(x, y, Math.max(0, width), Math.max(0, height));
    }

    public void drawTooltip(List<String> lines, int mouseX, int mouseY) {
        drawTooltipLines(font, theme, screenWidth, screenHeight, lines, mouseX, mouseY);
    }

    public static int lineHeight(FontRenderer font) {
        return font == null ? 0 : font.func_27277_a("A", 10000);
    }

    public static String trimToWidth(FontRenderer font, String text, int maximumWidth) {
        if (text == null || font == null || maximumWidth <= 0) {
            return "";
        }
        if (font.getStringWidth(text) <= maximumWidth) {
            return text;
        }
        String ellipsis = "...";
        int targetWidth = maximumWidth - font.getStringWidth(ellipsis);
        if (targetWidth <= 0) {
            return ellipsis;
        }
        int end = text.length();
        while (end > 0 && font.getStringWidth(text.substring(0, end)) > targetWidth) {
            end--;
        }
        return text.substring(0, end) + ellipsis;
    }

    /** Standalone tooltip entry point for Minecraft container screens. */
    public static void drawTooltipLines(FontRenderer font, int screenWidth, int screenHeight, List<String> lines,
            int mouseX, int mouseY) {
        drawTooltipLines(font, GuiTheme.DEFAULT, screenWidth, screenHeight, lines, mouseX, mouseY);
    }

    private static void drawTooltipLines(FontRenderer font, GuiTheme theme, int screenWidth, int screenHeight,
            List<String> lines, int mouseX, int mouseY) {
        if (font == null || lines == null || lines.isEmpty()) {
            return;
        }
        int padding = 4;
        int textWidth = 0;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i) == null ? "" : lines.get(i);
            textWidth = Math.max(textWidth, font.getStringWidth(line));
        }
        int lineHeight = lineHeight(font);
        int textHeight = lines.size() * (lineHeight + 2) - 2;
        int boxWidth = textWidth + padding * 2;
        int boxHeight = textHeight + padding * 2;
        int x = mouseX + 12;
        int y = mouseY + 8;
        if (x + boxWidth > screenWidth - 4) {
            x = screenWidth - boxWidth - 4;
        }
        if (y + boxHeight > screenHeight - 4) {
            y = screenHeight - boxHeight - 4;
        }
        x = Math.max(4, x);
        y = Math.max(4, y);

        GL11.glPushAttrib(DRAW_STATE_BITS);
        try {
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDepthMask(false);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_ALPHA_TEST);
            GuiRenderer renderer = new GuiRenderer(theme);
            renderer.font = font;
            renderer.drawRect(x, y, x + boxWidth, y + boxHeight, theme.tooltipBackground);
            renderer.drawRect(x, y, x + boxWidth, y + 1, theme.tooltipBorder);
            renderer.drawRect(x, y + boxHeight - 1, x + boxWidth, y + boxHeight, theme.tooltipBorder);
            renderer.drawRect(x, y, x + 1, y + boxHeight, theme.tooltipBorder);
            renderer.drawRect(x + boxWidth - 1, y, x + boxWidth, y + boxHeight, theme.tooltipBorder);
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i) == null ? "" : lines.get(i);
                font.drawStringWithShadow(line, x + padding, y + padding + i * (lineHeight + 2), theme.textPrimary);
            }
        } finally {
            GL11.glPopAttrib();
        }
    }
}
