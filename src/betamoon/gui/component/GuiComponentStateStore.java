package betamoon.gui.component;

import betamoon.gui.presentation.GuiPresentationHost;
import betamoon.gui.presentation.GuiPublishedData;
import betamoon.luaapi.utils.LuaCallbackScope;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * One compiled instance's keyed local state. Values are detached; matching
 * compatible keys survive refresh.
 */
public final class GuiComponentStateStore implements AutoCloseable {
    private final Map<String, Entry> entries = new LinkedHashMap<>();
    private final List<GuiPresentationHost.Observer> observers = new ArrayList<>();
    private final GuiPublishedData publication = new GuiPublishedData();
    private boolean closed;
    private boolean sealed;
    private Map<String, Object> restoration = Collections.emptyMap();
    private Map<String, Map<String, Object>> transaction;

    public void create(String key, GuiComponentSchema schema, GuiComponentStateStore previous) {
        requireActive();
        if (sealed) {
            throw new IllegalStateException("Keyed state creation finishes before activation");
        }
        if (entries.containsKey(key) || entries.size() >= 1024) {
            throw new IllegalArgumentException("Duplicate or excessive keyed component state: " + key);
        }
        Map<String, Object> values = new LinkedHashMap<>(schema.normalize(Collections.emptyMap()));
        Entry old = previous == null ? null : previous.entries.get(key);
        if (old != null) {
            for (Map.Entry<String, GuiComponentSchema.Field> field : schema.fields().entrySet()) {
                GuiComponentSchema.Field before = old.schema.fields().get(field.getKey());
                if (before != null && before.descriptor.kind == field.getValue().descriptor.kind) {
                    try {
                        values.put(field.getKey(),
                                field.getValue().descriptor.normalize(old.values.get(field.getKey())));
                    } catch (IllegalArgumentException incompatible) {
                        /* New validated default replaces incompatible state. */ }
                }
            }
        }
        Entry candidate = new Entry(schema, schema.normalize(values));
        entries.put(key, candidate);
        restoreEntry(key, candidate);
    }

    /** Seeds detached options/history before the instance becomes observable. */
    public void seed(String key, Map<String, Object> changes) {
        requireActive();
        if (sealed) {
            throw new IllegalStateException("Initial UI state is already sealed");
        }
        Entry entry = entry(key);
        entry.values = entry.schema.patch(entry.values, changes);
    }

    /**
     * History carries only detached values; the current schema decides what remains
     * compatible.
     */
    public void restore(Map<String, Object> flatValues) {
        requireActive();
        if (sealed) {
            throw new IllegalStateException("UI state restoration must precede activation");
        }
        GuiPublishedData detached = new GuiPublishedData();
        detached.publish(flatValues);
        restoration = detached.snapshot();
        for (Map.Entry<String, Entry> item : entries.entrySet()) {
            restoreEntry(item.getKey(), item.getValue());
        }
    }

    private void restoreEntry(String key, Entry entry) {
        if (restoration.isEmpty()) {
            return;
        }
        Map<String, Object> restored = new LinkedHashMap<>(entry.values);
        for (Map.Entry<String, GuiComponentSchema.Field> field : entry.schema.fields().entrySet()) {
            String source = source(key, field.getKey());
            if (restoration.containsKey(source)) {
                try {
                    restored.put(field.getKey(), field.getValue().descriptor.normalize(restoration.get(source)));
                } catch (IllegalArgumentException incompatible) {
                    /* The current validated default remains. */ }
            }
        }
        entry.values = entry.schema.normalize(restored);
    }

    public void seal() {
        requireActive();
        publish();
        sealed = true;
        restoration = Collections.emptyMap();
    }

    public Map<String, Object> values(String key) {
        Entry entry = entry(key);
        return transaction == null ? entry.values : transaction.getOrDefault(key, entry.values);
    }

    public Object get(String key, String name) {
        Entry entry = entry(key);
        if (!entry.schema.fields().containsKey(name)) {
            throw new IllegalArgumentException("Unknown component state field: " + name);
        }
        return values(key).get(name);
    }

