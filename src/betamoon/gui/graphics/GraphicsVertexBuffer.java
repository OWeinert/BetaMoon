package betamoon.gui.graphics;

import java.nio.FloatBuffer;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GLContext;

/**
 * Prepared immutable xy or xyuv batch. Upload/disposal belong to the host's
 * native resource lifecycle.
 */
public final class GraphicsVertexBuffer implements AutoCloseable {
    private final int mode;
    private final int stride;
    private final int vertices;
    private int buffer;

    public GraphicsVertexBuffer(int mode, int stride, float[] data) {
        int primitive = mode == GL11.GL_TRIANGLES ? 3 : mode == GL11.GL_LINES ? 2 : mode == GL11.GL_QUADS ? 4 : 0;
        if (!GLContext.getCapabilities().OpenGL15 || primitive == 0 || !(stride == 2 || stride == 4) || data == null
                || data.length == 0 || data.length > 65536 || data.length % (stride * primitive) != 0) {
            throw new IllegalArgumentException(
                    "Vertex buffer requires GL 1.5, xy/xyuv complete primitives and bounded data");
        }
        for (float value : data) {
            if (!Float.isFinite(value) || Math.abs(value) > 1e6) {
                throw new IllegalArgumentException("Invalid vertex buffer coordinate");
            }
        }
        this.mode = mode;
        this.stride = stride;
        vertices = data.length / stride;
        FloatBuffer bytes = BufferUtils.createFloatBuffer(data.length);
        bytes.put(data).flip();
        int previous = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
        int candidate = GL15.glGenBuffers();
        try {
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, candidate);
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, bytes, GL15.GL_STATIC_DRAW);
            buffer = candidate;
            candidate = 0;
        } finally {
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, previous);
            if (candidate != 0) {
                GL15.glDeleteBuffers(candidate);
            }
        }
    }

    public void draw() {
        if (buffer == 0) {
            throw new IllegalStateException("Prepared vertex buffer has been disposed");
        }
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, buffer);
        GL11.glEnableClientState(GL11.GL_VERTEX_ARRAY);
        GL11.glVertexPointer(2, GL11.GL_FLOAT, stride * 4, 0L);
        if (GLContext.getCapabilities().OpenGL13) {
            GL13.glClientActiveTexture(GL13.GL_TEXTURE0);
        }
        if (stride == 4) {
            GL11.glEnableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
            GL11.glTexCoordPointer(2, GL11.GL_FLOAT, stride * 4, 8L);
        } else {
            GL11.glDisableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
        }
        GL11.glDrawArrays(mode, 0, vertices);
    }

    @Override
    public void close() {
        if (buffer != 0) {
            GL15.glDeleteBuffers(buffer);
            buffer = 0;
        }
    }
}
