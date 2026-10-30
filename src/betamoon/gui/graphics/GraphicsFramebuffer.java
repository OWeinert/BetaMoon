package betamoon.gui.graphics;

import betamoon.gui.style.GuiAtlasLease;
import java.nio.ByteBuffer;
import org.lwjgl.opengl.ARBFramebufferObject;
import org.lwjgl.opengl.ContextCapabilities;
import org.lwjgl.opengl.EXTFramebufferObject;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GLContext;

/**
 * Host-owned prepared render target whose color attachment is also a borrowed
 * image lease.
 */
public final class GraphicsFramebuffer implements GuiAtlasLease, AutoCloseable {
    private final ContextCapabilities capabilities;
    private final int width;
    private final int height;
    private int framebuffer;
    private int texture;
    private int depth;

    public GraphicsFramebuffer(int width, int height, boolean withDepth) {
        capabilities = GLContext.getCapabilities();
        if (!(capabilities.OpenGL30 || capabilities.GL_ARB_framebuffer_object || capabilities.GL_EXT_framebuffer_object)
                || width < 1 || height < 1 || width > 4096 || height > 4096 || (long) width * height > 4194304) {
            throw new IllegalArgumentException(
                    "Render target requires FBO support and at most 4194304 pixels within 1..4096 per axis");
        }
        this.width = width;
        this.height = height;
        Binding previous = Binding.capture();
        int previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        boolean complete = false;
        try {
            texture = GL11.glGenTextures();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, width, height, 0, GL11.GL_RGBA,
                    GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
            framebuffer = generateFramebuffer(capabilities);
            bindFramebuffer(capabilities, GL30.GL_FRAMEBUFFER, framebuffer);
            if (modern(capabilities)) {
                if (capabilities.OpenGL30) {
                    GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D,
                            texture, 0);
                } else {
                    ARBFramebufferObject.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0,
                            GL11.GL_TEXTURE_2D, texture, 0);
                }
            } else {
                EXTFramebufferObject.glFramebufferTexture2DEXT(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0,
                        GL11.GL_TEXTURE_2D, texture, 0);
            }
            if (withDepth) {
                prepareDepth();
            }
            int status = capabilities.OpenGL30
                    ? GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER)
                    : modern(capabilities)
                            ? ARBFramebufferObject.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER)
                            : EXTFramebufferObject.glCheckFramebufferStatusEXT(GL30.GL_FRAMEBUFFER);
            if (status != GL30.GL_FRAMEBUFFER_COMPLETE) {
                throw new IllegalArgumentException("Render target is incomplete: 0x" + Integer.toHexString(status));
            }
            complete = true;
        } finally {
            previous.restore();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, previousTexture);
            if (!complete) {
                close();
            }
        }
    }

    private void prepareDepth() {
        if (capabilities.OpenGL30) {
            depth = GL30.glGenRenderbuffers();
            GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, depth);
            GL30.glRenderbufferStorage(GL30.GL_RENDERBUFFER, GL14.GL_DEPTH_COMPONENT24, width, height);
            GL30.glFramebufferRenderbuffer(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL30.GL_RENDERBUFFER, depth);
        } else if (modern(capabilities)) {
            depth = ARBFramebufferObject.glGenRenderbuffers();
            ARBFramebufferObject.glBindRenderbuffer(GL30.GL_RENDERBUFFER, depth);
            ARBFramebufferObject.glRenderbufferStorage(GL30.GL_RENDERBUFFER, GL14.GL_DEPTH_COMPONENT24, width, height);
            ARBFramebufferObject.glFramebufferRenderbuffer(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT,
                    GL30.GL_RENDERBUFFER, depth);
        } else {
            depth = EXTFramebufferObject.glGenRenderbuffersEXT();
            EXTFramebufferObject.glBindRenderbufferEXT(GL30.GL_RENDERBUFFER, depth);
            EXTFramebufferObject.glRenderbufferStorageEXT(GL30.GL_RENDERBUFFER, GL14.GL_DEPTH_COMPONENT24, width,
                    height);
            EXTFramebufferObject.glFramebufferRenderbufferEXT(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT,
                    GL30.GL_RENDERBUFFER, depth);
        }
    }

    public void bind() {
        requireActive();
        bindFramebuffer(capabilities, GL30.GL_FRAMEBUFFER, framebuffer);
        GL11.glViewport(0, 0, width, height);
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public int textureId() {
        requireActive();
        return texture;
    }

    public void requireActive() {
        if (framebuffer == 0 || texture == 0) {
            throw new IllegalStateException("Prepared render target has been disposed");
        }
    }

    @Override
    public void close() {
        if (texture != 0) {
            GL11.glDeleteTextures(texture);
            texture = 0;
        }
        if (framebuffer != 0) {
            if (capabilities.OpenGL30) {
                GL30.glDeleteFramebuffers(framebuffer);
            } else if (modern(capabilities)) {
                ARBFramebufferObject.glDeleteFramebuffers(framebuffer);
            } else {
                EXTFramebufferObject.glDeleteFramebuffersEXT(framebuffer);
            }
            framebuffer = 0;
        }
        if (depth != 0) {
            if (capabilities.OpenGL30) {
                GL30.glDeleteRenderbuffers(depth);
            } else if (modern(capabilities)) {
                ARBFramebufferObject.glDeleteRenderbuffers(depth);
            } else {
                EXTFramebufferObject.glDeleteRenderbuffersEXT(depth);
            }
            depth = 0;
        }
    }

    /** Captures only binding identities; no native payload lifetime is extended. */
    public static final class Binding {
        private final ContextCapabilities capabilities = GLContext.getCapabilities();
        private final int draw;
        private final int read;
        private final int renderbuffer;
        private final boolean supported;

        private Binding() {
            supported = modern(capabilities) || capabilities.GL_EXT_framebuffer_object;
            draw = supported ? GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING) : 0;
            read = modern(capabilities) ? GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING) : draw;
            renderbuffer = supported ? GL11.glGetInteger(GL30.GL_RENDERBUFFER_BINDING) : 0;
        }

        public static Binding capture() {
            return new Binding();
        }

        public void restore() {
            if (modern(capabilities)) {
                bindFramebuffer(capabilities, GL30.GL_DRAW_FRAMEBUFFER, draw);
                bindFramebuffer(capabilities, GL30.GL_READ_FRAMEBUFFER, read);
                if (capabilities.OpenGL30) {
                    GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, renderbuffer);
                } else {
                    ARBFramebufferObject.glBindRenderbuffer(GL30.GL_RENDERBUFFER, renderbuffer);
                }
            } else if (supported) {
                bindFramebuffer(capabilities, GL30.GL_FRAMEBUFFER, draw);
                EXTFramebufferObject.glBindRenderbufferEXT(GL30.GL_RENDERBUFFER, renderbuffer);
            }
        }
    }

    private static boolean modern(ContextCapabilities capabilities) {
        return capabilities.OpenGL30 || capabilities.GL_ARB_framebuffer_object;
    }

    private static int generateFramebuffer(ContextCapabilities capabilities) {
        return capabilities.OpenGL30
                ? GL30.glGenFramebuffers()
                : modern(capabilities)
                        ? ARBFramebufferObject.glGenFramebuffers()
                        : EXTFramebufferObject.glGenFramebuffersEXT();
    }

    private static void bindFramebuffer(ContextCapabilities capabilities, int target, int framebuffer) {
        if (capabilities.OpenGL30) {
            GL30.glBindFramebuffer(target, framebuffer);
        } else if (modern(capabilities)) {
            ARBFramebufferObject.glBindFramebuffer(target, framebuffer);
        } else {
            EXTFramebufferObject.glBindFramebufferEXT(target, framebuffer);
        }
    }
}
