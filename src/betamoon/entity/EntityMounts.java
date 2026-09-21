package betamoon.entity;

import net.minecraft.src.Entity;
import net.minecraft.src.EntityPlayer;

/** Maintains native single-seat riding invariants for custom entity operations. */
public final class EntityMounts {
    private EntityMounts() {
    }

    public static boolean addPassenger(Entity vehicle, Entity passenger, EntityMountDefinition definition) {
        if (vehicle == null || passenger == null || definition == null || vehicle == passenger
                || passenger instanceof LuaEntityPart || vehicle.isDead || passenger.isDead || vehicle.worldObj == null
                || vehicle.worldObj != passenger.worldObj || vehicle.riddenByEntity != null
                || passenger.ridingEntity != null || !allowed(passenger, definition)
                || createsCycle(vehicle, passenger)) {
            return false;
        }
        passenger.mountEntity(vehicle);
        vehicle.updateRiderPosition();
        return vehicle.riddenByEntity == passenger && passenger.ridingEntity == vehicle;
    }

    public static boolean removePassenger(Entity vehicle) {
        if (vehicle == null || vehicle.riddenByEntity == null) {
            return false;
        }
        Entity passenger = vehicle.riddenByEntity;
        passenger.mountEntity(null);
        return vehicle.riddenByEntity == null && passenger.ridingEntity == null;
    }

    public static boolean dismount(Entity passenger) {
        if (passenger == null || passenger.ridingEntity == null) {
            return false;
        }
        passenger.mountEntity(null);
        return passenger.ridingEntity == null;
    }

    public static void detach(Entity entity) {
        removePassenger(entity);
        dismount(entity);
    }

    private static boolean allowed(Entity passenger, EntityMountDefinition definition) {
        return passenger instanceof EntityPlayer
                ? definition.allowPlayers : definition.allowEntities;
    }

    private static boolean createsCycle(Entity vehicle, Entity passenger) {
        for (Entity current = vehicle; current != null; current = current.ridingEntity) {
            if (current == passenger) {
                return true;
            }
        }
        return false;
    }
}
