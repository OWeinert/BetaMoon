package betamoon.client.particles;

import betamoon.client.assets.ClientAssets;
import betamoon.client.assets.TextureAsset;
import betamoon.client.assets.TextureFrames;
import betamoon.client.assets.TextureImage;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.src.Entity;
import net.minecraft.src.RenderEngine;
import net.minecraft.src.Tessellator;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

/**
 * Sorted alpha sprites and texture-batched additive sprites with independent
 * strip cursors.
 */
public final class ParticleRenderer {
    private static final int TEXTURE_LIMIT = 128;
    private final Map<String, TextureBinding> textures = new HashMap<String, TextureBinding>();

    public void prune(List<ParticleSprite> sprites) {
        Set<String> used = new HashSet<String>();
        for (ParticleSprite sprite : sprites) {
            if (sprite.definition.texture != null) {
                used.add(sprite.definition.texture.getCacheKey());
            }
        }
        for (String key : new ArrayList<String>(textures.keySet())) {
            if (!used.contains(key)) {
                textures.remove(key).close();
            }
        }
    }

    public void clear() {
        for (TextureBinding binding : textures.values()) {
            binding.close();
        }
        textures.clear();
    }

    public void render(List<ParticleSprite> sprites, Entity camera, float partialTick, RenderEngine engine,
            ParticleRuntime runtime) {
        if (sprites.isEmpty() || camera == null) {
            return;
        }
        List<ParticleSprite> sorted = new ArrayList<ParticleSprite>(sprites);
        sorted.sort((left, right) -> {
            if (left.definition.additive != right.definition.additive) {
                return left.definition.additive ? 1 : -1;
            }
            if (left.definition.additive) {
                return textureKey(left).compareTo(textureKey(right));
            }
            return Double.compare(distance(right, camera, partialTick), distance(left, camera, partialTick));
        });
        double cx = interpolate(camera.lastTickPosX, camera.posX, partialTick);
        double cy = interpolate(camera.lastTickPosY, camera.posY, partialTick);
        double cz = interpolate(camera.lastTickPosZ, camera.posZ, partialTick);
        double yaw = camera.rotationYaw * Math.PI / 180;
        double pitch = camera.rotationPitch * Math.PI / 180;
        double rightX = Math.cos(yaw);
        double rightZ = Math.sin(yaw);
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glPushClientAttrib(GL11.GL_ALL_CLIENT_ATTRIB_BITS);
        boolean drawing = false;
        try {
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glDisable(GL11.GL_ALPHA_TEST);
            GL11.glDepthMask(false);
            int bound = -1;
            boolean additive = false;
            Map<String, Integer> atlases = new HashMap<String, Integer>();
            for (ParticleSprite sprite : sorted) {
                if (sprite.isDead) {
                    continue;
                }
                TextureBinding binding = null;
                int texture;
                try {
                    if (sprite.definition.texture == null) {
                        if (engine == null) {
                            continue;
                        }
                        Integer atlas = atlases.get(sprite.definition.atlas);
                        if (atlas == null) {
                            // Native texture lookup can itself bind/upload; finish the previous batch
                            // first.
                            if (drawing) {
                                Tessellator.instance.draw();
                                drawing = false;
                            }
                            atlas = engine.getTexture(sprite.definition.atlas);
                            atlases.put(sprite.definition.atlas, atlas);
                        }
                        texture = atlas;
                    } else {
                        String key = sprite.definition.texture.getCacheKey();
                        binding = textures.get(key);
                        if (binding == null) {
                            if (textures.size() >= TEXTURE_LIMIT) {
                                runtime.failure(sprite.owner, "Particle texture budget reached.");
                                continue;
                            }
                            if (drawing) {
                                Tessellator.instance.draw();
                                drawing = false;
                            }
                            binding = new TextureBinding(ClientAssets.acquireTexture(sprite.definition.texture));
                            textures.put(key, binding);
                        }
                        if (binding.needsUpload() && drawing) {
                            Tessellator.instance.draw();
                            drawing = false;
                        }
                        texture = binding.texture();
                    }
                } catch (IOException | RuntimeException error) {
                    runtime.failure(sprite.owner, "Particle texture " + textureKey(sprite) + ": " + error.getMessage());
                    continue;
                }
                if (!drawing || bound != texture || additive != sprite.definition.additive) {
                    if (drawing) {
                        Tessellator.instance.draw();
                    }
                    bound = texture;
                    additive = sprite.definition.additive;
                    GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
                    GL11.glBlendFunc(GL11.GL_SRC_ALPHA, additive ? GL11.GL_ONE : GL11.GL_ONE_MINUS_SRC_ALPHA);
                    Tessellator.instance.startDrawingQuads();
                    drawing = true;
                }
                double upX = sprite.definition.vertical ? 0 : -Math.sin(yaw) * Math.sin(pitch);
                double upY = sprite.definition.vertical ? 1 : Math.cos(pitch);
                double upZ = sprite.definition.vertical ? 0 : Math.cos(yaw) * Math.sin(pitch);
                draw(sprite, binding, partialTick, cx, cy, cz, rightX, rightZ, upX, upY, upZ);
            }
        } catch (RuntimeException error) {
            runtime.failure("Particles", "Particle render pass failed: " + error.getMessage());
        } finally {
            try {
                if (drawing) {
                    Tessellator.instance.draw();
                }
            } finally {
                GL11.glPopClientAttrib();
                GL11.glPopAttrib();
            }
        }
    }

