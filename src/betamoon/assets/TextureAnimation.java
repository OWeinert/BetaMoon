package betamoon.assets;

import java.util.Arrays;

/** Immutable playback metadata; source pixels and native bindings are separate. */
public final class TextureAnimation {
    public static final TextureAnimation FIRST_FRAME = new TextureAnimation();

    private final int frameTicks;
    private final int[] sequence;
    private final boolean unpaused;
    private final String cacheKey;

    private TextureAnimation() {
        frameTicks = 0;
        sequence = null;
        unpaused = false;
        cacheKey = "first-frame";
    }

    public TextureAnimation(int frameTicks, int[] sequence, boolean unpaused) {
        if (frameTicks < 1 || frameTicks > 1200) {
            throw new IllegalArgumentException("animation.frameTicks must be between 1 and 1200");
        }
        if (sequence != null && (sequence.length == 0 || sequence.length > 256)) {
            throw new IllegalArgumentException("animation.sequence must contain 1..256 entries");
        }
        this.frameTicks = frameTicks;
        this.sequence = sequence == null ? null : sequence.clone();
        this.unpaused = unpaused;
        cacheKey = frameTicks + ":" + unpaused + ":" + Arrays.toString(this.sequence);
    }

    public int frameTicks() {
        return frameTicks;
    }

    public int[] sequence() {
        return sequence == null ? null : sequence.clone();
    }

    public boolean unpausedOnly() {
        return unpaused;
    }

    public boolean firstFrameOnly() {
        return frameTicks == 0;
    }

    public String cacheKey() {
        return cacheKey;
    }
}
