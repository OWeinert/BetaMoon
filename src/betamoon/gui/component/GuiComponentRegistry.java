package betamoon.gui.component;

import betamoon.luaapi.utils.LuaCallbackScope;
import betamoon.luamodloader.ScriptExecution;
import betamoon.luamodloader.ScriptResourceTracker;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Owner-cleaned definitions and revision notifications; no active views or
 * native resources live here.
 */
public final class GuiComponentRegistry {
    private final Map<String, Entry> definitions = new LinkedHashMap<>();
    private final List<Runnable> observers = new ArrayList<>();
    private long revision;

    public void add(GuiComponentDefinition definition) {
        LuaCallbackScope.requireNoBindingMutation();
        definition.requireActive();
        Entry previous = definitions.get(definition.key);
        if (previous != null && !previous.definition.owner.equals(definition.owner)) {
            throw new IllegalArgumentException(definition.key + ": component key belongs to another owner");
        }
        if (previous == null && definitions.size() >= 1024) {
            throw new IllegalArgumentException("Component registry limit exceeded: 1024");
        }
        long total = definition.metadataBytes;
        long owner = definition.metadataBytes;
        for (Entry entry : definitions.values()) {
            if (entry != previous) {
                total += entry.definition.metadataBytes;
                if (entry.definition.owner.equals(definition.owner)) {
                    owner += entry.definition.metadataBytes;
                }
            }
        }
        if (total > GuiComponentLimits.REGISTRY_METADATA_BYTES || owner > GuiComponentLimits.OWNER_METADATA_BYTES) {
            throw new IllegalArgumentException("Component registry/owner metadata byte budget exceeded");
        }
        Map<String, GuiComponentDefinition> candidates = new LinkedHashMap<>();
        for (Entry entry : definitions.values()) {
            candidates.put(entry.definition.key, entry.definition);
        }
        candidates.put(definition.key, definition);
        validateCycles(candidates);
        Entry next = new Entry(definition);
        definitions.put(definition.key, next);
        ScriptExecution.withOwner(definition.owner, () -> {
            ScriptResourceTracker.track(next.cleanup);
            return null;
        });
        if (previous != null) {
            ScriptResourceTracker.untrack(previous.definition.owner, previous.cleanup);
        }
        changed();
    }

    public GuiComponentDefinition get(String key) {
        Entry entry = definitions.get(GuiComponentDefinition.key(key));
        if (entry == null) {
            return null;
        }
        entry.definition.requireActive();
        return entry.definition;
    }

    public GuiComponentDefinition getRequired(String key) {
        GuiComponentDefinition result = get(key);
        if (result == null) {
            throw new IllegalArgumentException("Unknown component: " + key);
        }
        return result;
    }

    public long revision() {
        return revision;
    }

    public AutoCloseable observe(Runnable observer) {
        observers.add(observer);
        return () -> observers.remove(observer);
    }

    public Map<String, GuiComponentDefinition> snapshot() {
        Map<String, GuiComponentDefinition> result = new LinkedHashMap<>();
        for (Entry entry : definitions.values()) {
            result.put(entry.definition.key, entry.definition);
        }
        return Collections.unmodifiableMap(result);
    }

    private void changed() {
        revision++;
        for (Runnable observer : new ArrayList<>(observers)) {
            observer.run();
        }
    }

    private final class Entry {
        final GuiComponentDefinition definition;
        final ScriptResourceTracker.Cleanup cleanup;
        Entry(GuiComponentDefinition definition) {
            this.definition = definition;
            cleanup = () -> {
                if (definitions.get(definition.key) == this) {
                    definitions.remove(definition.key);
                    changed();
                }
            };
        }
    }

    private static void validateCycles(Map<String, GuiComponentDefinition> definitions) {
        Set<String> complete = new LinkedHashSet<>();
        for (String key : definitions.keySet()) {
            visit(key, definitions, complete, new LinkedHashSet<>());
        }
    }

    private static void visit(String key, Map<String, GuiComponentDefinition> definitions, Set<String> complete,
            Set<String> visiting) {
        if (complete.contains(key) || !definitions.containsKey(key)) {
            return;
        }
        if (!visiting.add(key) || visiting.size() > 32) {
            throw new IllegalArgumentException("Component dependency cycle/depth exceeded: " + key);
        }
        for (String dependency : definitions.get(key).dependencies) {
            visit(dependency, definitions, complete, visiting);
        }
        visiting.remove(key);
        complete.add(key);
    }
}
