package betamoon.capability;

import betamoon.assets.AssetKey;
import betamoon.data.DataSchema;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Immutable reusable capability contract. */
public final class CapabilityDefinition {
    public final AssetKey key;
    public final String owner;
    public final DataSchema state;
    public final DataSchema config;
    public final Map<String, CapabilityOperationDefinition> operations;

    public CapabilityDefinition(AssetKey key, String owner, DataSchema state, DataSchema config,
            Map<String, CapabilityOperationDefinition> operations) {
        this.key = key;
        this.owner = owner;
        this.state = state;
        this.config = config;
        this.operations = Collections.unmodifiableMap(new LinkedHashMap<>(operations));
    }
}
