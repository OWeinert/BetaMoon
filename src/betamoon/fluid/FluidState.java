package betamoon.fluid;

/** Metadata interpretation shared by simulation, rendering, buckets and APIs. */
public final class FluidState {
    public static final int MIN_METADATA = 0;
    public static final int MAX_METADATA = 15;
    public static final int FALLING_FLAG = 8;

    private final int metadata;

    private FluidState(int metadata) {
        this.metadata = metadata;
    }

    public static FluidState of(int metadata) {
        if (metadata < MIN_METADATA || metadata > MAX_METADATA) {
            throw new IllegalArgumentException("Fluid metadata must be between 0 and 15, got " + metadata + ".");
        }
        return new FluidState(metadata);
    }

    public int metadata() {
        return metadata;
    }

    public boolean isSource() {
        return metadata == 0;
    }

    public boolean isFalling() {
        return metadata >= FALLING_FLAG;
    }

    /** Effective horizontal decay, matching Beta 1.7.3's treatment of falling states. */
    public int level() {
        return isFalling() ? 0 : metadata;
    }

    /** Fraction of a block left as air above this fluid surface. */
    public float airFraction() {
        return (level() + 1) / 9.0F;
    }

    public float surfaceHeight() {
        return 1.0F - airFraction();
    }

    public static int fallingFrom(int metadata) {
        return of(metadata).isFalling() ? metadata : metadata + FALLING_FLAG;
    }
}
