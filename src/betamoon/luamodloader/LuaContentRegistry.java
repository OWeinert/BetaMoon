package betamoon.luamodloader;

import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import org.luaj.vm2.LuaError;

/** Keeps Java content identities stable across Lua environment reloads. */
public final class LuaContentRegistry {
    private static final Map CONTENT = new HashMap();

    public static final class Entry {
        public Object value;
        public String kind;
        public boolean registered;
        private final String owner;
        private final String namespace;
        private final int id;
        private boolean seen = true;

        private Entry(Object value, String kind, String owner, String namespace, int id) {
            this.value = value;
            this.kind = kind;
            this.owner = owner;
            this.namespace = namespace;
            this.id = id;
        }
    }

    private LuaContentRegistry() {
    }

    private static String key(String namespace, int id) {
        String owner = LuaScriptRegistry.getCurrentScriptFile();
        if (owner == null) throw new LuaError("Content registration is only available while a script is loading.");
        return owner + "\n" + namespace + "\n" + id;
    }

    public static synchronized Entry find(String namespace, int id) {
        Entry entry = (Entry) CONTENT.get(key(namespace, id));
        if (entry != null) entry.seen = true;
        return entry;
    }

    public static synchronized Entry remember(String namespace, int id, Object value, String kind) {
        String key = key(namespace, id);
        Entry entry = (Entry) CONTENT.get(key);
        if (entry == null) {
            String owner = LuaScriptRegistry.getCurrentScriptFile();
            entry = new Entry(value, kind, owner, namespace, id);
            CONTENT.put(key, entry);
        } else if (!entry.kind.equals(kind)) {
            throw new LuaError("Cannot hot-reload " + namespace + " " + id + " from type '" + entry.kind
                + "' to '" + kind + "'. Restart Minecraft to change its type.");
        }
        return entry;
    }

    public static synchronized void replace(Entry entry, Object value, String kind) {
        entry.value = value;
        entry.kind = kind;
    }

    /** Returns the declaring script for a registered object, or null for foreign content. */
    public static synchronized String findOwner(Object target) {
        for (Object value : CONTENT.values()) {
            Entry entry = (Entry) value;
            if (entry.value == target) return entry.owner;
        }
        return null;
    }

    public static synchronized void beginLoadPass() {
        for (Object value : CONTENT.values()) ((Entry) value).seen = false;
    }

    /** Returns retained registrations whose declaration disappeared this pass. */
    public static synchronized List finishLoadPass() {
        List warnings = new ArrayList();
        for (Object value : CONTENT.values()) {
            Entry entry = (Entry) value;
            if (!entry.seen) {
                warnings.add(entry.owner + ": removed " + entry.namespace + " id " + entry.id
                    + " is retained until restart to protect loaded worlds and inventories.");
            }
        }
        return warnings;
    }
}
