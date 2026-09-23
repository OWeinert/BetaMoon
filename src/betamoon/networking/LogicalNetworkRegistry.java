package betamoon.networking;

import betamoon.assets.AssetKey;
import betamoon.luamodloader.NonReloadableScriptRegistry;
import betamoon.luamodloader.ScriptResourceTracker;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Startup registry for logical gameplay-network definitions. */
public final class LogicalNetworkRegistry {
    private static final Map<AssetKey, LogicalNetworkDefinition> DEFINITIONS = new LinkedHashMap<>();

    private LogicalNetworkRegistry() {
    }

    public static synchronized void register(final LogicalNetworkDefinition definition) {
        if (DEFINITIONS.containsKey(definition.key)) {
            throw new IllegalArgumentException("Logical network is already registered: " + definition.key);
        }
        DEFINITIONS.put(definition.key, definition);
        NonReloadableScriptRegistry.mark(definition.owner, "logical networks and persistent topology");
        ScriptResourceTracker.track(new ScriptResourceTracker.Cleanup() {
            public void run() {
                synchronized (LogicalNetworkRegistry.class) {
                    if (DEFINITIONS.get(definition.key) == definition) {
                        DEFINITIONS.remove(definition.key);
                    }
                }
            }
        });
    }

    public static synchronized LogicalNetworkDefinition find(AssetKey key) {
        return DEFINITIONS.get(key);
    }

    public static synchronized List<LogicalNetworkDefinition> all() {
        return new ArrayList<>(DEFINITIONS.values());
    }
}
