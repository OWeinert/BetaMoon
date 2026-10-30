package betamoon.gui.collection;

import betamoon.gui.component.GuiComponentLimits;
import betamoon.gui.presentation.GuiPublishedData;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Detached keyed records and state; realization belongs to the retained view
 * adapter.
 */
public final class GuiCollectionModel {
    public static final int MAX_RECORDS = 4096;
    private static final long MAX_METADATA_BYTES = 4 * 1024 * 1024;
    private static final long MAX_STATE_BYTES = 4 * 1024 * 1024;
    private static final Snapshot EMPTY = prepare(Collections.emptyList());

    public static final class Record {
        public final String key;
        public final String parent;
        public final String label;
        public final boolean enabled;
        public final Map<String, Object> data;

        public Record(String key, String parent, String label, boolean enabled, Map<String, ?> data) {
            if (key == null || key.isEmpty() || key.length() > 255
                    || parent != null && (parent.isEmpty() || parent.length() > 255) || label == null
                    || label.length() > 8192 || data == null) {
                throw new IllegalArgumentException("Collection records require a bounded key, label and detached data");
            }
            GuiPublishedData detached = new GuiPublishedData();
            detached.publish(data);
            this.key = key;
            this.parent = parent;
            this.label = label;
            this.enabled = enabled;
            this.data = detached.snapshot();
        }

        @Override
        public boolean equals(Object value) {
            if (!(value instanceof Record)) {
                return false;
            }
            Record other = (Record) value;
            return key.equals(other.key) && Objects.equals(parent, other.parent) && label.equals(other.label)
                    && enabled == other.enabled && data.equals(other.data);
        }

        @Override
        public int hashCode() {
            return Objects.hash(key, parent, label, enabled, data);
        }
    }

    /**
     * A complete validated candidate can be preflighted before the host publishes
     * it.
     */
    public static final class Snapshot {
        public final List<Record> records;
        public final Map<String, Record> keys;
        public final long metadataBytes;
        private final Map<String, List<Record>> children;

        private Snapshot(List<Record> records, Map<String, Record> keys, Map<String, List<Record>> children,
                long metadataBytes) {
            this.records = Collections.unmodifiableList(new ArrayList<>(records));
            this.keys = Collections.unmodifiableMap(new LinkedHashMap<>(keys));
            Map<String, List<Record>> copy = new HashMap<>();
            for (Map.Entry<String, List<Record>> entry : children.entrySet()) {
                copy.put(entry.getKey(), Collections.unmodifiableList(new ArrayList<>(entry.getValue())));
            }
            this.children = Collections.unmodifiableMap(copy);
            this.metadataBytes = metadataBytes;
        }

        public boolean branch(String key) {
            return children.containsKey(key);
        }

        public List<Row> visible(Set<String> expanded) {
            Objects.requireNonNull(expanded, "expanded collection keys");
            List<Row> result = new ArrayList<>();
            append(this, null, 0, expanded, result);
            return Collections.unmodifiableList(result);
        }
    }

    public static final class Row {
        public final Record record;
        public final int depth;

        private Row(Record record, int depth) {
            this.record = record;
            this.depth = depth;
        }
    }

    private Snapshot snapshot = EMPTY;
    private final Set<String> expanded = new LinkedHashSet<>();
    private final Map<String, State> states = new HashMap<>();
    private List<Row> visible = Collections.emptyList();
    private Set<String> publishedExpansion = Collections.emptySet();
    private String selected;
    private long revision;
    private long stateBytes;

    public static Snapshot prepare(List<Record> records) {
        if (records == null || records.size() > MAX_RECORDS) {
            throw new IllegalArgumentException("Collection model exceeds 4096 records");
        }
        Map<String, Record> keys = new LinkedHashMap<>();
        Map<String, List<Record>> children = new HashMap<>();
        GuiComponentLimits.Values budget = new GuiComponentLimits.Values(4 * 1024 * 1024, 131072);
        for (Record record : records) {
            if (record == null || keys.put(record.key, record) != null) {
                throw new IllegalArgumentException("Collection keys must be unique and records cannot be null");
            }
            budget.text(record.key);
            budget.text(record.label);
            if (record.parent != null) {
                budget.text(record.parent);
            }
            budget.add(record.data);
            children.computeIfAbsent(record.parent, key -> new ArrayList<>()).add(record);
        }
        long bytes = budget.estimatedBytes() + records.size() * 128L;
        if (bytes > MAX_METADATA_BYTES) {
            throw new IllegalArgumentException("Collection metadata exceeds 4 MiB");
        }
        Map<String, Integer> depths = new HashMap<>();
        Set<String> visiting = new HashSet<>();
        for (Record record : records) {
            depth(record.key, keys, depths, visiting);
        }
        return new Snapshot(records, keys, children, bytes);
    }

