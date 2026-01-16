package betamoon.gui.api;

import java.awt.Desktop;
import java.io.File;
import net.minecraft.src.FontRenderer;
import org.lwjgl.opengl.GL11;

public final class GuiUtils {
    public static final int COLOR_LIST_SEPERATOR = GuiColors.LIST_SEPARATOR;

    /**
     * Utility class for shared GUI rendering helpers.
     */
    private GuiUtils() {
    }

    /**
     * Draws a solid colored rectangle in GUI space.
     *
     * @param left left edge
     * @param top top edge
     * @param right right edge
     * @param bottom bottom edge
     * @param color ARGB color
     */
    public static void drawRect(int left, int top, int right, int bottom, int color) {
        // Normalize coordinates to match GuiScreen.drawRect behavior.
        int swap;
        if (left < right) {
            swap = left;
            left = right;
            right = swap;
        }
        if (top < bottom) {
            swap = top;
            top = bottom;
            bottom = swap;
        }
        float a = (float) (color >> 24 & 255) / 255.0F;
        float r = (float) (color >> 16 & 255) / 255.0F;
        float g = (float) (color >> 8 & 255) / 255.0F;
        float b = (float) (color & 255) / 255.0F;
        net.minecraft.src.Tessellator tessellator = net.minecraft.src.Tessellator.instance;
        // Render a flat quad without texture sampling.
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glColor4f(r, g, b, a);
        tessellator.startDrawingQuads();
        tessellator.addVertex((double) left, (double) bottom, 0.0D);
        tessellator.addVertex((double) right, (double) bottom, 0.0D);
        tessellator.addVertex((double) right, (double) top, 0.0D);
        tessellator.addVertex((double) left, (double) top, 0.0D);
        tessellator.draw();
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_BLEND);
    }

    /**
     * Draws scaled text centered around the provided x coordinate.
     *
     * @param font font renderer
     * @param text text to draw
     * @param centerX center x position
     * @param y top position
     * @param color ARGB color
     * @param scale scale factor
     */
    public static void drawScaledCenteredString(FontRenderer font, String text, int centerX, int y, int color, float scale) {
        GL11.glPushMatrix();
        GL11.glScalef(scale, scale, 1.0F);
        int scaledX = (int) (centerX / scale);
        int scaledY = (int) (y / scale);
        font.drawStringWithShadow(text, scaledX - font.getStringWidth(text) / 2, scaledY, color);
        GL11.glPopMatrix();
    }

    /**
     * Draws scaled text aligned to the left.
     *
     * @param font font renderer
     * @param text text to draw
     * @param x left position
     * @param y top position
     * @param color ARGB color
     * @param scale scale factor
     */
    public static void drawScaledString(FontRenderer font, String text, int x, int y, int color, float scale) {
        GL11.glPushMatrix();
        GL11.glScalef(scale, scale, 1.0F);
        int scaledX = (int) (x / scale);
        int scaledY = (int) (y / scale);
        font.drawStringWithShadow(text, scaledX, scaledY, color);
        GL11.glPopMatrix();
    }

    /**
     * Draws scaled text with a 1px underline in the same color.
     *
     * @param font font renderer
     * @param text text to draw
     * @param x left position
     * @param y top position
     * @param textColor ARGB text/underline color
     * @param scale scale factor
     */
    public static void drawScaledStringUL(FontRenderer font, String text, int x, int y, int textColor, float scale) {
        int textWidth = font.getStringWidth(text);
        int underlineEnd = x + (int) (textWidth * scale);
        int underlineY = y + (int) (8 * scale) + 1;
        drawScaledString(font, text, x, y, textColor, scale);
        int textColorArgb = textColor | GuiColors.ALPHA_OPAQUE_MASK; // ensure alpha channel is 0xFF
        drawRect(x, underlineY, underlineEnd, underlineY + 1, textColorArgb);
    }

    /**
     * Draws a 1px horizontal line.
     *
     * @param left left edge
     * @param right right edge
     * @param y y position
     * @param color ARGB color
     */
    public static void drawHorizontalLine(int left, int right, int y, int color) {
        if (left > right) {
            int swap = left;
            left = right;
            right = swap;
        }
        net.minecraft.src.Tessellator tessellator = net.minecraft.src.Tessellator.instance;
        // Render a thin quad as a line.
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        float a = (float) (color >> 24 & 255) / 255.0F;
        float r = (float) (color >> 16 & 255) / 255.0F;
        float g = (float) (color >> 8 & 255) / 255.0F;
        float b = (float) (color & 255) / 255.0F;
        GL11.glColor4f(r, g, b, a);
        tessellator.startDrawingQuads();
        tessellator.addVertex((double) left, (double) (y + 1), 0.0D);
        tessellator.addVertex((double) right, (double) (y + 1), 0.0D);
        tessellator.addVertex((double) right, (double) y, 0.0D);
        tessellator.addVertex((double) left, (double) y, 0.0D);
        tessellator.draw();
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_ALPHA_TEST);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
    }

    /**
     * Draws a 1px vertical line.
     *
     * @param top top edge
     * @param bottom bottom edge
     * @param x x position
     * @param color ARGB color
     */
    public static void drawVerticalLine(int top, int bottom, int x, int color) {
        if (top > bottom) {
            int swap = top;
            top = bottom;
            bottom = swap;
        }
        net.minecraft.src.Tessellator tessellator = net.minecraft.src.Tessellator.instance;
        // Render a thin quad as a line.
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        float a = (float) (color >> 24 & 255) / 255.0F;
        float r = (float) (color >> 16 & 255) / 255.0F;
        float g = (float) (color >> 8 & 255) / 255.0F;
        float b = (float) (color & 255) / 255.0F;
        GL11.glColor4f(r, g, b, a);
        tessellator.startDrawingQuads();
        tessellator.addVertex((double) (x + 1), (double) bottom, 0.0D);
        tessellator.addVertex((double) x, (double) bottom, 0.0D);
        tessellator.addVertex((double) x, (double) top, 0.0D);
        tessellator.addVertex((double) (x + 1), (double) top, 0.0D);
        tessellator.draw();
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_ALPHA_TEST);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
    }

    /**
     * Clamps a value to a range.
     *
     * @param value input value
     * @param min minimum value
     * @param max maximum value
     * @return clamped value
     */
    public static int clamp(int value, int min, int max) {
        if (value < min) {
            return min;
        }
        if (value > max) {
            return max;
        }
        return value;
    }

    public static void beginScissor(int left, int top, int right, int bottom, int screenWidth, int screenHeight, int displayWidth, int displayHeight) {
        int scissorX = left * displayWidth / screenWidth;
        int scissorY = (screenHeight - bottom) * displayHeight / screenHeight;
        int scissorW = (right - left) * displayWidth / screenWidth;
        int scissorH = (bottom - top) * displayHeight / screenHeight;
        // Scissor uses display-space coordinates with origin at the bottom-left.
        if (scissorW < 0) {
            scissorW = 0;
        }
        if (scissorH < 0) {
            scissorH = 0;
        }
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(scissorX, scissorY, scissorW, scissorH);
    }

    public static void endScissor() {
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }

    /**
     * Attempts to open a file or folder using the native file explorer.
     *
     * @param path file or directory to open
     * @return true when the open command was dispatched
     */
    public static boolean openInFileExplorer(File path) {
        if (path == null) {
            return false;
        }
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop desktop = Desktop.getDesktop();
                if (desktop.isSupported(Desktop.Action.OPEN)) {
                    desktop.open(path);
                    return true;
                }
            }
        } catch (Throwable t) {
            // Fall through to shell-based open.
        }
        try {
            String os = System.getProperty("os.name");
            if (os != null) {
                os = os.toLowerCase();
            }
            String resolved = path.getAbsolutePath();
            if (os != null && os.indexOf("win") >= 0) {
                Runtime.getRuntime().exec(new String[] { "explorer", resolved });
            } else if (os != null && os.indexOf("mac") >= 0) {
                Runtime.getRuntime().exec(new String[] { "open", resolved });
            } else {
                Runtime.getRuntime().exec(new String[] { "xdg-open", resolved });
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
