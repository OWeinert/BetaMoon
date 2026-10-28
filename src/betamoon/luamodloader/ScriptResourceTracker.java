package betamoon.luamodloader;

import betamoon.luaapi.utils.LuaCallbackScope;
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
    private static final Map<String, OwnerGeneration> OWNER_GENERATIONS = new HashMap<>();
    private static final OwnerGeneration UNOWNED = new OwnerGeneration();

    /**
     * Stable liveness cell; no callback, owner object, native root or cleanup list
     * is retained.
     */
    public static final class OwnerGeneration {
        private volatile long value;

        public long current() {
            return value;
        }

        public boolean matches(long expected) {
            return value == expected;
        }
    }

    public static synchronized OwnerGeneration ownerToken(String owner) {
        if (owner == null) {
            return UNOWNED;
        }
        OwnerGeneration found = OWNER_GENERATIONS.get(owner);
        if (found == null) {
            found = new OwnerGeneration();
            OWNER_GENERATIONS.put(owner, found);
        }
        return found;
    }
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

    /**
     * Removes one installed cleanup by identity when its owning session has already
     * closed it.
     */
    public static synchronized void untrack(String owner, Cleanup cleanup) {
        List<Cleanup> entries = CLEANUPS.get(owner);
        if (entries != null) {
            entries.removeIf(entry -> entry == cleanup);
            if (entries.isEmpty()) {
                CLEANUPS.remove(owner);
            }
        }
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

    /**
     * A declaration can compare this token without retaining its owner's cleanup
     * list.
     */
    public static synchronized long ownerGeneration(String owner) {
        OwnerGeneration generation = OWNER_GENERATIONS.get(owner);
        return generation == null ? 0 : generation.current();
    }

    public static synchronized void unload(String owner) {
        LuaCallbackScope.requireNoBindingMutation();
        if (owner != null) {
            ownerToken(owner).value++;
        }
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
