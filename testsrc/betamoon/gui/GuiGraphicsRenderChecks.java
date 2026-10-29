package betamoon.gui;

import betamoon.gui.framework.GuiComponentScreen;
import betamoon.gui.framework.GuiContainer;
import betamoon.gui.framework.GuiContext;
import betamoon.gui.framework.GuiElement;
import betamoon.gui.framework.GuiFrameSnapshot;
import betamoon.gui.framework.GuiGeometry.Rect;
import betamoon.gui.framework.GuiInputEvent;
import betamoon.gui.framework.GuiScene;
import betamoon.gui.graphics.GraphicsClip;
import betamoon.gui.graphics.GraphicsRenderScope;
import betamoon.gui.graphics.GraphicsStateGuard;
import betamoon.gui.graphics.GraphicsTransform;
import betamoon.gui.widget.GuiLink;
import betamoon.gui.widget.GuiMessage;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.io.ByteArrayOutputStream;
import betamoon.gui.framework.GuiTextureCache;
import java.util.concurrent.atomic.AtomicInteger;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.src.FontRenderer;
import org.luaj.vm2.Globals;
import org.luaj.vm2.lib.jse.JsePlatform;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

/** Runs in the real LWJGL pbuffer provided by GuiShowcaseRenderTest. */
final class GuiGraphicsRenderChecks {
    static void run(Minecraft minecraft, FontRenderer font, File output) throws Exception {
        restoreAfterFailure();
        transformedFrame(minecraft, font, output);
        noLuaInPreparedPaint(minecraft);
        preparedAssetFailure();
    }

    private static void preparedAssetFailure() throws Exception {
        GuiTextureCache cache = new GuiTextureCache();
        BufferedImage bitmap = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(bitmap, "png", bytes);
        GuiTextureCache.Texture usable = cache.get("ui-asset-candidate", bytes.toByteArray(), 1);
        require(usable != null && GL11.glIsTexture(usable.getTextureId()), "Prepared image did not allocate");
        require(cache.get("ui-asset-candidate", new byte[]{0}, 2) == usable && GL11.glIsTexture(usable.getTextureId()),
                "Invalid image refresh deleted the last usable resource");
        cache.clear();
        require(!GL11.glIsTexture(usable.getTextureId()), "Prepared cache disposal retained a GPU resource");
    }

