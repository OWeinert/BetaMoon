package betamoon.multiblock;

/** An interaction result is separate from a structural match report. */
public final class MultiblockActivationResult {
    public final boolean activated;
    public final boolean handled;
    public final String reason;
    public final MultiblockInstance instance;

    public MultiblockActivationResult(boolean activated, boolean handled, String reason, MultiblockInstance instance) {
        this.activated = activated;
        this.handled = handled;
        this.reason = reason;
        this.instance = instance;
    }
}
