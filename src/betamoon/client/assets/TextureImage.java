package betamoon.client.assets;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

/**
 * Validated PNG with detached bytes for Minecraft's normal image reader and
 * refresh paths.
 */
public final class TextureImage {
    public static final int MAX_PIXELS = 262144;
    private final BufferedImage image;
    private final byte[] png;

    private TextureImage(BufferedImage image, byte[] png) {
        this.image = image;
        this.png = png;
    }

    public static TextureImage decode(byte[] bytes) throws IOException {
        if (bytes.length < 8 || bytes[0] != (byte) 137 || bytes[1] != 80 || bytes[2] != 78 || bytes[3] != 71) {
            throw new IOException("Texture must be a PNG image");
        }
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new IOException("PNG image could not be decoded");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input);
                long pixels = (long) reader.getWidth(0) * reader.getHeight(0);
                if (pixels <= 0 || pixels > MAX_PIXELS) {
                    throw new IOException("Texture exceeds the current renderer limit of " + MAX_PIXELS + " pixels");
                }
                BufferedImage image = reader.read(0);
                return new TextureImage(image, bytes.clone());
            } finally {
                reader.dispose();
            }
        }
    }

    public static TextureImage missing() {
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                image.setRGB(x, y, ((x / 8 + y / 8) % 2 == 0) ? 0xffff00ff : 0xff000000);
            }
        }
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            ImageIO.write(image, "png", output);
            return new TextureImage(image, output.toByteArray());
        } catch (IOException error) {
            throw new IllegalStateException("Cannot create missing texture", error);
        }
    }

    public BufferedImage getImage() {
        return image;
    }

    public ByteArrayInputStream open() {
        return new ByteArrayInputStream(png);
    }

    public boolean sameContent(TextureImage other) {
        return Arrays.equals(png, other.png);
    }
}
