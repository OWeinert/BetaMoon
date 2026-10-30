package betamoon.gui.graphics;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayDeque;
import java.util.Deque;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.ARBMultitexture;
import org.lwjgl.opengl.ContextCapabilities;
import org.lwjgl.opengl.GLContext;

/**
 * Fixed-function GL state boundary; cannot undo resource deletion or
 * framebuffer writes.
 */
public final class GraphicsStateGuard implements AutoCloseable {
    private static final ThreadLocal<IntBuffer> SCISSOR = ThreadLocal
            .withInitial(() -> BufferUtils.createIntBuffer(16));

    private static final ThreadLocal<Deque<FloatBuffer>> MATRICES = ThreadLocal.withInitial(ArrayDeque::new);
    private final int matrixMode;
    private final ContextCapabilities capabilities;
    private final int activeTexture;
    private FloatBuffer matrices;
    private boolean active = true;

    private GraphicsStateGuard() {
        capabilities = GLContext.getCapabilities();
        matrixMode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        activeTexture = capabilities.OpenGL13 || capabilities.GL_ARB_multitexture
                ? GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE)
                : GL13.GL_TEXTURE0;
        FloatBuffer pooled = MATRICES.get().pollFirst();
        matrices = pooled == null ? BufferUtils.createFloatBuffer(48) : pooled;
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glPushClientAttrib(GL11.GL_CLIENT_PIXEL_STORE_BIT | GL11.GL_CLIENT_VERTEX_ARRAY_BIT);
        activate(GL13.GL_TEXTURE0);
        capture(GL11.GL_MODELVIEW_MATRIX, 0);
        capture(GL11.GL_PROJECTION_MATRIX, 16);
        capture(GL11.GL_TEXTURE_MATRIX, 32);
        activate(activeTexture);
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
        try {
            activate(GL13.GL_TEXTURE0);
            restore(GL11.GL_MODELVIEW, 0);
            restore(GL11.GL_PROJECTION, 16);
            restore(GL11.GL_TEXTURE, 32);
            GL11.glPopClientAttrib();
            GL11.glPopAttrib();
            activate(activeTexture);
            GL11.glMatrixMode(matrixMode);
        } finally {
            matrices.clear();
            MATRICES.get().addFirst(matrices);
            matrices = null;
        }
    }

    private void capture(int matrix, int offset) {
        matrices.position(offset);
        GL11.glGetFloat(matrix, matrices);
    }

    private void restore(int mode, int offset) {
        GL11.glMatrixMode(mode);
        matrices.position(offset);
        GL11.glLoadMatrix(matrices);
    }

    private void activate(int texture) {
        if (capabilities.OpenGL13) {
            GL13.glActiveTexture(texture);
        } else if (capabilities.GL_ARB_multitexture) {
            ARBMultitexture.glActiveTextureARB(texture);
        }
    }
}
