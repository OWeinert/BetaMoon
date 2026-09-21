package betamoon.entity;

import betamoon.assets.AssetKey;
import net.minecraft.src.Entity;
import net.minecraft.src.World;

/** Server-side spawn service; it never forces an unloaded chunk into memory. */
public final class EntitySpawner {
    private EntitySpawner() {
    }

    public static Result spawn(World world, AssetKey key, double x, double y, double z, float yaw, float pitch) {
        return spawn(world, key, x, y, z, yaw, pitch, null);
    }

    public static Result spawn(World world, AssetKey key, double x, double y, double z, float yaw, float pitch,
            Entity owner) {
        return spawn(world, key, x, y, z, yaw, pitch, owner, "script");
    }

    public static Result spawn(World world, AssetKey key, double x, double y, double z, float yaw, float pitch,
            Entity owner, String reason) {
        EntityTypeDefinition definition = EntityTypeRegistry.find(key);
        if (definition == null) {
            throw new IllegalArgumentException("Entity type not registered for spawning: " + key);
        }
        if (world.multiplayerWorld) {
            return new Result(null, "client_world");
        }
        if (y < -64 || y > 4096) {
            return new Result(null, "outside_world");
        }
        if (!world.blockExists((int) Math.floor(x), 64, (int) Math.floor(z))) {
            return new Result(null, "chunk_unloaded");
        }
        Entity entity;
        switch (definition.kind) {
            case PROJECTILE:
                entity = new LuaProjectileEntity(world);
                break;
            case LIVING:
                entity = new LuaLivingEntity(world);
                break;
            case PICKUP:
                entity = new LuaPickupEntity(world);
                break;
            case PROP:
                entity = new LuaPropEntity(world);
                break;
            default:
                throw new AssertionError(definition.kind);
        }
        if (entity instanceof LuaPickupEntity) {
            ((LuaPickupEntity) entity).initializeItem(definition);
        } else {
            ((TypedEntity) entity).entityState().attach(definition);
        }
        if (entity instanceof LuaLivingEntity) {
            ((LuaLivingEntity) entity).health = definition.living.maxHealth;
        }
        entity.width = definition.width;
        entity.height = definition.height;
        entity.rotationYaw = yaw;
        entity.rotationPitch = pitch;
        entity.prevRotationYaw = yaw;
        entity.prevRotationPitch = pitch;
        entity.setPosition(x, y, z);
        if (entity instanceof LuaProjectileEntity) {
            LuaProjectileEntity projectile = (LuaProjectileEntity) entity;
            projectile.setOwner(owner);
            double yawRadians = Math.toRadians(yaw);
            double pitchRadians = Math.toRadians(pitch);
            double speed = definition.projectile.speed;
            projectile.motionX = -Math.sin(yawRadians) * Math.cos(pitchRadians) * speed;
            projectile.motionY = -Math.sin(pitchRadians) * speed;
            projectile.motionZ = Math.cos(yawRadians) * Math.cos(pitchRadians) * speed;
        }
        if (!world.getCollidingBoundingBoxes(entity, entity.boundingBox).isEmpty()) {
            return new Result(null, "blocked");
        }
        if (!world.entityJoinedWorld(entity)) {
            return new Result(null, "chunk_unloaded");
        }
        EntityLifecycleEvents.spawned(entity, reason);
        if (entity.isDead) {
            return new Result(null, "removed_on_spawn");
        }
        return new Result(entity, null);
    }

    public static final class Result {
        public final Entity entity;
        public final String reason;

        private Result(Entity entity, String reason) {
            this.entity = entity;
            this.reason = reason;
        }
    }
}
