package betamoon.entity;

import betamoon.fluid.contact.FluidContactRuntime;
import net.minecraft.src.Entity;

/** Previous-frame maintenance and declarative contact for manually ticked entities. */
final class EntityMotion {
    private EntityMotion() {
    }

    static void stationaryTick(Entity entity) {
        frozenTick(entity);
        entity.ticksExisted++;
        entity.motionX = entity.motionY = entity.motionZ = 0;
        FluidContactRuntime.tickWithoutNativeUpdate(entity);
    }

    static void manualTick(Entity entity) {
        frozenTick(entity);
        entity.ticksExisted++;
        FluidContactRuntime.tickWithoutNativeUpdate(entity);
    }

    static void frozenTick(Entity entity) {
        entity.prevPosX = entity.lastTickPosX = entity.posX;
        entity.prevPosY = entity.lastTickPosY = entity.posY;
        entity.prevPosZ = entity.lastTickPosZ = entity.posZ;
        entity.prevRotationYaw = entity.rotationYaw;
        entity.prevRotationPitch = entity.rotationPitch;
    }
}
