package betamoon.entity.motion;

import net.minecraft.src.EntityLiving;
import net.minecraft.src.EntityPlayer;

/**
 * Detached body state used by producers, forces, attachment and observation.
 */
public final class EntityMotionState {
    public final MotionVector position;
    public final MotionVector velocity;
    public final float yaw;
    public final float pitch;
    public final float fallDistance;
    public final boolean onGround;
    public final boolean collidedHorizontally;
    public final boolean collidedVertically;
    public final boolean inWater;
    public final boolean inLava;
    public final boolean mounted;
    public final boolean sleeping;

    private EntityMotionState(EntityLiving entity, MotionVector velocity) {
        this.position = new MotionVector(entity.posX, entity.posY, entity.posZ);
        this.velocity = velocity;
        this.yaw = entity.rotationYaw;
        this.pitch = entity.rotationPitch;
        this.fallDistance = NativeEntityFields.fallDistance(entity);
        this.onGround = entity.onGround;
        this.collidedHorizontally = entity.isCollidedHorizontally;
        this.collidedVertically = entity.isCollidedVertically;
        this.inWater = entity.isInWater();
        this.inLava = entity.handleLavaMovement();
        this.mounted = entity.ridingEntity != null;
        this.sleeping = entity instanceof EntityPlayer && ((EntityPlayer) entity).isPlayerSleeping();
    }

    private EntityMotionState(EntityMotionState original, MotionVector velocity, Float yaw, Float pitch,
            Float fallDistance) {
        position = original.position;
        this.velocity = velocity;
        this.yaw = yaw == null ? original.yaw : yaw;
        this.pitch = pitch == null ? original.pitch : pitch;
        this.fallDistance = fallDistance == null ? original.fallDistance : fallDistance;
        onGround = original.onGround;
        collidedHorizontally = original.collidedHorizontally;
        collidedVertically = original.collidedVertically;
        inWater = original.inWater;
        inLava = original.inLava;
        mounted = original.mounted;
        sleeping = original.sleeping;
    }

    public static EntityMotionState capture(EntityLiving entity) {
        return new EntityMotionState(entity, new MotionVector(entity.motionX, entity.motionY, entity.motionZ));
    }

    public EntityMotionState prepared(MotionVector velocity, EntityMotionCommand command, boolean trackFall) {
        Float fall = null;
        if (!trackFall) {
            fall = 0F;
        } else if (command != null && command.fallDistance != null) {
            fall = command.fallDistance.floatValue();
        }
        return new EntityMotionState(this, velocity, command == null ? null : command.yaw,
                command == null ? null : command.pitch, fall);
    }
}
