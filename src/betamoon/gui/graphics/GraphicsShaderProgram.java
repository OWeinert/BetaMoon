package betamoon.gui.graphics;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.nio.FloatBuffer;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GLContext;

/**
 * Prepared GLSL compatibility program. Hosts own it through
 * NativeClientSession.resource.
 */
public final class GraphicsShaderProgram implements AutoCloseable {
    private static final ThreadLocal<FloatBuffer> MATRIX = ThreadLocal
            .withInitial(() -> BufferUtils.createFloatBuffer(16));
    private final Map<String, Uniform> uniforms;
    private int program;

    public GraphicsShaderProgram(String vertex, String fragment, Map<String, Integer> declarations) {
        if (!GLContext.getCapabilities().OpenGL20 || vertex == null || fragment == null || vertex.length() > 262144
                || fragment.length() > 262144 || declarations == null || declarations.size() > 64) {
            throw new IllegalArgumentException(
                    "Shader requires GL 2, bounded sources and at most 64 declared uniforms");
        }
        for (Map.Entry<String, Integer> entry : declarations.entrySet()) {
            if (entry.getKey() == null || !entry.getKey().matches("[A-Za-z_][A-Za-z0-9_.\\[\\]]{0,127}")
                    || entry.getValue() == null || entry.getValue() != -1 && entry.getValue() != 16
                            && (entry.getValue() < 1 || entry.getValue() > 4)) {
                throw new IllegalArgumentException(
                        "Uniform declarations require names and float vector sizes 1..4, matrix 16 or integer -1");
            }
        }
        int vertexShader = 0;
        int fragmentShader = 0;
        int candidate = 0;
        try {
            vertexShader = compile(GL20.GL_VERTEX_SHADER, vertex);
            fragmentShader = compile(GL20.GL_FRAGMENT_SHADER, fragment);
            candidate = GL20.glCreateProgram();
            GL20.glAttachShader(candidate, vertexShader);
            GL20.glAttachShader(candidate, fragmentShader);
            GL20.glLinkProgram(candidate);
            if (GL20.glGetProgrami(candidate, GL20.GL_LINK_STATUS) == 0) {
                throw new IllegalArgumentException("Shader link failed: " + GL20.glGetProgramInfoLog(candidate, 8192));
            }
            Map<String, Uniform> prepared = new LinkedHashMap<>();
            for (Map.Entry<String, Integer> entry : declarations.entrySet()) {
                int location = GL20.glGetUniformLocation(candidate, entry.getKey());
                if (location < 0) {
                    throw new IllegalArgumentException("Shader uniform is absent or inactive: " + entry.getKey());
                }
                prepared.put(entry.getKey(), new Uniform(location, entry.getValue()));
            }
            uniforms = Collections.unmodifiableMap(prepared);
            program = candidate;
            candidate = 0;
        } finally {
            if (vertexShader != 0) {
                GL20.glDeleteShader(vertexShader);
            }
            if (fragmentShader != 0) {
                GL20.glDeleteShader(fragmentShader);
            }
            if (candidate != 0) {
                GL20.glDeleteProgram(candidate);
            }
        }
    }

    public void use() {
        requireActive();
        GL20.glUseProgram(program);
    }

    public void uniform(String name, double[] values) {
        requireActive();
        Uniform uniform = uniforms.get(name);
        if (uniform == null || values == null || values.length != uniform.size) {
            throw new IllegalArgumentException("Uniform requires its declared vector size: " + name);
        }
        for (double value : values) {
            if (!Double.isFinite(value) || Math.abs(value) > 1e9) {
                throw new IllegalArgumentException("Uniform values require finite numbers within +/-1000000000");
            }
        }
        switch (values.length) {
            case 1:
                GL20.glUniform1f(uniform.location, (float) values[0]);
                break;
            case 2:
                GL20.glUniform2f(uniform.location, (float) values[0], (float) values[1]);
                break;
            case 3:
                GL20.glUniform3f(uniform.location, (float) values[0], (float) values[1], (float) values[2]);
                break;
            case 4:
                GL20.glUniform4f(uniform.location, (float) values[0], (float) values[1], (float) values[2],
                        (float) values[3]);
                break;
            default:
                throw new IllegalStateException("Unknown prepared uniform size");
        }
    }

    public void requireActive() {
        if (program == 0) {
            throw new IllegalStateException("Prepared shader program has been disposed");
        }
    }

    public void uniformInteger(String name, int value) {
        requireActive();
        Uniform uniform = uniforms.get(name);
        if (uniform == null || uniform.size != -1) {
            throw new IllegalArgumentException("Uniform requires a declared integer/sampler: " + name);
        }
        GL20.glUniform1i(uniform.location, value);
    }

    public void uniformMatrix(String name, double[] values) {
        requireActive();
        Uniform uniform = uniforms.get(name);
        if (uniform == null || uniform.size != 16 || values == null || values.length != 16) {
            throw new IllegalArgumentException("Uniform requires a declared 4x4 matrix: " + name);
        }
        FloatBuffer matrix = MATRIX.get();
        matrix.clear();
        for (double value : values) {
            if (!Double.isFinite(value) || Math.abs(value) > 1e9) {
                throw new IllegalArgumentException("Invalid uniform matrix value");
            }
            matrix.put((float) value);
        }
        matrix.flip();
        GL20.glUniformMatrix4(uniform.location, false, matrix);
    }

    @Override
    public void close() {
        if (program != 0) {
            if (GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM) == program) {
                GL20.glUseProgram(0);
            }
            GL20.glDeleteProgram(program);
            program = 0;
        }
    }

    private static int compile(int kind, String source) {
        int shader = GL20.glCreateShader(kind);
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == 0) {
            String reason = GL20.glGetShaderInfoLog(shader, 8192);
            GL20.glDeleteShader(shader);
            throw new IllegalArgumentException("Shader compile failed: " + reason);
        }
        return shader;
    }

    private static final class Uniform {
        final int location;
        final int size;
        Uniform(int location, int size) {
            this.location = location;
            this.size = size;
        }
    }
}
