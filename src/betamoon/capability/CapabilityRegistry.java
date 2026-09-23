package betamoon.capability;

import betamoon.assets.AssetKey;
import betamoon.luamodloader.NonReloadableScriptRegistry;
import betamoon.luamodloader.ScriptResourceTracker;
import java.util.LinkedHashMap;
import java.util.Map;

/** Process-wide startup registry of typed capability contracts. */
public final class CapabilityRegistry {
    private static final Map<AssetKey, CapabilityDefinition> DEFINITIONS = new LinkedHashMap<>();

    private CapabilityRegistry() {
    }

    public static synchronized void register(final CapabilityDefinition definition) {
        if (DEFINITIONS.containsKey(definition.key)) {
            throw new IllegalArgumentException("Capability is already registered: " + definition.key);
        }
        DEFINITIONS.put(definition.key, definition);
        NonReloadableScriptRegistry.mark(definition.owner, "capabilities and persistent capability state");
        ScriptResourceTracker.track(new ScriptResourceTracker.Cleanup() {
            public void run() {
                synchronized (CapabilityRegistry.class) {
                    if (DEFINITIONS.get(definition.key) == definition) {
                        DEFINITIONS.remove(definition.key);
                    }
                }
            }
        });
    }

    public static synchronized CapabilityDefinition find(AssetKey key) {
        return DEFINITIONS.get(key);
    }
}
