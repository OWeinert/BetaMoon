package betamoon.tileentity;

/** Outcome of atomically publishing a prepared recipe inventory. */
public enum RecipeCommitResult {
    APPLIED(null), INVALID_INVENTORY("invalid_inventory"), REENTRANT("reentrant_apply"), INVENTORY_CHANGED(
            "inputs_changed");

    private final String failureReason;

    RecipeCommitResult(String failureReason) {
        this.failureReason = failureReason;
    }

    public boolean wasApplied() {
        return this == APPLIED;
    }

    public String getFailureReason() {
        return failureReason;
    }
}
