package betamoon.entity.motion;

/** Post-step detached result, independent of any callback facade lifetime. */
public final class EntityMotionResult {
    public final String sourceOwner;
    public final String sourceKey;
    public final String mode;
    public final MotionVector requestedDelta;
    public final MotionVector appliedDelta;
    public final MotionVector velocityDelta;
    public final EntityMotionState movement;
    public final String blockedReason;
    public final String forceStatus;

    public EntityMotionResult(String sourceOwner, String sourceKey, String mode, MotionVector requestedDelta,
            MotionVector appliedDelta, MotionVector velocityDelta, EntityMotionState movement, String blockedReason,
            String forceStatus) {
        this.sourceOwner = sourceOwner;
        this.sourceKey = sourceKey;
        this.mode = mode;
        this.requestedDelta = requestedDelta;
        this.appliedDelta = appliedDelta;
        this.velocityDelta = velocityDelta;
        this.movement = movement;
        this.blockedReason = blockedReason;
        this.forceStatus = forceStatus;
    }
}
