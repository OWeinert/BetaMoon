package betamoon.gui;

import betamoon.gui.graphics.GraphicsClip;
import betamoon.gui.graphics.GraphicsStateGuard;
import betamoon.gui.graphics.GraphicsRawStateGuard;
import betamoon.gui.graphics.GraphicsRenderScope;
import betamoon.gui.graphics.GraphicsTransform;
import java.nio.FloatBuffer;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.ContextCapabilities;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GLContext;

/**
 * Real driver state checks; authority tests alone cannot establish GL
 * restoration.
 */
final class GuiRawGraphicsRenderChecks {
    static void run() {
        ContextCapabilities capabilities = GLContext.getCapabilities();
        require(capabilities.OpenGL20 && capabilities.OpenGL30,
                "Raw restoration fixture requires a GL 3 compatibility pbuffer");
        int program = program();
        int array = GL15.glGenBuffers();
        int elements = GL15.glGenBuffers();
        int framebuffer = GL30.glGenFramebuffers();
        int renderbuffer = GL30.glGenRenderbuffers();
        try {
            GL20.glUseProgram(program);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, array);
            GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, elements);
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebuffer);
            GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, renderbuffer);
            GL13.glActiveTexture(GL13.GL_TEXTURE1);
            GL11.glMatrixMode(GL11.GL_TEXTURE);
            GL11.glLoadIdentity();
            GL11.glTranslated(0.25, 0.5, 0);
            float[] textureOne = matrix();
            GL13.glActiveTexture(GL13.GL_TEXTURE2);
            GL11.glLoadIdentity();
            GL11.glScaled(2, 3, 1);
            float[] textureTwo = matrix();
            try {
                try (GraphicsRenderScope scope = GraphicsRenderScope.open(() -> {
                }, GraphicsRawStateGuard::enter, GraphicsTransform.IDENTITY,
                        new GraphicsClip(0, 0, 320, 240).pixels(320, 240, 320, 240))) {
                    require(GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM) == 0
                            && GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE) == GL13.GL_TEXTURE0,
                            "Raw incoming state did not establish the compatibility drawing profile");
                    GL13.glActiveTexture(GL13.GL_TEXTURE1);
                    GL11.glMatrixMode(GL11.GL_TEXTURE);
                    GL11.glLoadIdentity();
                    GL13.glActiveTexture(GL13.GL_TEXTURE2);
                    GL11.glLoadIdentity();
                    GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
                    GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, 0);
                    GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
                    GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, 0);
                    throw new IllegalArgumentException("raw failure fixture");
                }
            } catch (IllegalArgumentException expected) {
                require("raw failure fixture".equals(expected.getMessage()), "Unexpected raw setup failure");
            }
            require(GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM) == program, "Raw program binding leaked");
            require(GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING) == array
                    && GL11.glGetInteger(GL15.GL_ELEMENT_ARRAY_BUFFER_BINDING) == elements,
                    "Raw buffer binding leaked");
            require(GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING) == framebuffer
                    && GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING) == framebuffer
                    && GL11.glGetInteger(GL30.GL_RENDERBUFFER_BINDING) == renderbuffer,
                    "Raw framebuffer bindings leaked");
            require(GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE) == GL13.GL_TEXTURE2
                    && GL11.glGetInteger(GL11.GL_MATRIX_MODE) == GL11.GL_TEXTURE,
                    "Raw active texture/matrix mode leaked");
            same(textureTwo, matrix());
            GL13.glActiveTexture(GL13.GL_TEXTURE1);
            same(textureOne, matrix());
            nestedMatrices(8);
            same(textureOne, matrix());
            require(GL11.glGetError() == GL11.GL_NO_ERROR, "Raw state guard generated a GL error");
        } finally {
            GL20.glUseProgram(0);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
            GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, 0);
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
            GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, 0);
            GL13.glActiveTexture(GL13.GL_TEXTURE1);
            GL11.glMatrixMode(GL11.GL_TEXTURE);
            GL11.glLoadIdentity();
            GL13.glActiveTexture(GL13.GL_TEXTURE2);
            GL11.glLoadIdentity();
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL20.glDeleteProgram(program);
            GL15.glDeleteBuffers(array);
            GL15.glDeleteBuffers(elements);
            GL30.glDeleteFramebuffers(framebuffer);
            GL30.glDeleteRenderbuffers(renderbuffer);
        }
    }

    private static void nestedMatrices(int remaining) {
        if (remaining == 0) {
            return;
        }
        try (GraphicsStateGuard guard = GraphicsStateGuard.save()) {
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glTranslated(remaining, 0, 0);
            GL11.glMatrixMode(GL11.GL_TEXTURE);
            GL11.glScaled(remaining, 1, 1);
            nestedMatrices(remaining - 1);
        }
        require(GL11.glGetError() == GL11.GL_NO_ERROR, "Nested graphics guard exhausted a matrix stack");
    }

    private static int program() {
        int vertex = shader(GL20.GL_VERTEX_SHADER,
                "void main(){ gl_Position=gl_ModelViewProjectionMatrix*gl_Vertex; }");
        int fragment = shader(GL20.GL_FRAGMENT_SHADER, "void main(){ gl_FragColor=vec4(1.0); }");
        int program = GL20.glCreateProgram();
        GL20.glAttachShader(program, vertex);
        GL20.glAttachShader(program, fragment);
        GL20.glLinkProgram(program);
        GL20.glDeleteShader(vertex);
        GL20.glDeleteShader(fragment);
        require(GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) != 0, "Fixture shader link failed");
        return program;
    }

    private static int shader(int kind, String source) {
        int shader = GL20.glCreateShader(kind);
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);
        require(GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) != 0, "Fixture shader compile failed");
        return shader;
    }

    private static float[] matrix() {
        FloatBuffer values = BufferUtils.createFloatBuffer(16);
        GL11.glGetFloat(GL11.GL_TEXTURE_MATRIX, values);
        float[] result = new float[16];
        values.get(result);
        return result;
    }

    private static void same(float[] expected, float[] actual) {
        for (int index = 0; index < expected.length; index++) {
            require(Math.abs(expected[index] - actual[index]) < 0.0001, "Raw texture matrix changed");
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
