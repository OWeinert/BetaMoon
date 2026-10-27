package betamoon.interop.binding;

import betamoon.interop.catalog.CatalogJson;
import betamoon.interop.catalog.CatalogValues;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Bounded detached failure evidence; never retains exceptions, receivers,
 * classes or callback scopes.
 */
final class NativeFailureJournal {
    private static final int MAXIMUM_RECORDS = 64;
    private final Map<String, Entry> entries = new LinkedHashMap<>();
    private long omitted;

    synchronized void record(NativeAccessFailure failure) {
        Map<String, Object> record = new LinkedHashMap<>(failure.diagnostic());
        record.put("reason", text(failure.reason, 128));
        record.put("message", text(failure.getMessage(), 2048));
        Throwable cause = failure.getCause();
        if (cause != null) {
            record.put("nativeCauseType", cause.getClass().getName());
        }
        String key = CatalogJson.encode(record);
        if (key.length() > 65536) {
            omitted++;
            return;
        }
        Entry entry = entries.get(key);
        if (entry != null) {
            entry.count++;
            return;
        }
        if (entries.size() >= MAXIMUM_RECORDS) {
            omitted++;
            return;
        }
        entries.put(key, new Entry(CatalogValues.object(CatalogValues.freeze(record))));
    }

    synchronized Map<String, Object> snapshot() {
        List<Map<String, Object>> records = new ArrayList<>();
        for (Entry entry : entries.values()) {
            Map<String, Object> row = new LinkedHashMap<>(entry.record);
            row.put("count", entry.count);
            records.add(row);
        }
        return CatalogValues.record("maximumRecords", MAXIMUM_RECORDS, "omitted", omitted, "records", records);
    }

    static String text(String text, int limit) {
        return text == null || text.length() <= limit ? text : text.substring(0, limit) + "…";
    }

    private static final class Entry {
        private final Map<String, Object> record;
        private long count = 1;

        private Entry(Map<String, Object> record) {
            this.record = record;
        }
    }
}
