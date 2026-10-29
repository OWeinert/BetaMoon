package betamoon.gui.style;

import betamoon.gui.framework.GuiGeometry.Rect;
import net.minecraft.src.Tessellator;
import org.lwjgl.opengl.GL11;

/**
 * Native atlas skin with tiled width and independently stretched height. Has no
 * control behavior.
 */
public final class GuiNativeButtonSkin {
    public static final String TEXTURE = "/gui/gui.png";

    public static void draw(int texture, Rect bounds, boolean enabled, boolean hovered, int alpha) {
        draw(texture, bounds, enabled, hovered, alpha, false);
    }

    /**
     * Compatibility adapters retain native atlas cropping at nonstandard heights.
     */
    public static void drawLegacy(int texture, Rect bounds, boolean enabled, boolean hovered) {
        draw(texture, bounds, enabled, hovered, 255, true);
    }

    private static void draw(int texture, Rect bounds, boolean enabled, boolean hovered, int alpha,
            boolean legacyHeight) {
        if (texture < 0 || bounds.isEmpty() || alpha <= 0) {
            return;
        }
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT | GL11.GL_TEXTURE_BIT | GL11.GL_COLOR_BUFFER_BIT);
        try {
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
            GL11.glColor4f(1, 1, 1, Math.min(255, alpha) / 255F);
            int textureY = 46 + (enabled ? hovered ? 2 : 1 : 0) * 20;
            int edgeX = Math.min(2, bounds.getWidth() / 2);
            int edgeY = legacyHeight ? -1 : Math.min(2, bounds.getHeight() / 2);
            Tessellator batch = Tessellator.instance;
            batch.startDrawingQuads();
            strip(batch, bounds.getLeft(), bounds.getTop(), edgeX, bounds.getHeight(), 0, textureY, edgeX, edgeY);
            for (int offset = edgeX; offset < bounds.getWidth() - edgeX;) {
                int span = Math.min(196, bounds.getWidth() - edgeX - offset);
                strip(batch, bounds.getLeft() + offset, bounds.getTop(), span, bounds.getHeight(), 2, textureY, span,
                        edgeY);
                offset += span;
            }
            strip(batch, bounds.getRight() - edgeX, bounds.getTop(), edgeX, bounds.getHeight(), 200 - edgeX, textureY,
                    edgeX, edgeY);
            batch.draw();
        } finally {
            GL11.glPopAttrib();
        }
    }

    private static void strip(Tessellator batch, int x, int y, int width, int height, int u, int v, int span,
            int edge) {
        if (edge < 0) {
            quad(batch, x, y, width, height, u, v, span, height);
            return;
        }
        quad(batch, x, y, width, edge, u, v, span, edge);
        quad(batch, x, y + edge, width, height - edge * 2, u, v + 2, span, 16);
        quad(batch, x, y + height - edge, width, edge, u, v + 20 - edge, span, edge);
    }

    private static void quad(Tessellator batch, int x, int y, int width, int height, int u, int v, int span, int rows) {
        if (width <= 0 || height <= 0) {
            return;
        }
        batch.addVertexWithUV(x, y + height, 0, u / 256D, (v + rows) / 256D);
        batch.addVertexWithUV(x + width, y + height, 0, (u + span) / 256D, (v + rows) / 256D);
        batch.addVertexWithUV(x + width, y, 0, (u + span) / 256D, v / 256D);
        batch.addVertexWithUV(x, y, 0, u / 256D, v / 256D);
    }

    private GuiNativeButtonSkin() {
    }
}
