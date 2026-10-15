package betamoon.luamodloader;

import betamoon.assets.io.FileAssetProvider;
import betamoon.client.assets.ClientAssets;
import betamoon.client.particles.ParticleRenderer;
import betamoon.client.particles.ParticleRuntime;
import betamoon.client.particles.ParticleSprite;
import betamoon.particles.ParticleDefinition;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import org.luaj.vm2.Globals;
import org.luaj.vm2.lib.jse.JsePlatform;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.Pbuffer;
import org.lwjgl.opengl.PixelFormat;
import static betamoon.luamodloader.ParticleApiTest.require;

/**
 * Actual OpenGL sprite rendering, independent strip frames, refresh fallback,
 * and state/resource cleanup.
 */
public final class ParticleRenderTest {
    private ParticleRenderTest() {
    }

    public static void main(String[] args) throws Exception {
        Path root = Paths.get("build/particle-render").toAbsolutePath();
        Files.createDirectories(root);
        writeStrip(root.resolve("strip.png"), 0xffff2020, 0xff20ff20);
        List<String> diagnostics = new ArrayList<String>();
        ClientAssets.useProviders(new FileAssetProvider(root.toFile()), null, diagnostics::add);
        Globals lua = JsePlatform.standardGlobals();
        ParticleDefinition definition = ParticleApiTest.definition(lua,
                "texture={asset='strip.png',animation={frameTicks=1}},motion={drag=1},"
                        + "size=0.5,color={r=1,g=1,b=1,a=0.8}");
        ParticleApiTest.FixtureWorld world = new ParticleApiTest.FixtureWorld();
        ParticleRuntime runtime = new ParticleRuntime();
        runtime.clear(world);
        ParticleSprite camera = new ParticleSprite(world, definition, ParticleApiTest.OWNER, 4, 64, 6, 0, 0, 0, 1,
                new double[]{1, 1, 1, 1}, 1, error -> {
                    throw error;
                });
        runtime.camera(camera);
        runtime.spawn(definition, ParticleApiTest.OWNER, 3.6, 64, 4, 0, 0, 0, 1, new double[]{1, 1, 1, 1}, 1, 16);
        runtime.tick(false);
        runtime.spawn(definition, ParticleApiTest.OWNER, 4.4, 64, 4, 0, 0, 0, 1, new double[]{1, 1, 1, 1}, 1, 16);
        runtime.tick(false);
        Pbuffer surface = new Pbuffer(256, 256, new PixelFormat(8, 24, 0), null, null);
        ParticleRenderer renderer = new ParticleRenderer();
        try {
            surface.makeCurrent();
            GL11.glViewport(0, 0, 256, 256);
            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glLoadIdentity();
            GL11.glOrtho(-1, 1, -1, 1, 0.1, 10);
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glLoadIdentity();
            render(renderer, runtime, camera, root.resolve("independent-frames.png"));
            BufferedImage image = ImageIO.read(root.resolve("independent-frames.png").toFile());
            require(green(image.getRGB(76, 128)) > 100 && red(image.getRGB(179, 128)) > 100,
                    "Particles born on different ticks must display independent strip frames");
            writeStrip(root.resolve("strip.png"), 0xff2020ff, 0xffffff20);
            ClientAssets.refresh();
            render(renderer, runtime, camera, root.resolve("refreshed.png"));
            image = ImageIO.read(root.resolve("refreshed.png").toFile());
            require((image.getRGB(179, 128) & 255) > 100, "Asset refresh must update the live sprite texture");
            Files.write(root.resolve("strip.png"), new byte[]{1, 2, 3});
            ClientAssets.refresh();
            require(!diagnostics.isEmpty(), "Invalid replacement must report a diagnostic");
            render(renderer, runtime, camera, root.resolve("failed-refresh.png"));
            image = ImageIO.read(root.resolve("failed-refresh.png").toFile());
            require((image.getRGB(179, 128) & 255) > 100, "Invalid replacement must preserve the prior usable image");
            runtime.removeOwner(ParticleApiTest.OWNER);
            renderer.prune(runtime.sprites());
            require(runtime.sprites().isEmpty(), "Owner unload must leave no rendered sprites");
            require(GL11.glGetError() == GL11.GL_NO_ERROR, "Particle pass must leave no OpenGL errors");
        } finally {
            renderer.clear();
            surface.destroy();
            LuaScriptRegistry.setCurrentScriptFile(null);
        }
        System.out
                .println("Particle OpenGL rendering passed: independent frames, refresh, fallback, state and cleanup.");
    }

    private static void render(ParticleRenderer renderer, ParticleRuntime runtime, ParticleSprite camera, Path path)
            throws Exception {
        GL11.glClearColor(0, 0, 0, 1);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_ALPHA_TEST);
        GL11.glDepthMask(true);
        GL11.glColor4f(0.2f, 0.3f, 0.4f, 0.5f);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        renderer.render(runtime.sprites(), camera, 1, null, runtime);
        require(!GL11.glIsEnabled(GL11.GL_BLEND) && GL11.glIsEnabled(GL11.GL_ALPHA_TEST)
                && GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK) && GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D) == 0,
                "Particle rendering must restore blend, alpha, depth, and texture state");
        ByteBuffer pixels = BufferUtils.createByteBuffer(256 * 256 * 4);
        GL11.glReadPixels(0, 0, 256, 256, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
        BufferedImage image = new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 256; y++) {
            for (int x = 0; x < 256; x++) {
                int offset = (y * 256 + x) * 4;
                int color = 0xff000000 | (pixels.get(offset) & 255) << 16 | (pixels.get(offset + 1) & 255) << 8
                        | pixels.get(offset + 2) & 255;
                image.setRGB(x, 255 - y, color);
            }
        }
        ImageIO.write(image, "png", path.toFile());
    }

    private static int red(int color) {
        return color >> 16 & 255;
    }

    private static int green(int color) {
        return color >> 8 & 255;
    }

    private static void writeStrip(Path path, int first, int second) throws Exception {
        BufferedImage strip = new BufferedImage(16, 32, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 32; y++) {
            for (int x = 0; x < 16; x++) {
                strip.setRGB(x, y, y < 16 ? first : second);
            }
        }
        ImageIO.write(strip, "png", path.toFile());
    }
}
