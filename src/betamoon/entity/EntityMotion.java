package betamoon.entity;

import net.minecraft.src.Entity;

/** Minimal previous-frame maintenance for an inactive, non-simulating bridge. */
final class EntityMotion {
    private EntityMotion() {
    }

    static void stationaryTick(Entity entity) {
        frozenTick(entity);
        entity.ticksExisted++;
        entity.motionX = entity.motionY = entity.motionZ = 0;
    }

    static void manualTick(Entity entity) {
        frozenTick(entity);
        entity.ticksExisted++;
    }

    static void frozenTick(Entity entity) {
        entity.prevPosX = entity.lastTickPosX = entity.posX;
        entity.prevPosY = entity.lastTickPosY = entity.posY;
        entity.prevPosZ = entity.lastTickPosZ = entity.posZ;
        entity.prevRotationYaw = entity.rotationYaw;
        entity.prevRotationPitch = entity.rotationPitch;
    }
}
