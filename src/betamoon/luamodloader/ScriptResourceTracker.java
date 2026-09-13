package betamoon.luamodloader;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/** Tracks reversible Java-side resources created by each Lua script. */
public final class ScriptResourceTracker {
    @FunctionalInterface
    public interface Cleanup {
        void run();
    }

    private static final Map<String, List<Cleanup>> CLEANUPS = new HashMap<>();
    private static final Map<Object, String> OWNERS = new IdentityHashMap<>();

    private ScriptResourceTracker() {
    }

    public static synchronized void track(Cleanup cleanup) {
        String owner = LuaScriptRegistry.getCurrentScriptFile();
        if (owner == null || cleanup == null) {
            return;
        }
        List<Cleanup> entries = CLEANUPS.get(owner);
        if (entries == null) {
            entries = new ArrayList<>();
            CLEANUPS.put(owner, entries);
        }
        entries.add(cleanup);
    }

    /** Associates a short-lived runtime object with the declaring script. */
    public static synchronized void trackOwned(final Object value) {
        final String owner = LuaScriptRegistry.getCurrentScriptFile();
        if (owner == null || value == null) {
            return;
        }
        OWNERS.put(value, owner);
        track(new Cleanup() {
            public void run() {
                synchronized (ScriptResourceTracker.class) {
                    OWNERS.remove(value);
                }
            }
        });
    }

    /** Returns the declaring script for a tracked runtime object, if any. */
    public static synchronized String findOwner(Object value) {
        return OWNERS.get(value);
    }

    public static synchronized void unloadAll() {
        List<String> owners = new ArrayList<>(CLEANUPS.keySet());
        Collections.sort(owners);
        for (int i = owners.size() - 1; i >= 0; i--) {
            unload(owners.get(i));
        }
    }

    /** Unloads only scripts that do not own startup-only structural content. */
    public static synchronized void unloadReloadable() {
        List<String> owners = new ArrayList<>(CLEANUPS.keySet());
        Collections.sort(owners);
        for (int i = owners.size() - 1; i >= 0; i--) {
            String owner = owners.get(i);
            if (!NonReloadableScriptRegistry.contains(owner)) {
                unload(owner);
            }
        }
    }

    public static synchronized void unload(String owner) {
        List<Cleanup> entries = CLEANUPS.remove(owner);
        if (entries == null) {
            return;
        }
        for (int i = entries.size() - 1; i >= 0; i--) {
            try {
                entries.get(i).run();
            } catch (Throwable ignored) {
                // Continue cleanup so one stale integration cannot poison reload.
            }
        }
    }
}
