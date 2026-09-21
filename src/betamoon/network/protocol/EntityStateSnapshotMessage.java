package betamoon.network.protocol;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Complete declared state used to recover from a missed delta. */
public final class EntityStateSnapshotMessage implements ProtocolMessage {
    public final int entityId;
    public final long revision;
    public final Map<String, WireValue> state;

    public EntityStateSnapshotMessage(int entityId, long revision, Map<String, WireValue> state) {
        if (revision < 0 || state == null) {
            throw new IllegalArgumentException("Entity state snapshot revision and fields are required");
        }
        this.entityId = entityId;
        this.revision = revision;
        this.state = Collections.unmodifiableMap(new LinkedHashMap<String, WireValue>(state));
    }

    @Override
    public MessageType type() {
        return MessageType.ENTITY_STATE_SNAPSHOT;
    }
}
