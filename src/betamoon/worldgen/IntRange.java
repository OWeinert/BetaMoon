package betamoon.worldgen;

import java.util.Random;

/** Validated inclusive integer range. */
public final class IntRange {
    public final int min;
    public final int max;

    public IntRange(int min, int max) {
        if (min > max) {
            throw new IllegalArgumentException("Range min must be <= max");
        }
        this.min = min;
        this.max = max;
    }

    public int sample(Random random) {
        return min == max ? min : min + random.nextInt(max - min + 1);
    }
}
