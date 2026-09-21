package betamoon.network.protocol;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class EntitySpawnMessage implements ProtocolMessage {
    public final int entityId;
    public final String typeKey;
    public final String stableIdentity;
    public final NetworkEntityKind kind;
    public final int dimension;
    public final long revision;
    public final int health;
    public final EntityTransform transform;
    public final PresentationSnapshot presentation;
    public final Map<String, WireValue> state;

    public EntitySpawnMessage(int entityId, String typeKey, String stableIdentity, NetworkEntityKind kind,
            int dimension, long revision, int health, EntityTransform transform, PresentationSnapshot presentation,
            Map<String, WireValue> state) {
        this.entityId = entityId;
        this.typeKey = required(typeKey, "entity type key");
        this.stableIdentity = required(stableIdentity, "stable entity identity");
        if (kind == null || transform == null || state == null) {
            throw new IllegalArgumentException("Entity spawn kind, transform, and state are required");
        }
        this.kind = kind;
        this.dimension = dimension;
        this.revision = revision;
        this.health = health;
        this.transform = transform;
        this.presentation = presentation;
        this.state = Collections.unmodifiableMap(new LinkedHashMap<String, WireValue>(state));
    }

    @Override
    public MessageType type() {
        return MessageType.ENTITY_SPAWN;
    }

    private static String required(String value, String label) {
        if (value == null || value.length() == 0) {
            throw new IllegalArgumentException(label + " is required");
        }
        return value;
    }
}
