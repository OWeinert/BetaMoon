package betamoon.worldgen.terrain;

import betamoon.assets.model.ModelJson;
import betamoon.atmosphere.AtmosphereRegistry;
import betamoon.client.atmosphere.AtmosphereRuntime;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.Pbuffer;
import org.lwjgl.opengl.PixelFormat;

/**
 * Real offscreen fixed-function rendering and state restoration, without
 * opening a game window.
 */
public final class AtmosphereOpenGlTest {
    public static void main(String[] args) throws Exception {
        TerrainFoundationTest.Fixture world = new TerrainFoundationTest.Fixture(
                new TerrainFoundationTest.Save(Files.createTempDirectory("betamoon-atmosphere-gl-")), 1);
        try (AtmosphereRegistry.Batch batch = AtmosphereRegistry.begin("gl.lua")) {
            AtmosphereRegistry.add(ModelJson.read(("{\"key\":\"test:atmosphere/tone\"," + "\"lightTone\":[0.5,0.75,1]}")
                    .getBytes(StandardCharsets.UTF_8)));
            AtmosphereRegistry.bind(ModelJson
                    .read(("{\"profile\":\"test:atmosphere/tone\"," + "\"scope\":{\"type\":\"dimension\",\"id\":0}}")
                            .getBytes(StandardCharsets.UTF_8)));
            batch.publish();
        }
        Field presentation = AtmosphereRuntime.class.getDeclaredField("presentation");
        presentation.setAccessible(true);
        presentation.set(null, AtmosphereRegistry.queryPresentation(world, 0, 64, 0));
        for (int context = 0; context < 2; context++) {
            Pbuffer buffer = new Pbuffer(16, 16, new PixelFormat(), null);
            try {
                buffer.makeCurrent();
                verifyContext();
            } finally {
                buffer.destroy();
            }
        }
        presentation.set(null, null);
        System.out.println("Offscreen atmosphere OpenGL passed: pixel tint, native output restoration, texture state, "
                + "nested scopes and context replacement.");
    }

    private static void verifyContext() {
        GL11.glViewport(0, 0, 16, 16);
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glLoadIdentity();
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glLoadIdentity();
        GL11.glDisable(GL11.GL_DITHER);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_BLEND);
        GL13.glActiveTexture(GL13.GL_TEXTURE1);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_MODE, GL11.GL_REPLACE);
        int binding = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(0.8f, 0.6f, 0.4f, 1);
        int captured = AtmosphereRuntime.beginWorld();
        require(captured == GL13.GL_TEXTURE0, "Tint must capture the original texture unit");
        draw();
        pixel(0.4, 0.45, 0.4);
        AtmosphereRuntime.endWorld(captured);
        require(GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE) == GL13.GL_TEXTURE0,
                "Tint cleanup must restore the active texture unit");
        draw();
        pixel(0.8, 0.6, 0.4);
        GL13.glActiveTexture(GL13.GL_TEXTURE1);
        require(!GL11.glIsEnabled(GL11.GL_TEXTURE_2D), "Tint cleanup must restore texture enable state");
        require(GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D) == binding, "Tint cleanup must restore texture binding");
        require(GL11.glGetTexEnvi(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_MODE) == GL11.GL_REPLACE,
                "Tint cleanup must restore the texture combiner");
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        int outer = AtmosphereRuntime.beginWorld();
        GL13.glActiveTexture(GL13.GL_TEXTURE1);
        int inner = AtmosphereRuntime.beginWorld();
        AtmosphereRuntime.endWorld(inner);
        require(GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE) == GL13.GL_TEXTURE1,
                "Nested cleanup must restore its own captured texture unit");
        AtmosphereRuntime.endWorld(outer);
        require(GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE) == GL13.GL_TEXTURE0,
                "Outer cleanup must retain its original texture unit");
        require(GL11.glGetError() == GL11.GL_NO_ERROR, "Tint rendering must not generate OpenGL errors");
    }

    private static void draw() {
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2f(-1, -1);
        GL11.glVertex2f(1, -1);
        GL11.glVertex2f(1, 1);
        GL11.glVertex2f(-1, 1);
        GL11.glEnd();
    }

    private static void pixel(double red, double green, double blue) {
        ByteBuffer pixel = BufferUtils.createByteBuffer(4);
        GL11.glReadPixels(8, 8, 1, 1, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixel);
        double[] expected = {red, green, blue};
        for (int channel = 0; channel < 3; channel++) {
            int actual = pixel.get(channel) & 255;
            require(Math.abs(actual - expected[channel] * 255) <= 3,
                    "Unexpected tint pixel channel " + channel + ": " + actual);
        }
    }

    private static void require(boolean value, String message) {
        if (!value) {
            throw new AssertionError(message);
        }
    }
}
