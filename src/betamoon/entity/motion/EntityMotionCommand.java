package betamoon.entity.motion;

import betamoon.entity.modifier.EntityRuleSet;

/** A validated one-step command; no field is a retained gameplay override. */
public final class EntityMotionCommand {
    public enum Mode {
        NATIVE, MANUAL
    }

    public final Mode mode;
    public final MotionVector velocity;
    public final Float yaw;
    public final Float pitch;
    public final Double fallDistance;
    public final EntityRuleSet rules;

    public EntityMotionCommand(Mode mode, MotionVector velocity, Float yaw, Float pitch, Double fallDistance,
            EntityRuleSet rules) {
        if (mode == null || rules == null) {
            throw new IllegalArgumentException("Motion mode and rule contributions are required");
        }
        if (velocity != null && !velocity.within(16)) {
            throw new IllegalArgumentException("Motion velocity components must be within -16..16");
        }
        if (yaw != null && !Float.isFinite(yaw) || pitch != null && !Float.isFinite(pitch)) {
            throw new IllegalArgumentException("Motion rotation must remain finite");
        }
        if (fallDistance != null && (!Double.isFinite(fallDistance) || fallDistance < 0 || fallDistance > 65535)) {
            throw new IllegalArgumentException("Motion fallDistance must be within 0..65535");
        }
        this.mode = mode;
        this.velocity = velocity;
        this.yaw = yaw;
        this.pitch = pitch;
        this.fallDistance = fallDistance;
        this.rules = rules;
    }
}
