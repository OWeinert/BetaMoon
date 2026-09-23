package betamoon.system;

import betamoon.assets.AssetKey;
import betamoon.luamodloader.NonReloadableScriptRegistry;
import betamoon.luamodloader.ScriptResourceTracker;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Startup registry for persistent world-service declarations. */
public final class WorldServiceRegistry {
    private static final Map<AssetKey, WorldServiceDefinition> DEFINITIONS = new LinkedHashMap<>();

    private WorldServiceRegistry() {
    }

    public static synchronized void register(final WorldServiceDefinition definition) {
        if (DEFINITIONS.containsKey(definition.key)) {
            throw new IllegalArgumentException("World service is already registered: " + definition.key);
        }
        DEFINITIONS.put(definition.key, definition);
        NonReloadableScriptRegistry.mark(definition.owner, "persistent world services");
        ScriptResourceTracker.track(new ScriptResourceTracker.Cleanup() {
            public void run() {
                synchronized (WorldServiceRegistry.class) {
                    if (DEFINITIONS.get(definition.key) == definition) {
                        DEFINITIONS.remove(definition.key);
                    }
                }
            }
        });
    }

    public static synchronized WorldServiceDefinition find(AssetKey key) {
        return DEFINITIONS.get(key);
    }

    public static synchronized List<WorldServiceDefinition> all() {
        return new ArrayList<>(DEFINITIONS.values());
    }
}
