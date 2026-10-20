package betamoon.luaapi.config;

import betamoon.luamodloader.ScriptResourceTracker;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.luaj.vm2.LuaError;

/** Active config definitions, indexed by script owner and generation. */
public final class ScriptConfigRegistry {
    private static final Map<String, Map<String, ScriptConfigRegistration>> BY_OWNER =
            new LinkedHashMap<String, Map<String, ScriptConfigRegistration>>();
    private static final Map<String, ScriptConfigRegistration> BY_FILE =
            new LinkedHashMap<String, ScriptConfigRegistration>();
    private static final ArrayDeque<ScriptConfigRegistration.ApplyEvent> PENDING =
            new ArrayDeque<ScriptConfigRegistration.ApplyEvent>();

    private ScriptConfigRegistry() {
    }

    static synchronized void checkAvailable(String owner, String key, ScriptConfigStore store) {
        Map<String, ScriptConfigRegistration> entries = BY_OWNER.get(owner);
        if (entries != null && entries.containsKey(key)) {
            throw new LuaError("Config '" + key + "' was declared twice by " + owner);
        }
        ScriptConfigRegistration collision = BY_FILE.get(store.fileName());
        if (collision != null) {
            throw new LuaError("Config '" + key + "' uses the same file as " + collision.owner()
                    + " config '" + collision.key() + "'");
        }
    }

    static synchronized void register(final ScriptConfigRegistration entry, ScriptConfigStore store) {
        checkAvailable(entry.owner(), entry.key(), store);
        Map<String, ScriptConfigRegistration> entries = BY_OWNER.get(entry.owner());
        if (entries == null) {
            entries = new LinkedHashMap<String, ScriptConfigRegistration>();
            BY_OWNER.put(entry.owner(), entries);
        }
        entries.put(entry.key(), entry);
        BY_FILE.put(store.fileName(), entry);
        ScriptResourceTracker.track(() -> unregister(entry, store.fileName()));
    }

    private static synchronized void unregister(ScriptConfigRegistration entry, String file) {
        Map<String, ScriptConfigRegistration> entries = BY_OWNER.get(entry.owner());
        if (entries != null) {
            entries.remove(entry.key(), entry);
            if (entries.isEmpty()) {
                BY_OWNER.remove(entry.owner());
            }
        }
        BY_FILE.remove(file, entry);
    }

    static synchronized boolean isCurrent(ScriptConfigRegistration entry) {
        Map<String, ScriptConfigRegistration> entries = BY_OWNER.get(entry.owner());
        return entries != null && entries.get(entry.key()) == entry;
    }

    public static synchronized List<ScriptConfigRegistration> forScript(String owner) {
        Map<String, ScriptConfigRegistration> entries = BY_OWNER.get(owner);
        if (entries == null) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<ScriptConfigRegistration>(entries.values()));
    }

    static synchronized void enqueue(ScriptConfigRegistration.ApplyEvent event) {
        PENDING.add(event);
    }

    /** Called at the main client or server update point, outside render/input dispatch. */
    public static void tick() {
        for (int delivered = 0; delivered < 256; delivered++) {
            ScriptConfigRegistration.ApplyEvent event;
            synchronized (ScriptConfigRegistry.class) {
                event = PENDING.poll();
            }
            if (event == null) {
                return;
            }
            event.apply();
        }
    }
}
