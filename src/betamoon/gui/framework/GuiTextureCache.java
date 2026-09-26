package betamoon.gui.framework;

import betamoon.io.ImageIo;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

/** Owns OpenGL textures loaded from script-controlled image files. */
public final class GuiTextureCache {
    private static final GuiTextureCache SHARED = new GuiTextureCache();

    private final Map<String, Entry> entries = new HashMap<String, Entry>();

    public static GuiTextureCache shared() {
        return SHARED;
    }

    public synchronized Texture get(File file) {
        if (file == null) {
            return null;
        }
        File absolute = file.getAbsoluteFile();
        String key = absolute.getPath();
        long modified = absolute.isFile() ? absolute.lastModified() : -1L;
        long length = absolute.isFile() ? absolute.length() : -1L;
        Entry cached = entries.get(key);
        if (cached != null && cached.modified == modified && cached.length == length) {
            return cached.texture;
        }
        if (cached != null) {
            delete(cached.texture);
        }
        Texture texture = load(absolute);
        entries.put(key, new Entry(modified, length, texture));
        return texture;
    }

    public synchronized Texture get(String key, byte[] pngBytes, long revision) {
        if (key == null || pngBytes == null) {
            return null;
        }
        long contentRevision = revision * 31L + Arrays.hashCode(pngBytes);
        Entry cached = entries.get(key);
        if (cached != null && cached.modified == contentRevision && cached.length == pngBytes.length) {
            return cached.texture;
        }
        if (cached != null) {
            delete(cached.texture);
        }
        Texture texture = load(pngBytes);
        entries.put(key, new Entry(contentRevision, pngBytes.length, texture));
        return texture;
    }

    public synchronized void invalidate(File file) {
        if (file == null) {
            return;
        }
        Entry removed = entries.remove(file.getAbsoluteFile().getPath());
        if (removed != null) {
            delete(removed.texture);
        }
    }

    public synchronized void clear() {
        for (Entry entry : entries.values()) {
            delete(entry.texture);
        }
        entries.clear();
    }

    private static Texture load(File file) {
        if (!file.isFile() || !file.getName().toLowerCase(Locale.ROOT).endsWith(".png")) {
            return null;
        }
        BufferedImage image;
        try {
            image = ImageIo.loadImage(file);
        } catch (IOException exception) {
            return null;
        }
        return upload(image);
    }

    private static Texture load(byte[] pngBytes) {
        BufferedImage image;
        try {
            image = ImageIo.loadImage(pngBytes);
        } catch (IOException exception) {
            return null;
        }
        return upload(image);
    }

    private static Texture upload(BufferedImage image) {
        if (image == null || image.getWidth() <= 0 || image.getHeight() <= 0) {
            return null;
        }
        int width = image.getWidth();
        int height = image.getHeight();
        int[] pixels = new int[width * height];
        image.getRGB(0, 0, width, height, pixels, 0, width);
        ByteBuffer buffer = BufferUtils.createByteBuffer(width * height * 4);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int pixel = pixels[y * width + x];
                buffer.put((byte) (pixel >> 16 & 0xFF));
                buffer.put((byte) (pixel >> 8 & 0xFF));
                buffer.put((byte) (pixel & 0xFF));
                buffer.put((byte) (pixel >> 24 & 0xFF));
            }
        }
        buffer.flip();
        int textureId = GL11.glGenTextures();
        GL11.glPushAttrib(GL11.GL_TEXTURE_BIT);
        try {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_CLAMP);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_CLAMP);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, width, height, 0, GL11.GL_RGBA,
                    GL11.GL_UNSIGNED_BYTE, buffer);
        } finally {
            GL11.glPopAttrib();
        }
        return new Texture(textureId, width, height);
    }

    private static void delete(Texture texture) {
        if (texture != null && texture.textureId > 0) {
            GL11.glDeleteTextures(texture.textureId);
        }
    }

    public static final class Texture {
        private final int textureId;
        private final int width;
        private final int height;

        private Texture(int textureId, int width, int height) {
            this.textureId = textureId;
            this.width = width;
            this.height = height;
        }

        public int getTextureId() {
            return textureId;
        }

        public int getWidth() {
            return width;
        }

        public int getHeight() {
            return height;
        }
    }

    private static final class Entry {
        private final long modified;
        private final long length;
        private final Texture texture;

        private Entry(long modified, long length, Texture texture) {
            this.modified = modified;
            this.length = length;
            this.texture = texture;
        }
    }
}
