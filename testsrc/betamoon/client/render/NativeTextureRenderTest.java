package betamoon.client.render;

import betamoon.client.assets.NativeTextureResources;
import betamoon.client.assets.TexturePixelUpload;
import betamoon.luaapi.asset.ModelAppearanceDeclaration;
import betamoon.luaapi.block.ModelVariantRenderTest;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import net.minecraft.src.RenderEngine;
import net.minecraft.src.TexturePackBase;
import org.luaj.vm2.Globals;
import org.luaj.vm2.lib.jse.JsePlatform;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

/** Runs inside the existing offscreen context and verifies actual atlas sampling and live uploads. */
final class NativeTextureRenderTest {
    private NativeTextureRenderTest() {
    }

    static void run(Path output) throws Exception {
        BufferedImage image = new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 256; y++) {
            for (int x = 0; x < 256; x++) {
                image.setRGB(x, y, x >= 32 && x < 48 && y >= 16 && y < 32 ? 0xff00ff00 : 0xffff00ff);
            }
        }
        ByteArrayOutputStream encoded = new ByteArrayOutputStream();
        ImageIO.write(image, "png", encoded);
        NativeTextureResources.useTexturePack(new TexturePackBase() {
            public InputStream getResourceAsStream(String path) {
                return "/terrain.png".equals(path) ? new ByteArrayInputStream(encoded.toByteArray()) : null;
            }
        });
        RenderEngine engine = new RenderEngine(null, null);
        int texture = engine.allocateAndSetupTexture(image);
        try {
            Globals lua = JsePlatform.standardGlobals();
            lua.load("cell={model='minecraft:block/full_block',texture='minecraft:block/sand',"
                    + "material={lighting='unlit'}}; "
                    + "whole={model='minecraft:block/full_block',texture='minecraft:image/terrain',"
                    + "material={lighting='unlit'}}").call();
            try (ModelAppearance cell = new ModelAppearance(new ModelAppearanceDeclaration(lua.get("cell")));
                    ModelAppearance whole = new ModelAppearance(new ModelAppearanceDeclaration(lua.get("whole")))) {
                require(render(cell, texture, output.resolve("native-sand-cell.png"), 0, 255, 0) > 1000,
                        "A native cell material must sample sand's cell, excluding neighboring magenta cells");
                require(render(whole, texture, output.resolve("native-whole-atlas.png"), 255, 0, 255) > 1000,
                        "A whole-image material must sample the entire native atlas");
                BufferedImage frame = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
                for (int y = 0; y < 16; y++) {
                    for (int x = 0; x < 16; x++) {
                        frame.setRGB(x, y, 0xff0000ff);
                    }
                }
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
                TexturePixelUpload.upload(frame, 32, 16, 16, 16, false, false);
                require(render(cell, texture, output.resolve("native-live-cell.png"), 0, 0, 255) > 1000,
                        "The same model must see an atlas subimage update without reloading or recropping");
                ModelVariantRenderTest.runNativeAtlas(texture);
            }
        } finally {
            engine.deleteTexture(texture);
            NativeTextureResources.useTexturePack(null);
        }
    }

    private static int render(ModelAppearance appearance, int texture, Path output, int red, int green, int blue)
            throws Exception {
        GL11.glDepthMask(true);
        GL11.glClearColor(0, 0, 0, 0);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        ModelRenderer.render(appearance, "entity", 0, 0, 0, 0, 0, 1, resource -> {
            require("/terrain.png".equals(resource), "Native model binding must retain the native resource path");
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        });
        ByteBuffer pixels = BufferUtils.createByteBuffer(256 * 256 * 4);
        GL11.glReadPixels(0, 0, 256, 256, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
        BufferedImage image = new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB);
        int matches = 0;
        for (int y = 0; y < 256; y++) {
            for (int x = 0; x < 256; x++) {
                int r = pixels.get() & 255;
                int g = pixels.get() & 255;
                int b = pixels.get() & 255;
                int a = pixels.get() & 255;
                image.setRGB(x, 255 - y, (a << 24) | (r << 16) | (g << 8) | b);
                if (r == red && g == green && b == blue && a > 0) {
                    matches++;
                }
            }
        }
        ImageIO.write(image, "png", output.toFile());
        require(GL11.glGetError() == GL11.GL_NO_ERROR, "Native texture drawing must not produce GL errors");
        return matches;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