    public void set(String key, Map<String, Object> changes) {
        LuaCallbackScope.requireUiStateWrite();
        Entry entry = entry(key);
        if (!sealed) {
            throw new IllegalStateException("Component state must be sealed before mutation");
        }
        Map<String, Object> candidate = entry.schema.patch(values(key), changes);
        if (transaction != null) {
            transaction.put(key, candidate);
            return;
        }
        if (candidate.equals(entry.values)) {
            return;
        }
        Map<String, Object> before = entry.values;
        entry.values = candidate;
        try {
            publish();
        } catch (RuntimeException | Error invalid) {
            entry.values = before;
            throw invalid;
        }
        for (String name : candidate.keySet()) {
            Object old = before.get(name);
            Object next = candidate.get(name);
            if (!Objects.equals(old, next)) {
                for (GuiPresentationHost.Observer observer : new ArrayList<>(observers)) {
                    observer.changed("state:" + source(key, name), old, next);
                }
            }
        }
    }

    public void beginEvent() {
        requireActive();
        LuaCallbackScope.requireUiStateWrite();
        if (!sealed || transaction != null) {
            throw new IllegalStateException("Component events cannot open nested transactions");
        }
        transaction = new LinkedHashMap<>();
    }

    public void finishEvent(boolean commit) {
        if (closed) {
            transaction = null;
            return;
        }
        Map<String, Map<String, Object>> pending = transaction;
        if (pending == null) {
            throw new IllegalStateException("No component event transaction is active");
        }
        transaction = null;
        if (!commit || closed) {
            return;
        }
        Map<String, Map<String, Object>> before = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, Object>> change : pending.entrySet()) {
            Entry entry = entry(change.getKey());
            before.put(change.getKey(), entry.values);
            entry.values = change.getValue();
        }
        try {
            publish();
        } catch (RuntimeException | Error invalid) {
            for (Map.Entry<String, Map<String, Object>> old : before.entrySet()) {
                entries.get(old.getKey()).values = old.getValue();
            }
            throw invalid;
        }
        for (Map.Entry<String, Map<String, Object>> change : pending.entrySet()) {
            for (String name : change.getValue().keySet()) {
                Object old = before.get(change.getKey()).get(name);
                Object next = change.getValue().get(name);
                if (!Objects.equals(old, next)) {
                    for (GuiPresentationHost.Observer observer : new ArrayList<>(observers)) {
                        observer.changed("state:" + source(change.getKey(), name), old, next);
                    }
                }
            }
        }
    }

    public Map<String, Object> snapshot() {
        requireActive();
        return publication.snapshot();
    }

    public AutoCloseable observe(GuiPresentationHost.Observer observer) {
        requireActive();
        observers.add(observer);
        return () -> observers.remove(observer);
    }

    public static String source(String key, String name) {
        return key + "::" + name;
    }

    private void publish() {
        Map<String, Object> flat = new LinkedHashMap<>();
        for (Map.Entry<String, Entry> entry : entries.entrySet()) {
            for (Map.Entry<String, Object> value : entry.getValue().values.entrySet()) {
                flat.put(source(entry.getKey(), value.getKey()), value.getValue());
            }
        }
        publication.publish(flat);
    }

    private Entry entry(String key) {
        requireActive();
        Entry entry = entries.get(key);
        if (entry == null) {
            throw new IllegalArgumentException("Unknown component state identity: " + key);
        }
        return entry;
    }

    private void requireActive() {
        if (closed) {
            throw new IllegalStateException("Component state instance has closed");
        }
    }

    @Override
    public void close() {
        closed = true;
        entries.clear();
        restoration = Collections.emptyMap();
        observers.clear();
        transaction = null;
        publication.publish(Collections.emptyMap());
    }

    private static final class Entry {
        final GuiComponentSchema schema;
        Map<String, Object> values;
        Entry(GuiComponentSchema schema, Map<String, Object> values) {
            this.schema = schema;
            this.values = values;
        }
    }
}
