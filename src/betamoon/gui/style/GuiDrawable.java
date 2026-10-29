package betamoon.gui.style;

import betamoon.gui.framework.GuiContext;
import betamoon.gui.framework.GuiGeometry.Rect;
import net.minecraft.src.Tessellator;
import org.lwjgl.opengl.GL11;

/**
 * Immutable solid/image/nine-slice visual over a host-prepared atlas lease.
 * Never loads or owns a texture.
 */
public final class GuiDrawable {
    private final GuiAtlasLease atlas;
    private final int color;
    private final int x;
    private final int y;
    private final int width;
    private final int height;
    private final int border;

    private GuiDrawable(GuiAtlasLease atlas, int color, int x, int y, int width, int height, int border) {
        this.atlas = atlas;
        this.color = color;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.border = border;
    }

    public static GuiDrawable solid(int color) {
        return new GuiDrawable(null, color, 0, 0, 0, 0, 0);
    }

    public static GuiDrawable image(GuiAtlasLease atlas, int x, int y, int width, int height, int border) {
        if (atlas == null || x < 0 || y < 0 || width < 1 || height < 1 || (long) x + width > atlas.width()
                || (long) y + height > atlas.height() || border < 0 || border * 2L >= Math.min(width, height)) {
            throw new IllegalArgumentException("Invalid prepared drawable bounds or nine-slice border");
        }
        atlas.requireActive();
        return new GuiDrawable(atlas, 0xFFFFFFFF, x, y, width, height, border);
    }

    public void requireActive() {
        if (atlas != null) {
            atlas.requireActive();
        }
    }

    public void draw(GuiContext context, Rect bounds) {
        requireActive();
        int alpha = (int) Math.round((color >>> 24) * context.getOpacity());
        if (atlas == null) {
            context.getRenderer().drawRect(bounds, color & 0xFFFFFF | alpha << 24);
            return;
        }
        if (bounds.isEmpty() || alpha <= 0) {
            return;
        }
        GL11.glPushAttrib(GL11.GL_TEXTURE_BIT | GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_CURRENT_BIT);
        try {
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, atlas.textureId());
            GL11.glColor4f(1, 1, 1, alpha / 255F);
            Tessellator batch = Tessellator.instance;
            batch.startDrawingQuads();
            if (border == 0) {
                part(batch, bounds.getLeft(), bounds.getTop(), bounds.getRight(), bounds.getBottom(), x, y, x + width,
                        y + height);
            } else {
                int edgeX = Math.min(border, bounds.getWidth() / 2);
                int edgeY = Math.min(border, bounds.getHeight() / 2);
                for (int row = 0; row < 3; row++) {
                    int top = boundary(row, bounds.getTop(), bounds.getHeight(), edgeY);
                    int bottom = boundary(row + 1, bounds.getTop(), bounds.getHeight(), edgeY);
                    int sourceTop = boundary(row, y, height, border);
                    int sourceBottom = boundary(row + 1, y, height, border);
                    for (int column = 0; column < 3; column++) {
                        part(batch, boundary(column, bounds.getLeft(), bounds.getWidth(), edgeX), top,
                                boundary(column + 1, bounds.getLeft(), bounds.getWidth(), edgeX), bottom,
                                boundary(column, x, width, border), sourceTop, boundary(column + 1, x, width, border),
                                sourceBottom);
                    }
                }
            }
            batch.draw();
        } finally {
            GL11.glPopAttrib();
        }
    }

    private static int boundary(int index, int start, int size, int edge) {
        return index == 0 ? start : index == 1 ? start + edge : index == 2 ? start + size - edge : start + size;
    }

    private void part(Tessellator batch, int left, int top, int right, int bottom, int u, int v, int maxU, int maxV) {
        if (right <= left || bottom <= top) {
            return;
        }
        double atlasWidth = atlas.width();
        double atlasHeight = atlas.height();
        batch.addVertexWithUV(left, bottom, 0, u / atlasWidth, maxV / atlasHeight);
        batch.addVertexWithUV(right, bottom, 0, maxU / atlasWidth, maxV / atlasHeight);
        batch.addVertexWithUV(right, top, 0, maxU / atlasWidth, v / atlasHeight);
        batch.addVertexWithUV(left, top, 0, u / atlasWidth, v / atlasHeight);
    }

}
