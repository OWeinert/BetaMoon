package betamoon.capability;

import betamoon.data.DataSchema;

/** One typed operation in a capability contract. */
public final class CapabilityOperationDefinition {
    public enum Mode {
        QUERY,
        ACTION
    }

    public final String name;
    public final Mode mode;
    public final DataSchema request;
    public final DataSchema response;

    public CapabilityOperationDefinition(String name, Mode mode, DataSchema request, DataSchema response) {
        this.name = name;
        this.mode = mode;
        this.request = request;
        this.response = response;
    }
}
