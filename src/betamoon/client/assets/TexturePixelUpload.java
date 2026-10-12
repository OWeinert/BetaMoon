package betamoon.client.assets;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import net.minecraft.src.GLAllocation;
import org.lwjgl.opengl.GL11;

/** Uploads one resolved frame into a bound atlas region or standalone texture. */
public final class TexturePixelUpload {
    private static ByteBuffer uploadBuffer;

    private TexturePixelUpload() {
    }

    public static void upload(BufferedImage source, int x, int y, int width, int height, boolean anaglyph,
            boolean mipmaps) {
        for (int level = 0; level <= (mipmaps ? 4 : 0); level++) {
            int scaledWidth = width >> level;
            int scaledHeight = height >> level;
            if (scaledWidth < 1 || scaledHeight < 1) {
                break;
            }
            BufferedImage scaled = new BufferedImage(scaledWidth, scaledHeight, BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = scaled.createGraphics();
            try {
                graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                        RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                graphics.drawImage(source, 0, 0, scaledWidth, scaledHeight, null);
            } finally {
                graphics.dispose();
            }
            int[] pixels = new int[scaledWidth * scaledHeight];
            scaled.getRGB(0, 0, scaledWidth, scaledHeight, pixels, 0, scaledWidth);
            ByteBuffer buffer = buffer(pixels.length * 4);
            for (int argb : pixels) {
                int red = argb >> 16 & 255;
                int green = argb >> 8 & 255;
                int blue = argb & 255;
                if (anaglyph) {
                    int originalRed = red;
                    red = (originalRed * 30 + green * 59 + blue * 11) / 100;
                    int originalGreen = green;
                    green = (originalRed * 30 + originalGreen * 70) / 100;
                    blue = (originalRed * 30 + blue * 70) / 100;
                }
                buffer.put((byte) red);
                buffer.put((byte) green);
                buffer.put((byte) blue);
                buffer.put((byte) (argb >> 24));
            }
            buffer.flip();
            GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, level, x >> level, y >> level, scaledWidth, scaledHeight,
                    GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buffer);
        }
    }

    private static ByteBuffer buffer(int bytes) {
        if (uploadBuffer == null || uploadBuffer.capacity() < bytes) {
            uploadBuffer = GLAllocation.createDirectByteBuffer(bytes);
        }
        uploadBuffer.clear();
        return uploadBuffer;
    }
}
