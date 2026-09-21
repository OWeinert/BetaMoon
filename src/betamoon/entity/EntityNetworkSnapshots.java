package betamoon.entity;

import betamoon.network.protocol.EntitySpawnMessage;
import betamoon.network.protocol.EntityTransform;
import betamoon.network.protocol.NetworkEntityKind;
import betamoon.network.protocol.PresentationSnapshot;
import net.minecraft.src.Entity;
import net.minecraft.src.EntityLiving;

/** Builds authoritative full snapshots without depending on a packet transport. */
public final class EntityNetworkSnapshots {
    private EntityNetworkSnapshots() {
    }

    public static EntitySpawnMessage spawn(Entity entity) {
        if (!(entity instanceof TypedEntity) || entity.worldObj == null) {
            throw new IllegalArgumentException("A live BetaMoon entity is required");
        }
        EntityInstanceState state = ((TypedEntity) entity).entityState();
        EntityTypeDefinition definition = state.definition();
        if (definition == null) {
            throw new IllegalStateException("Cannot synchronize an entity with a missing definition");
        }
        EntityPresentationState presentationState = state.presentationIfPresent();
        PresentationSnapshot presentation = presentationState == null ? null
                : presentationState.networkSnapshot(definition.render, entity.ticksExisted);
        int health = entity instanceof EntityLiving ? ((EntityLiving) entity).health : state.health();
        return new EntitySpawnMessage(entity.entityId, definition.key.toString(), state.identity(),
                kind(definition.kind), entity.worldObj.worldProvider.worldType, state.data().revision(), health,
                new EntityTransform(entity.posX, entity.posY, entity.posZ, entity.rotationYaw, entity.rotationPitch,
                        entity.motionX, entity.motionY, entity.motionZ),
                presentation, state.data().networkSnapshot(definition));
    }

    private static NetworkEntityKind kind(EntityKind kind) {
        switch (kind) {
            case PROP:
                return NetworkEntityKind.PROP;
            case PROJECTILE:
                return NetworkEntityKind.PROJECTILE;
            case LIVING:
                return NetworkEntityKind.LIVING;
            case PICKUP:
                return NetworkEntityKind.PICKUP;
            default:
                throw new AssertionError(kind);
        }
    }
}
