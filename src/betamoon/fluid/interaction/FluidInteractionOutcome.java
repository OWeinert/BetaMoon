package betamoon.fluid.interaction;

import betamoon.fluid.FluidState;

/** One ordered, metadata-aware result clause. */
public final class FluidInteractionOutcome {
    private final Boolean source;
    private final Boolean falling;
    private final int minimumLevel;
    private final int maximumLevel;
    private final int resultBlockId;

    public FluidInteractionOutcome(Boolean source, Boolean falling, int minimumLevel, int maximumLevel,
            int resultBlockId) {
        if (minimumLevel < 0 || maximumLevel > 7 || minimumLevel > maximumLevel) {
            throw new IllegalArgumentException("Fluid interaction levels must form a range within 0..7.");
        }
        if (resultBlockId < 0 || resultBlockId > 255) {
            throw new IllegalArgumentException("Fluid interaction result block ID must be within 0..255.");
        }
        this.source = source;
        this.falling = falling;
        this.minimumLevel = minimumLevel;
        this.maximumLevel = maximumLevel;
        this.resultBlockId = resultBlockId;
    }

    public boolean matches(FluidState state) {
        return (source == null || source.booleanValue() == state.isSource())
                && (falling == null || falling.booleanValue() == state.isFalling())
                && state.level() >= minimumLevel && state.level() <= maximumLevel;
    }

    public int resultBlockId() {
        return resultBlockId;
    }
}