    private static int depth(String key, Map<String, Record> keys, Map<String, Integer> depths, Set<String> visiting) {
        Integer known = depths.get(key);
        if (known != null) {
            return known;
        }
        Record record = keys.get(key);
        if (record == null || !visiting.add(key) || visiting.size() > 32) {
            throw new IllegalArgumentException(
                    "Collection parents must exist and form an acyclic tree within 32 levels");
        }
        int depth = record.parent == null ? 0 : 1 + depth(record.parent, keys, depths, visiting);
        if (depth >= 32) {
            throw new IllegalArgumentException("Collection tree exceeds 32 levels");
        }
        visiting.remove(key);
        depths.put(key, depth);
        return depth;
    }

    public boolean publish(List<Record> records) {
        return publish(prepare(records));
    }

    /**
     * Stage model, expansion and state changes without modifying the displayed
     * collection.
     */
    public GuiCollectionModel fork(Snapshot candidate) {
        Objects.requireNonNull(candidate, "collection snapshot");
        GuiCollectionModel copy = new GuiCollectionModel();
        copy.snapshot = snapshot;
        copy.expanded.addAll(expanded);
        copy.states.putAll(states);
        copy.visible = visible;
        copy.publishedExpansion = publishedExpansion;
        copy.selected = selected;
        copy.revision = revision;
        copy.stateBytes = stateBytes;
        copy.publish(candidate);
        return copy;
    }

    public boolean publish(Snapshot candidate) {
        Objects.requireNonNull(candidate, "collection snapshot");
        if (snapshot.records.equals(candidate.records)) {
            return false;
        }
        snapshot = candidate;
        expanded.removeIf(key -> !candidate.keys.containsKey(key) || !candidate.branch(key));
        states.entrySet().removeIf(entry -> {
            if (candidate.keys.containsKey(entry.getKey())) {
                return false;
            }
            stateBytes -= entry.getValue().bytes;
            return true;
        });
        if (selected != null && (!candidate.keys.containsKey(selected) || !candidate.keys.get(selected).enabled)) {
            selected = null;
        }
        rebuildVisible();
        revision++;
        return true;
    }

    public Snapshot snapshot() {
        return snapshot;
    }

    public List<Row> visible() {
        return visible;
    }

    public long revision() {
        return revision;
    }

    public String selected() {
        return selected;
    }

    public boolean select(String key) {
        if (key != null && !required(key).enabled) {
            return false;
        }
        if (Objects.equals(selected, key)) {
            return false;
        }
        selected = key;
        revision++;
        return true;
    }

    public Set<String> expanded() {
        return publishedExpansion;
    }

    public boolean expand(String key, boolean value) {
        required(key);
        if (!snapshot.branch(key) || !(value ? expanded.add(key) : expanded.remove(key))) {
            return false;
        }
        rebuildVisible();
        revision++;
        return true;
    }

    public Map<String, Object> rowState(String key) {
        required(key);
        State value = states.get(key);
        return value == null ? Collections.emptyMap() : value.values;
    }

    public boolean publishRowState(String key, Map<String, ?> values) {
        required(key);
        GuiPublishedData detached = new GuiPublishedData();
        detached.publish(values);
        GuiComponentLimits.Values budget = new GuiComponentLimits.Values(16384, 2048);
        budget.add(detached.snapshot());
        long bytes = budget.estimatedBytes();
        State previous = states.get(key);
        if ((previous == null ? Collections.emptyMap() : previous.values).equals(detached.snapshot())) {
            return false;
        }
        long total = stateBytes - (previous == null ? 0 : previous.bytes) + bytes;
        if (bytes > 65536 || total > MAX_STATE_BYTES) {
            throw new IllegalArgumentException("Collection row state exceeds its 64 KiB row / 4 MiB model budget");
        }
        states.put(key, new State(detached.snapshot(), bytes));
        stateBytes = total;
        revision++;
        return true;
    }

    private Record required(String key) {
        Record record = snapshot.keys.get(key);
        if (record == null) {
            throw new IllegalArgumentException("Unknown collection key: " + key);
        }
        return record;
    }

    private void rebuildVisible() {
        visible = snapshot.visible(expanded);
        publishedExpansion = Collections.unmodifiableSet(new LinkedHashSet<>(expanded));
    }

    private static void append(Snapshot snapshot, String parent, int depth, Set<String> expanded, List<Row> rows) {
        for (Record record : snapshot.children.getOrDefault(parent, Collections.emptyList())) {
            rows.add(new Row(record, depth));
            if (expanded.contains(record.key)) {
                append(snapshot, record.key, depth + 1, expanded, rows);
            }
        }
    }

    private static final class State {
        final Map<String, Object> values;
        final long bytes;

        State(Map<String, Object> values, long bytes) {
            this.values = values;
            this.bytes = bytes;
        }
    }
}
