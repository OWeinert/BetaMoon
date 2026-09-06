package betamoon.luamodloader;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.IdentityHashMap;

/** Tracks reversible Java-side resources created by each Lua script. */
public final class ScriptResourceTracker {
    public interface Cleanup {
        void run();
    }

    private static final Map CLEANUPS = new HashMap();
    private static final Map OWNERS = new IdentityHashMap();

    private ScriptResourceTracker() {
    }

    public static synchronized void track(Cleanup cleanup) {
        String owner = LuaScriptRegistry.getCurrentScriptFile();
        if (owner == null || cleanup == null) {
            return;
        }
        List entries = (List) CLEANUPS.get(owner);
        if (entries == null) {
            entries = new ArrayList();
            CLEANUPS.put(owner, entries);
        }
        entries.add(cleanup);
    }

    /** Associates a short-lived runtime object with the declaring script. */
    public static synchronized void trackOwned(final Object value) {
        final String owner = LuaScriptRegistry.getCurrentScriptFile();
        if (owner == null || value == null) return;
        OWNERS.put(value, owner);
        track(new Cleanup() {
            public void run() {
                synchronized (ScriptResourceTracker.class) { OWNERS.remove(value); }
            }
        });
    }

    /** Returns the declaring script for a tracked runtime object, if any. */
    public static synchronized String findOwner(Object value) {
        return (String) OWNERS.get(value);
    }

    public static synchronized void unloadAll() {
        List owners = new ArrayList(CLEANUPS.keySet());
        Collections.sort(owners);
        for (int i = owners.size() - 1; i >= 0; i--) {
            unload((String) owners.get(i));
        }
    }

    /** Unloads only scripts that do not own startup-only structural content. */
    public static synchronized void unloadReloadable() {
        List owners = new ArrayList(CLEANUPS.keySet());
        Collections.sort(owners);
        for (int i = owners.size() - 1; i >= 0; i--) {
            String owner = (String) owners.get(i);
            if (!NonReloadableScriptRegistry.contains(owner)) unload(owner);
        }
    }

    public static synchronized void unload(String owner) {
        List entries = (List) CLEANUPS.remove(owner);
        if (entries == null) {
            return;
        }
        for (int i = entries.size() - 1; i >= 0; i--) {
            try {
                ((Cleanup) entries.get(i)).run();
            } catch (Throwable ignored) {
                // Continue cleanup so one stale integration cannot poison reload.
            }
        }
    }
}
