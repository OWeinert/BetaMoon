package betamoon.client.assets;

import betamoon.assets.TextureAnimation;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.ImageIO;

/** Validated, immutable frames decoded from one texture source. */
public final class TextureFrames {
    private final TextureImage source;
    private final BufferedImage[] frames;
    private final int[] sequence;
    private final TextureAnimation animation;

    private TextureFrames(TextureImage source, BufferedImage[] frames, int[] sequence, TextureAnimation animation) {
        this.source = source;
        this.frames = frames;
        this.sequence = sequence;
        this.animation = animation;
    }

    public static TextureFrames decode(TextureImage source, TextureAnimation animation) throws IOException {
        BufferedImage image = source.getImage();
        if (animation == null) {
            return new TextureFrames(source, new BufferedImage[]{image}, new int[]{0}, null);
        }
        int width = image.getWidth();
        int height = image.getHeight();
        if (width < 1 || height < width || height % width != 0) {
            throw new IOException(
                    "Vertical texture strip must have width W and height W × N; got " + width + " × " + height);
        }
        int count = height / width;
        if (count > 256) {
            throw new IOException("Texture strip exceeds 256 frames: " + count);
        }
        BufferedImage[] frames = new BufferedImage[count];
        for (int i = 0; i < count; i++) {
            BufferedImage frame = new BufferedImage(width, width, BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = frame.createGraphics();
            try {
                graphics.drawImage(image, 0, -i * width, null);
            } finally {
                graphics.dispose();
            }
            frames[i] = frame;
        }
        int[] declared = animation.sequence();
        int[] sequence = declared == null ? new int[count] : declared;
        if (declared == null) {
            for (int i = 0; i < count; i++) {
                sequence[i] = i;
            }
        } else {
            for (int i = 0; i < sequence.length; i++) {
                if (sequence[i] < 1 || sequence[i] > count) {
                    throw new IOException("animation.sequence[" + (i + 1) + "] is outside 1.." + count);
                }
                sequence[i]--;
            }
        }
        if (animation.firstFrameOnly()) {
            sequence = new int[]{0};
        }
        return new TextureFrames(source, frames, sequence, animation);
    }

    public BufferedImage frame(int position) {
        return frames[sequence[position]];
    }

    public int sequenceLength() {
        return sequence.length;
    }

    /**
     * Source-strip index for consumers with an independent per-instance playback
     * cursor.
     */
    public int sourceFrame(int position) {
        return sequence[position];
    }

    public TextureAnimation animation() {
        return animation;
    }

    public ByteArrayInputStream open(int position) throws IOException {
        if (animation == null) {
            return source.open();
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        if (!ImageIO.write(frame(position), "png", output)) {
            throw new IOException("PNG encoder unavailable");
        }
        return new ByteArrayInputStream(output.toByteArray());
    }
}
