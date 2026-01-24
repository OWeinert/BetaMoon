package betamoon.resources;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import net.minecraft.src.TextureFX;

/**
 * Fallback texture FX implementation when ModTextureStatic is unavailable.
 */
public class BetaMoonTextureStatic extends TextureFX {
    private final BufferedImage source;

    public BetaMoonTextureStatic(int iconIndex, int atlasId, BufferedImage image) {
        super(iconIndex);
        this.tileImage = atlasId;
        this.source = scaleTo16(image);
        ensureBuffer();
        updateImageData();
    }

    public void onTick() {
        updateImageData();
    }

    public void updateAnimation() {
        updateImageData();
    }

    private void ensureBuffer() {
        if (this.imageData == null || this.imageData.length < 1024) {
            this.imageData = new byte[1024];
        }
    }

    private void updateImageData() {
        if (source == null) {
            return;
        }
        int[] pixels = new int[256];
        source.getRGB(0, 0, 16, 16, pixels, 0, 16);
        for (int i = 0; i < pixels.length; i++) {
            int argb = pixels[i];
            int a = (argb >> 24) & 0xFF;
            int r = (argb >> 16) & 0xFF;
            int g = (argb >> 8) & 0xFF;
            int b = argb & 0xFF;
            if (this.anaglyphEnabled) {
                int r2 = (r * 30 + g * 59 + b * 11) / 100;
                int g2 = (r * 30 + g * 70) / 100;
                int b2 = (r * 30 + b * 70) / 100;
                r = r2;
                g = g2;
                b = b2;
            }
            int index = i * 4;
            this.imageData[index] = (byte) r;
            this.imageData[index + 1] = (byte) g;
            this.imageData[index + 2] = (byte) b;
            this.imageData[index + 3] = (byte) a;
        }
    }

    private static BufferedImage scaleTo16(BufferedImage input) {
        if (input == null) {
            return null;
        }
        if (input.getWidth() == 16 && input.getHeight() == 16) {
            return input;
        }
        BufferedImage scaled = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = scaled.createGraphics();
        try {
            g2.drawImage(input, 0, 0, 16, 16, null);
        } finally {
            g2.dispose();
        }
        return scaled;
    }
}