    private void draw(ParticleSprite sprite, TextureBinding binding, float partialTick, double cx, double cy, double cz,
            double rightX, double rightZ, double upX, double upY, double upZ) {
        double u0 = 0;
        double u1 = 1;
        double v0 = 0;
        double v1 = 1;
        if (binding == null) {
            int position = (sprite.frameSeed % sprite.definition.atlasFrameCount()
                    + sprite.age() / sprite.definition.frameTicks) % sprite.definition.atlasFrameCount();
            int cell = sprite.definition.atlasIndex(position);
            u0 = (cell % 16) / 16.0;
            u1 = u0 + 0.999 / 16;
            v0 = (cell / 16) / 16.0;
            v1 = v0 + 0.999 / 16;
        } else {
            TextureFrames frames = binding.asset.getFrames();
            if (frames.animation() != null) {
                int position = (sprite.frameSeed % frames.sequenceLength()
                        + sprite.age() / frames.animation().frameTicks()) % frames.sequenceLength();
                int cell = frames.sourceFrame(position);
                BufferedImage image = binding.asset.getContent().getValue().getImage();
                double height = (double) image.getWidth() / image.getHeight();
                v0 = cell * height;
                v1 = v0 + height;
            }
        }
        double brightness = sprite.definition.fullLight ? 1 : sprite.getEntityBrightness(partialTick);
        Tessellator.instance.setColorRGBA_F(
                (float) (interpolate(sprite.previousColor[0], sprite.color[0], partialTick) * brightness),
                (float) (interpolate(sprite.previousColor[1], sprite.color[1], partialTick) * brightness),
                (float) (interpolate(sprite.previousColor[2], sprite.color[2], partialTick) * brightness),
                (float) interpolate(sprite.previousColor[3], sprite.color[3], partialTick));
        double half = interpolate(sprite.previousSize, sprite.size, partialTick) * 0.5;
        double angle = interpolate(sprite.previousRotation, sprite.rotation, partialTick) * Math.PI / 180;
        double cosine = Math.cos(angle);
        double sine = Math.sin(angle);
        double x = interpolate(sprite.prevPosX, sprite.posX, partialTick) - cx;
        double y = interpolate(sprite.prevPosY, sprite.posY, partialTick) - cy;
        double z = interpolate(sprite.prevPosZ, sprite.posZ, partialTick) - cz;
        vertex(x, y, z, -half, -half, cosine, sine, rightX, rightZ, upX, upY, upZ, u1, v1);
        vertex(x, y, z, -half, half, cosine, sine, rightX, rightZ, upX, upY, upZ, u1, v0);
        vertex(x, y, z, half, half, cosine, sine, rightX, rightZ, upX, upY, upZ, u0, v0);
        vertex(x, y, z, half, -half, cosine, sine, rightX, rightZ, upX, upY, upZ, u0, v1);
    }

    private static void vertex(double x, double y, double z, double horizontal, double vertical, double cosine,
            double sine, double rightX, double rightZ, double upX, double upY, double upZ, double u, double v) {
        double rx = horizontal * cosine - vertical * sine;
        double ry = horizontal * sine + vertical * cosine;
        Tessellator.instance.addVertexWithUV(x + rightX * rx + upX * ry, y + upY * ry, z + rightZ * rx + upZ * ry, u,
                v);
    }

    private static String textureKey(ParticleSprite sprite) {
        return sprite.definition.texture == null ? sprite.definition.atlas : sprite.definition.texture.getCacheKey();
    }

    private static double distance(ParticleSprite sprite, Entity camera, float partialTick) {
        double x = interpolate(sprite.prevPosX, sprite.posX, partialTick)
                - interpolate(camera.lastTickPosX, camera.posX, partialTick);
        double y = interpolate(sprite.prevPosY, sprite.posY, partialTick)
                - interpolate(camera.lastTickPosY, camera.posY, partialTick);
        double z = interpolate(sprite.prevPosZ, sprite.posZ, partialTick)
                - interpolate(camera.lastTickPosZ, camera.posZ, partialTick);
        return x * x + y * y + z * z;
    }

    private static double interpolate(double previous, double current, float fraction) {
        return previous + (current - previous) * fraction;
    }

    private static final class TextureBinding implements AutoCloseable {
        private final TextureAsset asset;
        private TextureImage uploaded;
        private int id;

        private TextureBinding(TextureAsset asset) {
            this.asset = asset;
        }

        private boolean needsUpload() {
            return uploaded != asset.getContent().getValue();
        }

        private int texture() {
            if (needsUpload()) {
                BufferedImage image = asset.getContent().getValue().getImage();
                ByteBuffer pixels = BufferUtils.createByteBuffer(image.getWidth() * image.getHeight() * 4);
                for (int y = 0; y < image.getHeight(); y++) {
                    for (int x = 0; x < image.getWidth(); x++) {
                        int color = image.getRGB(x, y);
                        pixels.put((byte) (color >> 16)).put((byte) (color >> 8)).put((byte) color)
                                .put((byte) (color >> 24));
                    }
                }
                pixels.flip();
                if (id == 0) {
                    id = GL11.glGenTextures();
                }
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, id);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_CLAMP);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_CLAMP);
                GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, image.getWidth(), image.getHeight(), 0,
                        GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
                uploaded = asset.getContent().getValue();
            }
            return id;
        }

        @Override
        public void close() {
            if (id != 0) {
                GL11.glDeleteTextures(id);
                id = 0;
            }
            asset.close();
        }
    }
}