    private static void restoreAfterFailure() {
        GL11.glViewport(0, 0, 320, 240);
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(5, 7, 100, 120);
        GL11.glEnable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(true);
        GL11.glColor4f(0.2F, 0.3F, 0.4F, 0.5F);
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        float[] projection = matrix(GL11.GL_PROJECTION_MATRIX);
        float[] modelview = matrix(GL11.GL_MODELVIEW_MATRIX);
        float[] texture = matrix(GL11.GL_TEXTURE_MATRIX);
        int alignment = GL11.glGetInteger(GL11.GL_UNPACK_ALIGNMENT);
        GraphicsRenderScope saved = null;
        try {
            saved = GraphicsRenderScope.open(() -> {
            }, GraphicsStateGuard::enter, new GraphicsTransform(3, 4, 2, 2),
                    new GraphicsClip(0, 0, 50, 50).pixels(100, 100, 200, 200));
            try (GraphicsRenderScope scope = saved) {
                scope.requireActive();
                require(!GL11.glIsEnabled(GL11.GL_LIGHTING) && !GL11.glIsEnabled(GL11.GL_DEPTH_TEST),
                        "Incoming graphics pass did not establish UI state");
                GL11.glMatrixMode(GL11.GL_PROJECTION);
                GL11.glLoadIdentity();
                GL11.glTranslated(8, 9, 10);
                GL11.glMatrixMode(GL11.GL_TEXTURE);
                GL11.glScaled(3, 4, 5);
                GL11.glViewport(1, 2, 20, 30);
                GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1);
                GL11.glDisable(GL11.GL_SCISSOR_TEST);
                GL11.glDepthMask(false);
                GL11.glColor4f(1, 0, 0, 1);
                throw new IllegalArgumentException("graphics callback fixture");
            }
        } catch (IllegalArgumentException expected) {
            require("graphics callback fixture".equals(expected.getMessage()), "Unexpected graphics setup failure");
        }
        require(saved != null, "Render scope did not initialize");
        try {
            saved.requireActive();
            throw new AssertionError("Failed render retained authority");
        } catch (IllegalStateException expected) {
            // Expected: cleanup expires the token even on callback failure.
        }
        require(GL11.glGetInteger(GL11.GL_MATRIX_MODE) == GL11.GL_PROJECTION, "Matrix mode leaked");
        same(projection, matrix(GL11.GL_PROJECTION_MATRIX));
        same(modelview, matrix(GL11.GL_MODELVIEW_MATRIX));
        same(texture, matrix(GL11.GL_TEXTURE_MATRIX));
        require(GL11.glIsEnabled(GL11.GL_LIGHTING) && GL11.glIsEnabled(GL11.GL_DEPTH_TEST)
                && GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK), "Server graphics state leaked");
        require(GL11.glGetInteger(GL11.GL_UNPACK_ALIGNMENT) == alignment, "Client pixel state leaked");
        IntBuffer box = BufferUtils.createIntBuffer(16);
        GL11.glGetInteger(GL11.GL_SCISSOR_BOX, box);
        require(GL11.glIsEnabled(GL11.GL_SCISSOR_TEST) && box.get(0) == 5 && box.get(1) == 7 && box.get(2) == 100
                && box.get(3) == 120, "Parent scissor leaked");
        FloatBuffer color = BufferUtils.createFloatBuffer(16);
        GL11.glGetFloat(GL11.GL_CURRENT_COLOR, color);
        require(Math.abs(color.get(0) - 0.2F) < 0.0001F && Math.abs(color.get(3) - 0.5F) < 0.0001F,
                "Graphics color leaked");
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        require(GL11.glGetError() == GL11.GL_NO_ERROR, "Graphics guard produced an OpenGL error");
    }

    private static void transformedFrame(Minecraft minecraft, FontRenderer font, File output) throws Exception {
        AtomicInteger clicks = new AtomicInteger();
        GuiElement control = new GuiElement() {
            @Override
            protected void renderElement(GuiContext context) {
                context.getRenderer().drawRect(getBounds(), 0xFFFF0000);
            }

            @Override
            protected boolean onInput(GuiContext context, GuiInputEvent event) {
                if (event.getType() == GuiInputEvent.Type.POINTER_DOWN) {
                    require(event.getMouseX() >= 20 && event.getMouseX() < 50, "Inverse pointer X disagreed");
                    clicks.incrementAndGet();
                    return true;
                }
                return false;
            }
        };
        control.setVisualTransform(new GraphicsTransform(40, 10, 2, 2));
        GuiContainer root = new GuiContainer() {
            @Override
            protected void arrangeChildren(GuiContext context) {
                control.arrange(context, new Rect(20, 20, 50, 30));
            }
        };
        root.add(control);
        GuiScene scene = new GuiScene();
        scene.setContent(root);
        scene.updateEnvironment(minecraft, font, 320, 240, 320, 240);
        scene.update();
        GuiShowcaseRenderTest.setupFrame();
        scene.render(65, 35, 0);
        GuiFrameSnapshot frame = scene.getDisplayedFrame();
        require(frame.node(control).bounds.contains(65, 35) && !frame.node(control).bounds.contains(25, 25),
                "Published transformed bounds disagreed");
        BufferedImage image = GuiShowcaseRenderTest.capture(320, 240);
        require((image.getRGB(65, 35) & 0xFFFFFF) == 0xFF0000 && (image.getRGB(25, 25) & 0xFFFFFF) != 0xFF0000,
                "Transformed pixels disagreed with input geometry");
        require(scene.mousePressed(65, 35, 0) && clicks.get() == 1, "Displayed transformed control missed input");
        scene.render(65, 35, 0);
        require(scene.getDisplayedFrame() == frame, "Idle drawing rebuilt geometry");
        ImageIO.write(image, "png", new File(output, "ui-transformed-frame.png"));
    }

    private static void noLuaInPreparedPaint(Minecraft minecraft) {
        Globals lua = JsePlatform.standardGlobals();
        lua.load("calls=0; function sample() calls=calls+1; return 'prepared text' end").call();
        GuiContainer root = new GuiContainer();
        GuiLink link = root.add(new GuiLink());
        link.setTextSupplier(() -> lua.get("sample").call().checkjstring());
        link.setTooltipSupplier(() -> lua.get("sample").call().checkjstring());
        root.add(new GuiMessage(() -> lua.get("sample").call().checkjstring()));
        GuiComponentScreen screen = new GuiComponentScreen() {
            @Override
            protected GuiElement createContent() {
                return root;
            }

            @Override
            protected void updateSceneState(int mouseX, int mouseY, float partialTicks) {
                require(getGuiContext().getRenderer().textWidth("Ready") > 0,
                        "Initial publication cannot measure prepared font metrics");
                lua.get("sample").call();
            }
        };
        screen.setWorldAndResolution(minecraft, 320, 240);
        int publishedCalls = lua.get("calls").checkint();
        for (int i = 0; i < 8; i++) {
            GuiShowcaseRenderTest.setupFrame();
            screen.drawScreen(2, 2, 0.5F);
        }
        require(lua.get("calls").checkint() == publishedCalls, "Prepared painting invoked Lua");
        screen.updateScreen();
        require(lua.get("calls").checkint() == publishedCalls + 4, "Logical update did not sample once");
    }

    private static float[] matrix(int name) {
        FloatBuffer buffer = BufferUtils.createFloatBuffer(16);
        GL11.glGetFloat(name, buffer);
        float[] result = new float[16];
        buffer.get(result);
        return result;
    }

    private static void same(float[] expected, float[] actual) {
        for (int i = 0; i < expected.length; i++) {
            require(Math.abs(expected[i] - actual[i]) < 0.0001F, "Graphics matrix leaked at " + i);
        }
    }

    private static void require(boolean condition, String detail) {
        if (!condition) {
            throw new AssertionError(detail);
        }
    }
}
