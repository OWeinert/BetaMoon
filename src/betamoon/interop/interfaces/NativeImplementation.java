package betamoon.interop.interfaces;

import betamoon.interop.catalog.CatalogValues;
import betamoon.interop.reference.NativeTypeRef;
import betamoon.interop.runtime.NativeOwnedValue;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Retainable metadata and ownership identity, never a proxy payload or Lua
 * closure.
 */
public final class NativeImplementation {
    public final NativeOwnedValue value;
    public final List<NativeTypeRef> interfaces;
    public final String policy;
    public final String owner;

    NativeImplementation(NativeOwnedValue value, List<NativeTypeRef> interfaces, String policy, String owner) {
        this.value = value;
        this.interfaces = Collections.unmodifiableList(new ArrayList<>(interfaces));
        this.policy = policy;
        this.owner = owner;
    }

    public Map<String, Object> describe() {
        return CatalogValues.record("interfaces", interfaces, "policy", policy, "ownership", value.describe());
    }
}
