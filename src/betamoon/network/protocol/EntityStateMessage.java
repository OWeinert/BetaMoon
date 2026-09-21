package betamoon.network.protocol;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Ordered authoritative field delta. Missing revisions trigger a full resync. */
public final class EntityStateMessage implements ProtocolMessage {
    public final int entityId;
    public final long baseRevision;
    public final long revision;
    public final Map<String, WireValue> changedFields;

    public EntityStateMessage(int entityId, long baseRevision, long revision,
            Map<String, WireValue> changedFields) {
        if (revision <= baseRevision) {
            throw new IllegalArgumentException("Entity state revision must advance");
        }
        if (changedFields == null) {
            throw new IllegalArgumentException("Changed entity fields are required");
        }
        this.entityId = entityId;
        this.baseRevision = baseRevision;
        this.revision = revision;
        this.changedFields = Collections.unmodifiableMap(new LinkedHashMap<String, WireValue>(changedFields));
    }

    @Override
    public MessageType type() {
        return MessageType.ENTITY_STATE;
    }
}
