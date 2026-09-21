package betamoon.entity;

import betamoon.network.protocol.WireValue;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Immutable revision interval and its final changed field values. */
public final class EntityDataDelta {
    public final long baseRevision;
    public final long revision;
    public final Map<String, WireValue> changedFields;

    EntityDataDelta(long baseRevision, long revision, Map<String, WireValue> changedFields) {
        this.baseRevision = baseRevision;
        this.revision = revision;
        this.changedFields = Collections.unmodifiableMap(new LinkedHashMap<>(changedFields));
    }
}
