package betamoon.entity.motion;

import betamoon.entity.LuaEntityPart;
import net.minecraft.src.AxisAlignedBB;
import net.minecraft.src.Entity;
import net.minecraft.src.EntityPlayer;
import net.minecraft.src.World;

/** Immediate transform actions with no implicit simulation ticks or chunk generation. */
public final class EntityTransforms {
    public static final double MAX_MOTION = 16;
    public static final double SUPPORT_PROBE = 0.001;

    private EntityTransforms() {
    }

    public static String available(Entity entity) {
        EntitySimulationThread.requireCurrent();
        if (entity == null || entity.isDead || entity.worldObj == null) {
            return "entity_unavailable";
        }
        if (entity.worldObj.multiplayerWorld) {
            return "unsupported_environment";
        }
        return entity instanceof LuaEntityPart ? "unsupported_entity" : null;
    }

    public static String transform(Entity entity, EntityTransformRequest request) {
        String failure = available(entity);
        if (failure != null) {
            return failure;
        }
        if (entity instanceof EntityPlayer && request.pitch != null && Math.abs(request.pitch) > 90) {
            throw new IllegalArgumentException("Player pitch must be within -90..90 degrees");
        }
        double x = request.x == null ? entity.posX : request.x;
        double y = request.y == null ? entity.posY : request.y;
        double z = request.z == null ? entity.posZ : request.z;
        if (request.changesPosition()) {
            if (entity.ridingEntity != null || entity.riddenByEntity != null
                    || entity instanceof EntityPlayer && ((EntityPlayer) entity).isPlayerSleeping()) {
                return "unsupported_entity";
            }
            failure = EntityChunkMembership.checkSource(entity);
            if (failure != null) {
                return failure;
            }
            if (Math.abs(x) > EntitySpatialSafety.MAX_COORDINATE
                    || Math.abs(z) > EntitySpatialSafety.MAX_COORDINATE || y < -64 || y > 4096) {
                return "out_of_bounds";
            }
            AxisAlignedBB destination = offset(entity.boundingBox, x - entity.posX, y - entity.posY, z - entity.posZ);
            failure = EntitySpatialSafety.check(entity, destination);
            if (failure != null) {
                return failure;
            }
            if (!entity.worldObj.getCollidingBoundingBoxes(entity, destination).isEmpty()) {
                return "blocked";
            }
        }
        // Resolve protected state before changing anything, so an unavailable mapping
        // cannot produce a partially applied transform.
        if (request.resetFallDistance) {
            NativeEntityFields.fallDistance(entity);
        }
        if (request.changesPosition()) {
            entity.setPosition(x, y, z);
            EntityChunkMembership.synchronize(entity, entity.worldObj);
        }
        if (request.yaw != null) {
            entity.rotationYaw = request.yaw;
        }
        if (request.pitch != null) {
            entity.rotationPitch = request.pitch;
        }
        if (request.resetVelocity) {
            entity.motionX = 0;
            entity.motionY = 0;
            entity.motionZ = 0;
        }
        if (request.resetFallDistance) {
            NativeEntityFields.fallDistance(entity, 0);
        }
        if (request.snapInterpolation) {
            if (request.changesPosition()) {
                entity.prevPosX = entity.lastTickPosX = entity.posX;
                entity.prevPosY = entity.lastTickPosY = entity.posY;
                entity.prevPosZ = entity.lastTickPosZ = entity.posZ;
            }
            if (request.yaw != null) {
                entity.prevRotationYaw = entity.rotationYaw;
            }
            if (request.pitch != null) {
                entity.prevRotationPitch = entity.rotationPitch;
            }
        }
        return null;
    }

    public static String impulse(Entity entity, MotionVector delta) {
        String failure = available(entity);
        if (failure != null) {
            return failure;
        }
        MotionVector result = new MotionVector(entity.motionX + delta.x, entity.motionY + delta.y,
                entity.motionZ + delta.z);
        if (!delta.within(MAX_MOTION) || !result.within(MAX_MOTION)) {
            throw new IllegalArgumentException("Impulse and resulting velocity must be within -16..16");
        }
        entity.motionX = result.x;
        entity.motionY = result.y;
        entity.motionZ = result.z;
        return null;
    }

    public static String move(Entity entity, MotionVector delta) {
        String failure = available(entity);
        if (failure != null) {
            return failure;
        }
        if (!delta.within(MAX_MOTION)) {
            throw new IllegalArgumentException("Displacement components must be within -16..16");
        }
        if (entity.noClip || entity.ridingEntity != null || entity.riddenByEntity != null || entity instanceof EntityPlayer
                && ((EntityPlayer) entity).isPlayerSleeping()) {
            return "unsupported_entity";
        }
        failure = EntityChunkMembership.checkSource(entity);
        if (failure == null) {
            failure = EntitySpatialSafety.check(entity, EntitySpatialSafety.swept(entity, delta));
        }
        if (failure == null) {
            World world = entity.worldObj;
            entity.moveEntity(delta.x, delta.y, delta.z);
            EntityChunkMembership.synchronize(entity, world);
        }
        return failure;
    }

    public static String supportAvailable(Entity entity) {
        EntitySimulationThread.requireCurrent();
        if (entity == null || entity.isDead || entity.worldObj == null) {
            return "entity_unavailable";
        }
        return EntitySpatialSafety.check(entity, supportArea(entity));
    }

    public static boolean supported(Entity entity) {
        return !entity.worldObj.getCollidingBoundingBoxes(entity, supportArea(entity)).isEmpty();
    }

    private static AxisAlignedBB supportArea(Entity entity) {
        AxisAlignedBB body = entity.boundingBox;
        return AxisAlignedBB.getBoundingBox(body.minX, body.minY - SUPPORT_PROBE, body.minZ,
                body.maxX, body.minY, body.maxZ);
    }

    private static AxisAlignedBB offset(AxisAlignedBB body, double x, double y, double z) {
        return AxisAlignedBB.getBoundingBox(body.minX + x, body.minY + y, body.minZ + z,
                body.maxX + x, body.maxY + y, body.maxZ + z);
    }
}
