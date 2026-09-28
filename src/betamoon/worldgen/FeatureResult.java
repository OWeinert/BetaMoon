package betamoon.worldgen;

/** Result of planning or committing one bounded feature. */
public final class FeatureResult {
    public static final String BLOCKED = "blocked";
    public static final String OUT_OF_BOUNDS = "out_of_bounds";
    public static final String UNLOADED_CHUNK = "unloaded_chunk";
    public static final String INVALID_GROUND = "invalid_ground";
    public static final String PROTECTED = "protected";
    public static final String BUDGET_EXCEEDED = "budget_exceeded";
    public static final String NO_CHANGES = "no_changes";
    public static final String RUNTIME_ERROR = "runtime_error";

    public final boolean placed;
    public final String reason;
    public final int blocksChanged;
    public final BlockPosition min;
    public final BlockPosition max;

    private FeatureResult(boolean placed, String reason, int blocksChanged, BlockPosition min, BlockPosition max) {
        this.placed = placed;
        this.reason = reason;
        this.blocksChanged = blocksChanged;
        this.min = min;
        this.max = max;
    }

    public static FeatureResult rejected(String reason) {
        return new FeatureResult(false, reason, 0, null, null);
    }

    public static FeatureResult placed(int blocksChanged, BlockPosition min, BlockPosition max) {
        return new FeatureResult(true, null, blocksChanged, min, max);
    }
}
