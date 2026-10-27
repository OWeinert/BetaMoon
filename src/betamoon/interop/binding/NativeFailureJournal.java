package betamoon.interop.binding;

import betamoon.interop.catalog.CatalogJson;
import betamoon.interop.catalog.CatalogValues;
import betamoon.interop.diagnostics.NativeDiagnosticEvidence;
import betamoon.interop.diagnostics.NativeDiagnosticGuidance;
import java.nio.charset.StandardCharsets;
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
    private static final int MAXIMUM_RECORD_BYTES = 65536;
    private static final int MAXIMUM_BYTES = 256 * 1024;
    private final Map<String, Entry> entries = new LinkedHashMap<>();
    private long omitted;
    private int encodedBytes;

    synchronized void record(NativeAccessFailure failure) {
        Map<String, Object> record = new LinkedHashMap<>(failure.diagnostic());
        record.put("reason", text(failure.reason, 128));
        record.put("message", NativeDiagnosticEvidence.bounded(failure.getMessage(), 2048));
        record.put("guidance", NativeDiagnosticGuidance.forReason(failure.reason));
        Throwable cause = failure.getCause();
        if (cause != null) {
            record.put("nativeCauseType", cause.getClass().getName());
            record.put("nativeCauses", NativeDiagnosticEvidence.capture(cause));
        }
        String key = CatalogJson.encode(record);
        int bytes = key.getBytes(StandardCharsets.UTF_8).length + 64;
        if (bytes > MAXIMUM_RECORD_BYTES) {
            omitted++;
            return;
        }
        Entry entry = entries.get(key);
        if (entry != null) {
            entry.count++;
            return;
        }
        if (entries.size() >= MAXIMUM_RECORDS || encodedBytes + bytes > MAXIMUM_BYTES - 256) {
            omitted++;
            return;
        }
        entries.put(key, new Entry(CatalogValues.object(CatalogValues.freeze(record))));
        encodedBytes += bytes;
    }

    synchronized Map<String, Object> snapshot() {
        List<Map<String, Object>> records = new ArrayList<>();
        for (Entry entry : entries.values()) {
            Map<String, Object> row = new LinkedHashMap<>(entry.record);
            row.put("count", entry.count);
            records.add(row);
        }
        return CatalogValues.record("maximumRecords", MAXIMUM_RECORDS, "maximumRecordBytes", MAXIMUM_RECORD_BYTES,
                "maximumBytes", MAXIMUM_BYTES, "encodedBytes", encodedBytes, "omitted", omitted, "records", records);
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
