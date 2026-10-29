package betamoon.gui.interop;

import betamoon.gui.framework.GuiTextureCache;
import betamoon.interop.binding.NativeAccessFailure;
import java.nio.ByteBuffer;
import java.util.Arrays;
import org.lwjgl.opengl.GL11;

/**
 * One independently owned cache revision; replacing an image cannot delete
 * another live lease's texture.
 */
public final class PreparedClientImage implements AutoCloseable {
    private static final byte[] SIGNATURE = {(byte) 137, 80, 78, 71, 13, 10, 26, 10};
    private final GuiTextureCache cache = new GuiTextureCache();
    private final GuiTextureCache.Texture texture;

    PreparedClientImage(byte[] encoded, boolean nearest) {
        validate(encoded);
        texture = cache.get("prepared-client-image", encoded.clone(), 1);
        if (texture == null) {
            throw new NativeAccessFailure("native_client_resource_unavailable", "PNG could not be decoded");
        }
        try {
            if (nearest) {
                GL11.glPushAttrib(GL11.GL_TEXTURE_BIT);
                try {
                    GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture.getTextureId());
                    GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
                    GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
                } finally {
                    GL11.glPopAttrib();
                }
            }
        } catch (RuntimeException | Error failure) {
            cache.clear();
            throw failure;
        }
    }

    public int width() {
        return texture.getWidth();
    }

    public int height() {
        return texture.getHeight();
    }

    public int textureId() {
        return texture.getTextureId();
    }

    GuiTextureCache.Texture texture() {
        return texture;
    }

    @Override
    public void close() {
        cache.clear();
    }

    private static void validate(byte[] bytes) {
        if (bytes == null || bytes.length < 33 || bytes.length > 4 * 1024 * 1024
                || !Arrays.equals(SIGNATURE, Arrays.copyOf(bytes, 8)) || bytes[12] != 'I' || bytes[13] != 'H'
                || bytes[14] != 'D' || bytes[15] != 'R') {
            throw new NativeAccessFailure("invalid_client_image", "use a PNG of at most 4 MiB");
        }
        ByteBuffer header = ByteBuffer.wrap(bytes);
        int width = header.getInt(16);
        int height = header.getInt(20);
        if (width <= 0 || height <= 0 || width > 2048 || height > 2048 || (long) width * height > 4194304) {
            throw new NativeAccessFailure("invalid_client_image", "PNG dimensions exceed 2048 x 2048");
        }
    }
}
