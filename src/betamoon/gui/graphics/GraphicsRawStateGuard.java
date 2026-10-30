package betamoon.gui.graphics;

import java.nio.FloatBuffer;
import java.util.ArrayDeque;
import java.util.Deque;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.ARBFramebufferObject;
import org.lwjgl.opengl.ARBMultitexture;
import org.lwjgl.opengl.ContextCapabilities;
import org.lwjgl.opengl.EXTFramebufferObject;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL31;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GLContext;

/**
 * Additional guard for the supported raw compatibility-GL profile. Does not
 * undo object/content mutations.
 */
public final class GraphicsRawStateGuard implements AutoCloseable {
    private static final ThreadLocal<Deque<FloatBuffer>> MATRICES = ThreadLocal.withInitial(ArrayDeque::new);
    private final ContextCapabilities capabilities;
    private final int activeTexture;
    private final int textureUnits;
    private final int program;
    private final int arrayBuffer;
    private final int elementBuffer;
    private final int drawFramebuffer;
    private final int readFramebuffer;
    private final int renderbuffer;
    private final int framebufferMode;
    private final AutoCloseable fixedState;
    private FloatBuffer textureMatrices;
    private boolean active = true;

    private GraphicsRawStateGuard(GraphicsTransform transform, GraphicsClip.Pixels clip) {
        capabilities = GLContext.getCapabilities();
        boolean multitexture = capabilities.OpenGL13 || capabilities.GL_ARB_multitexture;
        activeTexture = multitexture ? GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE) : GL13.GL_TEXTURE0;
        textureUnits = multitexture
                ? GL11.glGetInteger(capabilities.OpenGL20 ? GL20.GL_MAX_TEXTURE_COORDS : GL13.GL_MAX_TEXTURE_UNITS)
                : 1;
        if (textureUnits < 1 || textureUnits > 256) {
            throw new IllegalStateException("Unsupported fixed-function texture-unit count");
        }
        program = capabilities.OpenGL20 ? GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM) : 0;
        arrayBuffer = capabilities.OpenGL15 ? GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING) : 0;
        elementBuffer = capabilities.OpenGL15 ? GL11.glGetInteger(GL15.GL_ELEMENT_ARRAY_BUFFER_BINDING) : 0;
        framebufferMode = capabilities.OpenGL30 || capabilities.GL_ARB_framebuffer_object
                ? 2
                : capabilities.GL_EXT_framebuffer_object ? 1 : 0;
        drawFramebuffer = framebufferMode == 0 ? 0 : GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        readFramebuffer = framebufferMode == 2 ? GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING) : drawFramebuffer;
        renderbuffer = framebufferMode == 0 ? 0 : GL11.glGetInteger(GL30.GL_RENDERBUFFER_BINDING);
        activate(0);
        try {
            fixedState = GraphicsStateGuard.enter(transform, clip);
        } catch (RuntimeException | Error failure) {
            activate(activeTexture - GL13.GL_TEXTURE0);
            throw failure;
        }
        FloatBuffer pooled = MATRICES.get().pollFirst();
        textureMatrices = pooled != null && pooled.capacity() >= textureUnits * 16
                ? pooled
                : BufferUtils.createFloatBuffer(textureUnits * 16);
        int captured = 0;
        try {
            GL11.glMatrixMode(GL11.GL_TEXTURE);
            for (int unit = 0; unit < textureUnits; unit++) {
                activate(unit);
                textureMatrices.position(unit * 16);
                GL11.glGetFloat(GL11.GL_TEXTURE_MATRIX, textureMatrices);
                captured++;
                GL11.glLoadIdentity();
                GL11.glDisable(GL11.GL_TEXTURE_1D);
                if (unit > 0) {
                    GL11.glDisable(GL11.GL_TEXTURE_2D);
                }
                if (capabilities.OpenGL12) {
                    GL11.glDisable(GL12.GL_TEXTURE_3D);
                }
                if (capabilities.OpenGL13 || capabilities.GL_ARB_texture_cube_map) {
                    GL11.glDisable(GL13.GL_TEXTURE_CUBE_MAP);
                }
                if (capabilities.OpenGL31 || capabilities.GL_ARB_texture_rectangle
                        || capabilities.GL_NV_texture_rectangle) {
                    GL11.glDisable(GL31.GL_TEXTURE_RECTANGLE);
                }
            }
            activate(0);
            GL11.glLoadIdentity();
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glDisableClientState(GL11.GL_COLOR_ARRAY);
            GL11.glDisableClientState(GL11.GL_NORMAL_ARRAY);
            for (int unit = 0; unit < textureUnits; unit++) {
                if (capabilities.OpenGL13) {
                    GL13.glClientActiveTexture(GL13.GL_TEXTURE0 + unit);
                } else if (capabilities.GL_ARB_multitexture) {
                    ARBMultitexture.glClientActiveTextureARB(GL13.GL_TEXTURE0 + unit);
                }
                GL11.glDisableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
            }
            if (capabilities.OpenGL13) {
                GL13.glClientActiveTexture(GL13.GL_TEXTURE0);
            } else if (capabilities.GL_ARB_multitexture) {
                ARBMultitexture.glClientActiveTextureARB(GL13.GL_TEXTURE0);
            }
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            if (capabilities.OpenGL20) {
                GL20.glUseProgram(0);
            }
            if (capabilities.OpenGL15) {
                GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
                GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, 0);
            }
        } catch (RuntimeException | Error failure) {
            try {
                restore(captured);
            } catch (RuntimeException | Error cleanup) {
                failure.addSuppressed(cleanup);
            }
            active = false;
            throw failure;
        }
    }

    public static AutoCloseable enter(GraphicsTransform transform, GraphicsClip.Pixels clip) {
        return new GraphicsRawStateGuard(transform, clip);
    }

    private void activate(int unit) {
        if (capabilities.OpenGL13) {
            GL13.glActiveTexture(GL13.GL_TEXTURE0 + unit);
        } else if (capabilities.GL_ARB_multitexture) {
            ARBMultitexture.glActiveTextureARB(GL13.GL_TEXTURE0 + unit);
        }
    }

    @Override
    public void close() {
        if (active) {
            active = false;
            restore(textureUnits);
        }
    }

    private void restore(int captured) {
        try {
            GL11.glMatrixMode(GL11.GL_TEXTURE);
            for (int unit = 0; unit < captured; unit++) {
                activate(unit);
                textureMatrices.position(unit * 16);
                GL11.glLoadMatrix(textureMatrices);
            }
            activate(0);
        } finally {
            try {
                try {
                    restoreFramebuffer();
                } finally {
                    fixedState.close();
                }
            } catch (RuntimeException | Error failure) {
                throw failure;
            } catch (Exception failure) {
                throw new IllegalStateException("Cannot restore fixed-function raw state", failure);
            } finally {
                try {
                    if (capabilities.OpenGL20) {
                        GL20.glUseProgram(program);
                    }
                    if (capabilities.OpenGL15) {
                        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, arrayBuffer);
                        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, elementBuffer);
                    }
                    activate(activeTexture - GL13.GL_TEXTURE0);
                } finally {
                    textureMatrices.clear();
                    MATRICES.get().addFirst(textureMatrices);
                    textureMatrices = null;
                }
            }
        }
    }

    private void restoreFramebuffer() {
        if (framebufferMode == 2) {
            if (capabilities.OpenGL30) {
                GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFramebuffer);
                GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFramebuffer);
                GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, renderbuffer);
            } else {
                ARBFramebufferObject.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFramebuffer);
                ARBFramebufferObject.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFramebuffer);
                ARBFramebufferObject.glBindRenderbuffer(GL30.GL_RENDERBUFFER, renderbuffer);
            }
        } else if (framebufferMode == 1) {
            EXTFramebufferObject.glBindFramebufferEXT(EXTFramebufferObject.GL_FRAMEBUFFER_EXT, drawFramebuffer);
            EXTFramebufferObject.glBindRenderbufferEXT(EXTFramebufferObject.GL_RENDERBUFFER_EXT, renderbuffer);
        }
    }
}
