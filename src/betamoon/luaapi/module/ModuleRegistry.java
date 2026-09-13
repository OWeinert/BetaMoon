package betamoon.luaapi.module;

import betamoon.luamodloader.LuaScriptRegistry;
import betamoon.luamodloader.ScriptResourceTracker;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

/** Owns pending and published Lua module exports. */
public final class ModuleRegistry {
    private static final Map<String, ExportEntry> pendingExports = new HashMap<>();
    private static final Map<String, ExportEntry> publishedExports = new HashMap<>();

    private ModuleRegistry() {
    }

    static void stage(String name, LuaTable value, LuaValue packageLoaded) {
        final ExportEntry entry = stageEntry(name, value, packageLoaded);
        if (entry == null) {
            return;
        }

        ScriptResourceTracker.track(new ScriptResourceTracker.Cleanup() {
            @Override
            public void run() {
                remove(entry);
            }
        });
    }

    private static synchronized ExportEntry stageEntry(String name, LuaTable value, LuaValue packageLoaded) {
        String owner = LuaScriptRegistry.getCurrentScriptFile();
        if (owner == null) {
            throw new LuaError("Module: export must be called while a script is loading or initializing.");
        }
        if (name.trim().isEmpty()) {
            throw new LuaError("Module: export name must not be empty.");
        }

        ExportEntry published = publishedExports.get(name);
        if (published != null && !published.owner.equals(owner)) {
            throw duplicateExport(name, published.owner);
        }

        ExportEntry pending = pendingExports.get(name);
        if (pending != null) {
            if (!pending.owner.equals(owner)) {
                throw duplicateExport(name, pending.owner);
            }
            if (pending.value != value) {
                throw new LuaError("Module: script '" + owner + "' exported two different tables as '" + name + "'.");
            }
            return null;
        }

        ExportEntry entry = new ExportEntry(name, owner, value, packageLoaded);
        pendingExports.put(name, entry);
        return entry;
    }

    static synchronized LuaTable importRequired(String name) {
        if (name.trim().isEmpty()) {
            throw new LuaError("Module: import name must not be empty.");
        }

        ExportEntry entry = publishedExports.get(name);
        if (entry == null) {
            throw new LuaError("Module: not exported: " + name);
        }
        return entry.value;
    }

    /**
     * Publishes every pending export owned by a successfully initialized script.
     */
    public static synchronized void publish(String owner) {
        for (ExportEntry entry : pendingExports.values()) {
            if (!entry.owner.equals(owner)) {
                continue;
            }

            ExportEntry published = publishedExports.get(entry.name);
            if (published != null && !published.owner.equals(owner)) {
                throw duplicateExport(entry.name, published.owner);
            }
        }

        Iterator<Map.Entry<String, ExportEntry>> iterator = pendingExports.entrySet().iterator();
        while (iterator.hasNext()) {
            ExportEntry entry = iterator.next().getValue();
            if (!entry.owner.equals(owner)) {
                continue;
            }

            ExportEntry previous = publishedExports.put(entry.name, entry);
            if (previous != null && previous != entry) {
                previous.removeFromPackageLoaded();
            }
            entry.addToPackageLoaded();
            iterator.remove();
        }
    }

    /** Discards exports that belong to a script generation that did not load. */
    public static synchronized void discardPending(String owner) {
        Iterator<ExportEntry> iterator = pendingExports.values().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().owner.equals(owner)) {
                iterator.remove();
            }
        }
    }

    private static synchronized void remove(ExportEntry entry) {
        if (pendingExports.get(entry.name) == entry) {
            pendingExports.remove(entry.name);
        }
        if (publishedExports.get(entry.name) == entry) {
            publishedExports.remove(entry.name);
            entry.removeFromPackageLoaded();
        }
    }

    private static LuaError duplicateExport(String name, String owner) {
        return new LuaError("Module: '" + name + "' is already exported by script '" + owner + "'.");
    }

    private static final class ExportEntry {
        private final String name;
        private final String owner;
        private final LuaTable value;
        private final LuaValue packageLoaded;

        private ExportEntry(String name, String owner, LuaTable value, LuaValue packageLoaded) {
            this.name = name;
            this.owner = owner;
            this.value = value;
            this.packageLoaded = packageLoaded;
        }

        private void addToPackageLoaded() {
            if (packageLoaded.istable()) {
                packageLoaded.set(name, value);
            }
        }

        private void removeFromPackageLoaded() {
            if (packageLoaded.istable() && packageLoaded.get(name) == value) {
                packageLoaded.set(name, LuaValue.NIL);
            }
        }
    }
}
