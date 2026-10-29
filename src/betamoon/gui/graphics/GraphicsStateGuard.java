package betamoon.gui.graphics;

import java.nio.IntBuffer;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

/**
 * Fixed-function GL state boundary; cannot undo resource deletion or
 * framebuffer writes.
 */
public final class GraphicsStateGuard implements AutoCloseable {
    private static final ThreadLocal<IntBuffer> SCISSOR = ThreadLocal
            .withInitial(() -> BufferUtils.createIntBuffer(16));

    private final int matrixMode;
    private boolean active = true;

    private GraphicsStateGuard() {
        matrixMode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glPushClientAttrib(GL11.GL_CLIENT_PIXEL_STORE_BIT | GL11.GL_CLIENT_VERTEX_ARRAY_BIT);
        pushMatrix(GL11.GL_PROJECTION);
        pushMatrix(GL11.GL_TEXTURE);
        pushMatrix(GL11.GL_MODELVIEW);
        GL11.glMatrixMode(matrixMode);
    }

    public static GraphicsStateGuard save() {
        return new GraphicsStateGuard();
    }

    public static AutoCloseable enter(GraphicsTransform transform, GraphicsClip.Pixels clip) {
        GraphicsStateGuard guard = save();
        try {
            int x = clip.x;
            int y = clip.y;
            int right = x + clip.width;
            int top = y + clip.height;
            if (GL11.glIsEnabled(GL11.GL_SCISSOR_TEST)) {
                IntBuffer parent = SCISSOR.get();
                parent.clear();
                GL11.glGetInteger(GL11.GL_SCISSOR_BOX, parent);
                x = Math.max(x, parent.get(0));
                y = Math.max(y, parent.get(1));
                right = Math.min(right, parent.get(0) + parent.get(2));
                top = Math.min(top, parent.get(1) + parent.get(3));
            }
            GL11.glEnable(GL11.GL_SCISSOR_TEST);
            GL11.glScissor(x, y, Math.max(0, right - x), Math.max(0, top - y));
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glColor4f(1, 1, 1, 1);
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glTranslated(transform.x, transform.y, 0);
            GL11.glScaled(transform.scaleX, transform.scaleY, 1);
            return guard;
        } catch (RuntimeException | Error failure) {
            guard.close();
            throw failure;
        }
    }

    @Override
    public void close() {
        if (!active) {
            return;
        }
        active = false;
        popMatrix(GL11.GL_MODELVIEW);
        popMatrix(GL11.GL_TEXTURE);
        popMatrix(GL11.GL_PROJECTION);
        GL11.glPopClientAttrib();
        GL11.glPopAttrib();
        GL11.glMatrixMode(matrixMode);
    }

    private static void pushMatrix(int mode) {
        GL11.glMatrixMode(mode);
        GL11.glPushMatrix();
    }

    private static void popMatrix(int mode) {
        GL11.glMatrixMode(mode);
        GL11.glPopMatrix();
    }
}
