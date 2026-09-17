package betamoon.client.render;

import betamoon.assets.io.FileAssetProvider;
import betamoon.assets.model.ModelFoundationTest;
import betamoon.client.assets.ClientAssets;
import betamoon.luaapi.asset.ModelAppearanceDeclaration;
import betamoon.luaapi.block.ModelVariantRenderTest;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.luaj.vm2.Globals;
import org.luaj.vm2.lib.jse.JsePlatform;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.Pbuffer;
import org.lwjgl.opengl.PixelFormat;

/**
 * Real off-screen OpenGL rendering and state restoration across mixed Lua/clip
 * poses.
 */
public final class ModelRenderTest {
    private ModelRenderTest() {
    }

    public static void main(String[] args) throws Exception {
        Path root = java.nio.file.Paths.get("build/model-render").toAbsolutePath();
        Files.createDirectories(root);
        Files.write(root.resolve("test.json"), ModelFoundationTest.GEOMETRY.getBytes(StandardCharsets.UTF_8));
        Files.write(root.resolve("test.animation.json"),
                ModelFoundationTest.ANIMATIONS.getBytes(StandardCharsets.UTF_8));
        BufferedImage texture = new BufferedImage(64, 32, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 32; y++) {
            for (int x = 0; x < 64; x++) {
                texture.setRGB(x, y, ((x / 4 + y / 4) % 2 == 0) ? 0xfff0a030 : 0xff30a0e0);
            }
        }
        ImageIO.write(texture, "png", root.resolve("test.png").toFile());
        ClientAssets.useProviders(new FileAssetProvider(root.toFile()), null, message -> {
            throw new AssertionError(message);
        });
        Globals lua = JsePlatform.standardGlobals();
        lua.load("appearance={model='test.json',texture='test.png',animation={asset='test.animation.json',clip='wave'},"
                + "onPose=function(ctx) ctx.pose:rotate('body',{x=0,y=25,z=0}) end}").call();
        Pbuffer surface = new Pbuffer(256, 256, new PixelFormat(8, 24, 0), null, null);
        try {
            surface.makeCurrent();
            GL11.glViewport(0, 0, 256, 256);
            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glLoadIdentity();
            GL11.glOrtho(-1, 1, -0.3, 1.7, -5, 5);
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glLoadIdentity();
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            int textureId = GL11.glGenTextures();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId);
            ByteBuffer pixels = BufferUtils.createByteBuffer(64 * 32 * 4);
            for (int y = 0; y < 32; y++) {
                for (int x = 0; x < 64; x++) {
                    int color = texture.getRGB(x, y);
                    pixels.put((byte) (color >> 16)).put((byte) (color >> 8)).put((byte) color).put((byte) 255);
                }
            }
            pixels.flip();
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, 64, 32, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE,
                    pixels);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            try (ModelAppearance appearance = new ModelAppearance(
                    new ModelAppearanceDeclaration(lua.get("appearance")))) {
                int first = render(appearance, textureId, 0, root.resolve("rest.png"));
                int second = render(appearance, textureId, 20, root.resolve("wave.png"));
                require(first > 100 && second > 100 && first != second,
                        "Animated model must produce distinct visible frames");
                lua.load("appearance.onPose=function() error('expected pose error') end").call();
                try (ModelAppearance failed = new ModelAppearance(
                        new ModelAppearanceDeclaration(lua.get("appearance")))) {
                    render(failed, textureId, 0, root.resolve("callback-error.png"));
                }
            }
            ModelVariantRenderTest.run(textureId);
            GL11.glDeleteTextures(textureId);
            require(GL11.glGetError() == GL11.GL_NO_ERROR, "OpenGL error after model rendering");
        } finally {
            surface.destroy();
        }
        System.out.println(
                "Model rendering passed: real GL pixels, independent frames, callback failure and state restoration.");
    }

    private static int render(ModelAppearance appearance, int texture, double ticks, Path output) throws Exception {
        GL11.glClearColor(0, 0, 0, 0);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_ONE, GL11.GL_ZERO);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glColor4f(0.2f, 0.3f, 0.4f, 0.5f);
        GL11.glDepthMask(false);
        ModelRenderer.render(appearance, "held", ticks, 0, 0, 0, 0, 1,
                resource -> GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture));
        require(GL11.glIsEnabled(GL11.GL_BLEND), "Blend enable leaked");
        require(!GL11.glIsEnabled(GL11.GL_CULL_FACE), "Cull state leaked");
        require(!GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK), "Depth-write state leaked");
        require(GL11.glGetInteger(GL11.GL_BLEND_SRC) == GL11.GL_ONE, "Blend function leaked");
        FloatBuffer color = BufferUtils.createFloatBuffer(16);
        GL11.glGetFloat(GL11.GL_CURRENT_COLOR, color);
        require(Math.abs(color.get(0) - 0.2f) < 0.00001, "Color leaked");
        GL11.glDepthMask(true);
        ByteBuffer pixels = BufferUtils.createByteBuffer(256 * 256 * 4);
        GL11.glReadPixels(0, 0, 256, 256, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
        BufferedImage image = new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB);
        int count = 0;
        for (int y = 0; y < 256; y++) {
            for (int x = 0; x < 256; x++) {
                int r = pixels.get() & 255;
                int g = pixels.get() & 255;
                int b = pixels.get() & 255;
                int a = pixels.get() & 255;
                image.setRGB(x, 255 - y, (a << 24) | (r << 16) | (g << 8) | b);
                if (r + g + b > 0) {
                    count++;
                }
            }
        }
        ImageIO.write(image, "png", output.toFile());
        return count;
    }

    private static void require(boolean value, String message) {
        if (!value) {
            throw new AssertionError(message);
        }
    }
}
