package betamoon.gui.graphics;

import net.minecraft.src.Tessellator;
import org.lwjgl.opengl.GL11;

/**
 * Detached bounded vertex batch. No Lua vertex calls, generic reflection or
 * per-paint buffer construction.
 */
public final class GraphicsMesh {
    private final float[] vertices;
    private final int mode;

    public GraphicsMesh(int mode, float[] vertices) {
        int primitive = mode == GL11.GL_TRIANGLES ? 3 : mode == GL11.GL_LINES ? 2 : mode == GL11.GL_QUADS ? 4 : 0;
        if (primitive == 0 || vertices == null || vertices.length == 0 || vertices.length > 32768
                || vertices.length % (primitive * 2) != 0) {
            throw new IllegalArgumentException(
                    "Mesh requires complete triangle/line/quad primitives and at most 16384 vertices");
        }
        for (float value : vertices) {
            if (!Float.isFinite(value) || Math.abs(value) > 1e6) {
                throw new IllegalArgumentException("Mesh coordinates exceed finite geometry bounds");
            }
        }
        this.mode = mode;
        this.vertices = vertices.clone();
    }

    public int vertices() {
        return vertices.length / 2;
    }

    public void draw(int color, double opacity) {
        color(color, opacity);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        Tessellator batch = Tessellator.instance;
        batch.startDrawing(mode);
        for (int index = 0; index < vertices.length; index += 2) {
            batch.addVertex(vertices[index], vertices[index + 1], 0);
        }
        batch.draw();
    }

    public static void color(int color, double opacity) {
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glColor4f((color >> 16 & 255) / 255F, (color >> 8 & 255) / 255F, (color & 255) / 255F,
                (float) ((color >>> 24) / 255D * Math.max(0, Math.min(1, opacity))));
    }
}
