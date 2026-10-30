package betamoon.luaapi.client.ui;

import betamoon.gui.graphics.GraphicsClip;
import betamoon.gui.graphics.GraphicsFramebuffer;
import betamoon.gui.graphics.GraphicsMesh;
import betamoon.gui.graphics.GraphicsRenderScope;
import betamoon.gui.graphics.GraphicsResources;
import betamoon.gui.graphics.GraphicsShaderProgram;
import betamoon.gui.style.GuiAtlasLease;
import betamoon.luaapi.utils.LuaDeclarationValues;
import java.nio.IntBuffer;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.ARBMultitexture;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GLContext;

/**
 * Explicit pass-scoped direct bindings. Shared functions never capture a frame,
 * host or native resource.
 */
public final class GuiRawGraphicsAccess extends LuaTable implements AutoCloseable {
    private static final Map<LuaValue, LuaValue> METHODS = methods();
    private static final ThreadLocal<IntBuffer> VIEWPORT = ThreadLocal
            .withInitial(() -> BufferUtils.createIntBuffer(16));
    private final GraphicsRenderScope scope;
    private GraphicsResources resources;
    private GraphicsShaderProgram program;
    private final GraphicsFramebuffer.Binding target;
    private final GraphicsClip.Pixels clip;
    private final int viewportX;
    private final int viewportY;
    private final int viewportWidth;
    private final int viewportHeight;
    private final int textureUnits;
    private final int matrixCapacity;
    private GraphicsFramebuffer currentTarget;
    private int depth;
    private boolean active = true;

    public GuiRawGraphicsAccess(GraphicsRenderScope scope, GraphicsResources resources, GraphicsClip.Pixels clip) {
        this.scope = scope;
        this.resources = resources;
        this.clip = clip;
        scope.requireActive();
        target = GraphicsFramebuffer.Binding.capture();
        IntBuffer viewport = VIEWPORT.get();
        viewport.clear();
        GL11.glGetInteger(GL11.GL_VIEWPORT, viewport);
        viewportX = viewport.get(0);
        viewportY = viewport.get(1);
        viewportWidth = viewport.get(2);
        viewportHeight = viewport.get(3);
        textureUnits = GLContext.getCapabilities().OpenGL20
                ? GL11.glGetInteger(GL20.GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS)
                : GLContext.getCapabilities().OpenGL13 || GLContext.getCapabilities().GL_ARB_multitexture
                        ? GL11.glGetInteger(GL13.GL_MAX_TEXTURE_UNITS)
                        : 1;
        matrixCapacity = Math.min(16, GL11.glGetInteger(GL11.GL_MAX_MODELVIEW_STACK_DEPTH)
                - GL11.glGetInteger(GL11.GL_MODELVIEW_STACK_DEPTH));
        for (Map.Entry<LuaValue, LuaValue> entry : METHODS.entrySet()) {
            rawset(entry.getKey(), entry.getValue());
        }
    }

    private void requireActive() {
        if (!active) {
            throw new LuaError("Graphics facade expired when its raw callback returned");
        }
        scope.requireActive();
    }

    @Override
    public void close() {
        if (!active) {
            return;
        }
        scope.requireCleanup();
        int unbalanced = depth;
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        while (depth > 0) {
            GL11.glPopMatrix();
            depth--;
        }
        active = false;
        resources = null;
        program = null;
        currentTarget = null;
        if (unbalanced != 0) {
            throw new LuaError("Raw transform pushes must be balanced");
        }
    }

