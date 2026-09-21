package betamoon.client.network;

import betamoon.assets.AssetKey;
import betamoon.entity.EntityInstanceState;
import betamoon.entity.EntityPresentationEvents;
import betamoon.entity.EntityTypeDefinition;
import betamoon.entity.EntityTypeRegistry;
import betamoon.entity.LuaLivingEntity;
import betamoon.entity.LuaPickupEntity;
import betamoon.entity.LuaProjectileEntity;
import betamoon.entity.LuaPropEntity;
import betamoon.entity.TypedEntity;
import betamoon.network.protocol.EntityPresentationMessage;
import betamoon.network.protocol.EntitySoundMessage;
import betamoon.network.protocol.EntitySpawnMessage;
import betamoon.network.protocol.EntityStateMessage;
import betamoon.network.protocol.EntityStateSnapshotMessage;
import betamoon.network.protocol.NetworkEntityKind;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.src.Entity;
import net.minecraft.src.WorldClient;

/** Applies validated server state to a remote client world. */
public final class ClientEntityReplication {
    private final EntityResyncSender resyncSender;
    private final Map<Integer, Long> presentationSequences = new HashMap<>();
    private final Map<Integer, Long> soundSequences = new HashMap<>();
    private final Map<Integer, Long> pendingResyncRevision = new HashMap<>();

    public ClientEntityReplication(EntityResyncSender resyncSender) {
        if (resyncSender == null) {
            throw new IllegalArgumentException("Entity resync sender is required");
        }
        this.resyncSender = resyncSender;
    }

    public Entity spawn(WorldClient world, EntitySpawnMessage message) {
        requireWorld(world, message.dimension);
        if (world.func_709_b(message.entityId) != null) {
            throw new IllegalStateException("Entity runtime ID is already tracked: " + message.entityId);
        }
        EntityTypeDefinition definition = EntityTypeRegistry.find(AssetKey.parse(message.typeKey));
        if (definition == null || !kindMatches(definition, message.kind)) {
            throw new IllegalStateException("Missing or incompatible entity type: " + message.typeKey);
        }

        Entity entity = construct(world, definition, message.kind);
        EntityInstanceState state = ((TypedEntity) entity).entityState();
        state.attachNetwork(definition, message.stableIdentity, message.revision, message.state);
        if (entity instanceof LuaLivingEntity) {
            ((LuaLivingEntity) entity).health = Math.max(0, message.health);
        } else if (message.health >= 0) {
            state.health(message.health);
        }
        entity.width = definition.width;
        entity.height = definition.height;
        entity.rotationYaw = message.transform.yaw;
        entity.rotationPitch = message.transform.pitch;
        entity.prevRotationYaw = message.transform.yaw;
        entity.prevRotationPitch = message.transform.pitch;
        entity.motionX = message.transform.velocityX;
        entity.motionY = message.transform.velocityY;
        entity.motionZ = message.transform.velocityZ;
        entity.serverPosX = floorPacketPosition(message.transform.x);
        entity.serverPosY = floorPacketPosition(message.transform.y);
        entity.serverPosZ = floorPacketPosition(message.transform.z);
        entity.setPosition(message.transform.x, message.transform.y, message.transform.z);
        if (message.presentation != null) {
            state.presentation().applyNetworkSnapshot(message.presentation, entity.ticksExisted, 0);
        }

        world.func_712_a(message.entityId, entity);
        presentationSequences.remove(message.entityId);
        soundSequences.remove(message.entityId);
        pendingResyncRevision.remove(message.entityId);
        return entity;
    }

    public void state(WorldClient world, EntityStateMessage message) {
        TypedEntity typed = typed(world, message.entityId);
        EntityInstanceState state = typed.entityState();
        if (state.applyNetworkDelta(message.baseRevision, message.revision, message.changedFields)) {
            pendingResyncRevision.remove(message.entityId);
            return;
        }
        long knownRevision = state.data().revision();
        Long pending = pendingResyncRevision.get(message.entityId);
        if (pending == null || pending.longValue() != knownRevision) {
            pendingResyncRevision.put(message.entityId, Long.valueOf(knownRevision));
            resyncSender.send(new betamoon.network.protocol.EntityResyncRequestMessage(
                    message.entityId, knownRevision));
        }
    }

    public void stateSnapshot(WorldClient world, EntityStateSnapshotMessage message) {
        TypedEntity typed = typed(world, message.entityId);
        EntityInstanceState state = typed.entityState();
        EntityTypeDefinition definition = state.definition();
        if (definition == null) {
            throw new IllegalStateException("Cannot synchronize an entity with a missing definition");
        }
        state.data().applyNetworkSnapshot(definition, message.revision, message.state);
        pendingResyncRevision.remove(message.entityId);
    }

    public void presentation(WorldClient world, EntityPresentationMessage message) {
        Long previous = presentationSequences.get(message.entityId);
        if (previous != null && message.sequence <= previous.longValue()) {
            return;
        }
        Entity entity = entity(world, message.entityId);
        ((TypedEntity) entity).entityState().presentation()
                .applyNetworkSnapshot(message.presentation, entity.ticksExisted, message.sequence);
        presentationSequences.put(message.entityId, Long.valueOf(message.sequence));
    }

    public void sound(WorldClient world, EntitySoundMessage message) {
        Long previous = soundSequences.get(message.entityId);
        if (previous != null && message.sequence <= previous.longValue()) {
            return;
        }
        if (message.entityId >= 0) {
            entity(world, message.entityId);
        }
        EntityPresentationEvents.sound(AssetKey.parse(message.eventKey), message.x, message.y, message.z,
                message.volume, message.pitch, message.range);
        soundSequences.put(message.entityId, Long.valueOf(message.sequence));
    }

    public void removed(int entityId) {
        presentationSequences.remove(entityId);
        soundSequences.remove(entityId);
        pendingResyncRevision.remove(entityId);
    }

    public void clear() {
        presentationSequences.clear();
        soundSequences.clear();
        pendingResyncRevision.clear();
    }

    private static Entity construct(WorldClient world, EntityTypeDefinition definition, NetworkEntityKind kind) {
        switch (kind) {
            case PROP:
                return new LuaPropEntity(world);
            case PROJECTILE:
                return new LuaProjectileEntity(world);
            case LIVING:
                return new LuaLivingEntity(world);
            case PICKUP:
                LuaPickupEntity pickup = new LuaPickupEntity(world);
                pickup.initializeItem(definition);
                return pickup;
            default:
                throw new AssertionError(kind);
        }
    }

    private static boolean kindMatches(EntityTypeDefinition definition, NetworkEntityKind kind) {
        return definition.kind.name().equals(kind.name());
    }

    private static TypedEntity typed(WorldClient world, int entityId) {
        Entity entity = entity(world, entityId);
        if (!(entity instanceof TypedEntity)) {
            throw new IllegalStateException("Tracked entity is not a BetaMoon entity: " + entityId);
        }
        return (TypedEntity) entity;
    }

    private static Entity entity(WorldClient world, int entityId) {
        Entity entity = world.func_709_b(entityId);
        if (entity == null) {
            throw new IllegalStateException("Entity is not tracked: " + entityId);
        }
        return entity;
    }

    private static void requireWorld(WorldClient world, int dimension) {
        if (world == null || world.worldProvider == null || world.worldProvider.worldType != dimension) {
            throw new IllegalStateException("Entity snapshot belongs to a different dimension");
        }
    }

    private static int floorPacketPosition(double value) {
        return (int) Math.floor(value * 32.0);
    }
}
