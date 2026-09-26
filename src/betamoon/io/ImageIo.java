package betamoon.io;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;

/**
 * Shared image loading helpers.
 */
public final class ImageIo {
    private ImageIo() {
    }

    /**
     * Loads an image file if it exists.
     */
    public static BufferedImage loadImage(File file) throws IOException {
        if (file == null || !file.isFile()) {
            return null;
        }
        return ImageIO.read(file);
    }

    /** Loads an image from in-memory encoded bytes. */
    public static BufferedImage loadImage(byte[] bytes) throws IOException {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        return ImageIO.read(new ByteArrayInputStream(bytes));
    }
}