    private static Map<LuaValue, LuaValue> methods() {
        Map<LuaValue, LuaValue> methods = new LinkedHashMap<>();
        add(methods, "mesh", (access, arguments) -> {
            activateZero();
            access.resources.mesh(string(arguments, 2)).draw(color(arguments.arg(3)), 1);
        });
        add(methods, "buffer", (access, arguments) -> access.resources.buffer(string(arguments, 2)).draw());
        add(methods, "vertices", (access, arguments) -> {
            String mode = string(arguments, 2);
            int primitive = "triangles".equals(mode)
                    ? GL11.GL_TRIANGLES
                    : "lines".equals(mode) ? GL11.GL_LINES : "quads".equals(mode) ? GL11.GL_QUADS : -1;
            LuaValue list = arguments.arg(3);
            if (!list.istable() || list.length() > 32768 || list.checktable().keys().length != list.length()) {
                throw new LuaError("Raw vertex batch requires a bounded dense numeric list");
            }
            float[] vertices = new float[list.length()];
            for (int index = 0; index < vertices.length; index++) {
                vertices[index] = (float) number(list.get(index + 1));
            }
            activateZero();
            new GraphicsMesh(primitive, vertices).draw(color(arguments.arg(4)), 1);
        });
        add(methods, "color", (access, arguments) -> GraphicsMesh.color(color(arguments.arg(2)), 1));
        add(methods, "texture", (access, arguments) -> {
            int unit = integer(arguments.arg(2), 0, access.textureUnits - 1);
            if (GLContext.getCapabilities().OpenGL13) {
                GL13.glActiveTexture(GL13.GL_TEXTURE0 + unit);
            } else if (unit != 0) {
                throw new LuaError("Texture unit requires GL 1.3");
            }
            if (arguments.arg(3).isnil()) {
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
            } else {
                GuiAtlasLease image = access.resources.image(string(arguments, 3));
                image.requireActive();
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, image.textureId());
            }
        });
        add(methods, "program", (access, arguments) -> {
            if (!GLContext.getCapabilities().OpenGL20) {
                throw new LuaError("Shader programs require GL 2");
            }
            access.program = arguments.arg(2).isnil() ? null : access.resources.program(string(arguments, 2));
            if (access.program == null) {
                GL20.glUseProgram(0);
            } else {
                access.program.use();
            }
        });
        add(methods, "uniform", (access, arguments) -> {
            if (access.program == null || arguments.narg() < 3 || arguments.narg() > 6) {
                throw new LuaError("Use a prepared program and provide a uniform vector of size 1..4");
            }
            double[] values = new double[arguments.narg() - 2];
            for (int index = 0; index < values.length; index++) {
                values[index] = number(arguments.arg(index + 3));
            }
            access.program.uniform(string(arguments, 2), values);
        });
        add(methods, "uniformInt", (access, arguments) -> {
            if (access.program == null) {
                throw new LuaError("Use a prepared program before assigning a uniform");
            }
            access.program.uniformInteger(string(arguments, 2),
                    integer(arguments.arg(3), Integer.MIN_VALUE, Integer.MAX_VALUE));
        });
        add(methods, "uniformMatrix", (access, arguments) -> {
            if (access.program == null) {
                throw new LuaError("Use a prepared program before assigning a uniform");
            }
            LuaValue input = arguments.arg(3);
            if (LuaDeclarationValues.length(input, "uniform matrix") != 16) {
                throw new LuaError("Uniform matrix requires 16 column-major values");
            }
            double[] values = new double[16];
            for (int index = 0; index < values.length; index++) {
                values[index] = number(input.get(index + 1));
            }
            access.program.uniformMatrix(string(arguments, 2), values);
        });
        add(methods, "target", (access, arguments) -> {
            access.currentTarget = arguments.arg(2).isnil() ? null : access.resources.target(string(arguments, 2));
            if (access.currentTarget == null) {
                access.target.restore();
                GL11.glViewport(access.viewportX, access.viewportY, access.viewportWidth, access.viewportHeight);
                GL11.glScissor(access.clip.x, access.clip.y, access.clip.width, access.clip.height);
            } else {
                access.currentTarget.bind();
                GL11.glScissor(0, 0, access.currentTarget.width(), access.currentTarget.height());
            }
            GL11.glEnable(GL11.GL_SCISSOR_TEST);
        });
        add(methods, "clear", (access, arguments) -> {
            int color = color(arguments.arg(2));
            GL11.glClearColor((color >> 16 & 255) / 255F, (color >> 8 & 255) / 255F, (color & 255) / 255F,
                    (color >>> 24) / 255F);
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | (LuaDeclarationValues.bool(arguments.arg(3), "clear depth", false)
                    ? GL11.GL_DEPTH_BUFFER_BIT
                    : 0));
        });
        add(methods, "enable", (access, arguments) -> {
            int capability = capability(string(arguments, 2));
            if (capability == GL11.GL_TEXTURE_2D) {
                activateZero();
            }
            if (!arguments.arg(3).isboolean()) {
                throw new LuaError("Graphics enable requires a boolean");
            }
            if (arguments.arg(3).toboolean()) {
                GL11.glEnable(capability);
            } else {
                GL11.glDisable(capability);
            }
        });
        add(methods, "blend",
                (access, arguments) -> GL11.glBlendFunc(blend(string(arguments, 2)), blend(string(arguments, 3))));
        add(methods, "push", (access, arguments) -> {
            if (access.depth >= access.matrixCapacity) {
                throw new LuaError("Raw transform stack exceeds its available matrix capacity");
            }
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPushMatrix();
            access.depth++;
        });
        add(methods, "pop", (access, arguments) -> {
            if (access.depth == 0) {
                throw new LuaError("Raw transform stack underflow");
            }
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPopMatrix();
            access.depth--;
        });
        add(methods, "translate", (access, arguments) -> {
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glTranslated(number(arguments.arg(2)), number(arguments.arg(3)),
                    arguments.arg(4).isnil() ? 0 : number(arguments.arg(4)));
        });
        add(methods, "scale", (access, arguments) -> {
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glScaled(number(arguments.arg(2)), number(arguments.arg(3)),
                    arguments.arg(4).isnil() ? 1 : number(arguments.arg(4)));
        });
        add(methods, "rotate", (access, arguments) -> {
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glRotated(number(arguments.arg(2)), 0, 0, 1);
        });
        add(methods, "ortho", (access, arguments) -> {
            double left = number(arguments.arg(2));
            double right = number(arguments.arg(3));
            double bottom = number(arguments.arg(4));
            double top = number(arguments.arg(5));
            double near = arguments.arg(6).isnil() ? -1 : number(arguments.arg(6));
            double far = arguments.arg(7).isnil() ? 1 : number(arguments.arg(7));
            if (left == right || bottom == top || near == far) {
                throw new LuaError("Ortho bounds must have nonzero spans");
            }
            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glLoadIdentity();
            GL11.glOrtho(left, right, bottom, top, near, far);
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
        });
        return Collections.unmodifiableMap(methods);
    }

    private static void add(Map<LuaValue, LuaValue> methods, String name, Operation operation) {
        methods.put(LuaValue.valueOf(name), new VarArgFunction() {
            public Varargs invoke(Varargs arguments) {
                if (!(arguments.arg1() instanceof GuiRawGraphicsAccess)) {
                    throw new LuaError("Call graphics methods with their pass facade using ':'");
                }
                GuiRawGraphicsAccess access = (GuiRawGraphicsAccess) arguments.arg1();
                access.requireActive();
                try {
                    operation.run(access, arguments);
                } catch (IllegalArgumentException invalid) {
                    throw new LuaError("graphics." + name + ": " + invalid.getMessage());
                }
                return LuaValue.NIL;
            }
        });
    }

    @FunctionalInterface
    private interface Operation {
        void run(GuiRawGraphicsAccess access, Varargs arguments);
    }

    private static String string(Varargs arguments, int index) {
        return LuaDeclarationValues.string(arguments.arg(index), "graphics argument " + (index - 1));
    }

    private static double number(LuaValue value) {
        double result = LuaDeclarationValues.number(value, "graphics number");
        if (Math.abs(result) > 1e9) {
            throw new LuaError("Graphics numbers require values within +/-1000000000");
        }
        return result;
    }

    private static int integer(LuaValue value, int minimum, int maximum) {
        return LuaDeclarationValues.integer(value, "graphics integer", minimum, maximum);
    }

    private static int color(LuaValue value) {
        if (value.isnil()) {
            return 0xFFFFFFFF;
        }
        double color = LuaDeclarationValues.number(value, "graphics color");
        if (color != Math.rint(color) || color < Integer.MIN_VALUE || color > 0xFFFFFFFFL) {
            throw new LuaError("Graphics color requires an ARGB integer");
        }
        return (int) (long) color;
    }

    private static void activateZero() {
        if (GLContext.getCapabilities().OpenGL13) {
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
        } else if (GLContext.getCapabilities().GL_ARB_multitexture) {
            ARBMultitexture.glActiveTextureARB(GL13.GL_TEXTURE0);
        }
    }

    private static int capability(String name) {
        switch (name) {
            case "blend":
                return GL11.GL_BLEND;
            case "depth":
                return GL11.GL_DEPTH_TEST;
            case "lighting":
                return GL11.GL_LIGHTING;
            case "cull":
                return GL11.GL_CULL_FACE;
            case "texture":
                return GL11.GL_TEXTURE_2D;
            case "alpha":
                return GL11.GL_ALPHA_TEST;
            case "stencil":
                return GL11.GL_STENCIL_TEST;
            default:
                throw new LuaError("Unsupported raw capability: " + name);
        }
    }

    private static int blend(String name) {
        switch (name) {
            case "zero":
                return GL11.GL_ZERO;
            case "one":
                return GL11.GL_ONE;
            case "src_alpha":
                return GL11.GL_SRC_ALPHA;
            case "one_minus_src_alpha":
                return GL11.GL_ONE_MINUS_SRC_ALPHA;
            case "dst_alpha":
                return GL11.GL_DST_ALPHA;
            case "one_minus_dst_alpha":
                return GL11.GL_ONE_MINUS_DST_ALPHA;
            case "src_color":
                return GL11.GL_SRC_COLOR;
            case "one_minus_src_color":
                return GL11.GL_ONE_MINUS_SRC_COLOR;
            case "dst_color":
                return GL11.GL_DST_COLOR;
            case "one_minus_dst_color":
                return GL11.GL_ONE_MINUS_DST_COLOR;
            default:
                throw new LuaError("Unsupported raw blend factor: " + name);
        }
    }
}
