package betamoon.luamodloader;

import java.util.HashMap;
import java.util.Map;

/** Records scripts whose live Lua environment must remain active until restart. */
public final class NonReloadableScriptRegistry {
    private static final Map REASONS = new HashMap();

    private NonReloadableScriptRegistry() {
    }

    public static synchronized void mark(String script, String reason) {
        if (script != null) REASONS.put(script, reason);
    }

    public static synchronized boolean contains(String script) {
        return REASONS.containsKey(script);
    }

    public static synchronized String reason(String script) {
        return (String) REASONS.get(script);
    }

    public static synchronized void unmark(String script) {
        REASONS.remove(script);
    }
}
