package betamoon.gui.collection;

import betamoon.gui.component.GuiComponentLimits;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Immutable authored records with one cached, fully validated model; ordinary
 * detached-data limits stay unchanged.
 */
public final class GuiCollectionValues extends AbstractList<Map<String, Object>> {
    private final GuiCollectionModel.Snapshot model;
    private final List<Map<String, Object>> records;

    private GuiCollectionValues(GuiCollectionModel.Snapshot model) {
        this.model = model;
        List<Map<String, Object>> records = new ArrayList<>();
        for (GuiCollectionModel.Record record : model.records) {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("key", record.key);
            value.put("parent", record.parent);
            value.put("label", record.label);
            value.put("enabled", record.enabled);
            value.put("data", record.data);
            records.add(Collections.unmodifiableMap(value));
        }
        this.records = Collections.unmodifiableList(records);
    }

    public static GuiCollectionValues ofRecords(List<GuiCollectionModel.Record> records) {
        return new GuiCollectionValues(GuiCollectionModel.prepare(records));
    }

    public static GuiCollectionValues normalize(Object value) {
        if (value instanceof GuiCollectionValues) {
            return (GuiCollectionValues) value;
        }
        if (!(value instanceof List) || ((List<?>) value).size() > GuiCollectionModel.MAX_RECORDS) {
            throw new IllegalArgumentException("Collection records require a list of at most 4096 keyed records");
        }
        Preparation prepared = new Preparation();
        for (Object item : (List<?>) value) {
            Map<String, Object> record = record(item);
            for (String key : record.keySet()) {
                if (!Arrays.asList("key", "parent", "label", "enabled", "data").contains(key)) {
                    throw new IllegalArgumentException("Unknown collection record field: " + key);
                }
            }
            String key = text(record.get("key"), "key", null);
            if (key == null) {
                throw new IllegalArgumentException("Collection record requires its stable key");
            }
            Object enabled = record.get("enabled");
            if (enabled != null && !(enabled instanceof Boolean)) {
                throw new IllegalArgumentException("Collection record enabled requires a boolean");
            }
            Map<String, Object> data = record.get("data") == null ? Collections.emptyMap() : record(record.get("data"));
            prepared.add(new GuiCollectionModel.Record(key, text(record.get("parent"), "parent", null),
                    text(record.get("label"), "label", key), enabled == null || (Boolean) enabled, data));
        }
        return prepared.finish();
    }

    private static String text(Object value, String field, String fallback) {
        if (value == null) {
            return fallback;
        }
        if (!(value instanceof String)) {
            throw new IllegalArgumentException("Collection record " + field + " requires a string");
        }
        return (String) value;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> record(Object value) {
        if (!(value instanceof Map)) {
            throw new IllegalArgumentException("Collection entries and their data require string-keyed records");
        }
        for (Object key : ((Map<?, ?>) value).keySet()) {
            if (!(key instanceof String)) {
                throw new IllegalArgumentException("Collection record keys require strings");
            }
        }
        return (Map<String, Object>) value;
    }

    public GuiCollectionModel.Snapshot model() {
        return model;
    }

    @Override
    public Map<String, Object> get(int index) {
        return records.get(index);
    }

    @Override
    public int size() {
        return records.size();
    }

    /** One candidate reader; a failed or finished preparation cannot be reused. */
    public static final class Preparation {
        private final GuiComponentLimits.Values budget = new GuiComponentLimits.Values(
                GuiCollectionModel.MAX_METADATA_BYTES, GuiCollectionModel.MAX_METADATA_VALUES);
        private final List<GuiCollectionModel.Record> records = new ArrayList<>();
        private boolean finished;

        public void add(GuiCollectionModel.Record record) {
            requireOpen();
            try {
                if (record == null || records.size() >= GuiCollectionModel.MAX_RECORDS) {
                    throw new IllegalArgumentException("Collection exceeds 4096 keyed records");
                }
                budget.text(record.key);
                budget.text(record.label);
                if (record.parent != null) {
                    budget.text(record.parent);
                }
                budget.add(record.data);
                if (budget.estimatedBytes() + (records.size() + 1) * 128L > GuiCollectionModel.MAX_METADATA_BYTES) {
                    throw new IllegalArgumentException("Collection metadata exceeds 4 MiB");
                }
                records.add(record);
            } catch (RuntimeException | Error invalid) {
                finished = true;
                throw invalid;
            }
        }

        public GuiCollectionValues finish() {
            requireOpen();
            finished = true;
            return ofRecords(records);
        }

        private void requireOpen() {
            if (finished) {
                throw new IllegalStateException("Collection value preparation has already finished or failed");
            }
        }
    }
}
