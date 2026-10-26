package betamoon.interop.runtime;

import betamoon.interop.binding.NativeAccessFailure;
import betamoon.interop.catalog.CatalogValues;
import betamoon.interop.reference.NativeTypeRef;
import betamoon.luamodloader.ScriptResourceTracker;
import java.lang.ref.WeakReference;
import java.util.Map;

/**
 * Retainable identity/status only; the payload stays in its bounded session
 * registry.
 */
public final class NativeOwnedValue {
    final long identity;
    final String owner;
    final long generation;
    final String lifetime;
    public final NativeTypeRef type;
    private final WeakReference<NativeOwnedValues> registry;
    String state = "active";

    NativeOwnedValue(NativeOwnedValues registry, long identity, String owner, long generation, String lifetime,
            NativeTypeRef type) {
        this.registry = new WeakReference<>(registry);
        this.identity = identity;
        this.owner = owner;
        this.generation = generation;
        this.lifetime = lifetime;
        this.type = type;
    }

    public Object use(NativeContext context) {
        return registry().use(this, context);
    }

    public void release(NativeContext context, boolean direct) {
        registry().release(this, context, direct);
    }

    public Map<String, Object> describe() {
        String currentState = state;
        if (currentState.equals("active")) {
            if (registry.get() == null) {
                currentState = "session_closed";
            } else if (ScriptResourceTracker.ownerGeneration(owner) != generation) {
                currentState = "stale_owner";
            }
        }
        return CatalogValues.record("type", type, "lifetime", lifetime, "state", currentState);
    }

    private NativeOwnedValues registry() {
        NativeOwnedValues value = registry.get();
        if (value == null) {
            throw new NativeAccessFailure("owned_value_closed", "native session ended");
        }
        return value;
    }
}
