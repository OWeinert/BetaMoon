package betamoon.debug;

import betamoon.interop.catalog.CatalogJson;
import betamoon.interop.catalog.CatalogFingerprint;
import betamoon.interop.catalog.CatalogValues;
import betamoon.interop.catalog.NativeAuthoringCatalog;
import betamoon.interop.discovery.NativeDiscoveryRuntime;
import betamoon.interop.discovery.NativeIndexSnapshot;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Uses one immutable generation; never starts a scan, binds classes or scrapes
 * live object fields.
 */
final class DebugNativeExporter implements DebugExporter {
    private final boolean detailed;
    private final Supplier<NativeIndexSnapshot> snapshots;
    private final Supplier<Map<String, Object>> statuses;
    private final int rowLimit;

    DebugNativeExporter(boolean detailed) {
        this(detailed, NativeDiscoveryRuntime::snapshot, NativeDiscoveryRuntime::status, 100000);
    }

    DebugNativeExporter(boolean detailed, Supplier<NativeIndexSnapshot> snapshots) {
        this(detailed, snapshots, 100000);
    }

    DebugNativeExporter(boolean detailed, Supplier<NativeIndexSnapshot> snapshots, int rowLimit) {
        this(detailed, snapshots, Collections::emptyMap, rowLimit);
    }

    DebugNativeExporter(boolean detailed, Supplier<NativeIndexSnapshot> snapshots,
            Supplier<Map<String, Object>> statuses, int rowLimit) {
        this.detailed = detailed;
        this.snapshots = snapshots;
        this.statuses = statuses;
        this.rowLimit = rowLimit;
    }

    @Override
    public void export(DebugExportSession session) throws Exception {
        NativeIndexSnapshot snapshot = snapshots.get();
        Map<String, Object> status = statuses.get();
        String failureJson = CatalogJson.encode(CatalogValues.record("schemaVersion", 1, "generation",
                snapshot.generation, "namespace", snapshot.namespace, "mappingSha256", snapshot.mappingSha256,
                "evidence", "exported_snapshot", "execution", status.get("execution"), "scanFailure",
                status.get("lastFailure"), "warnings", snapshot.warnings)) + "\n";
        byte[] failureBytes = failureJson.getBytes(StandardCharsets.UTF_8);
        session.writeTextFile("native_summary.txt", writer -> {
            writer.write("Generation: " + snapshot.generation + "\nNamespace: " + snapshot.namespace
                    + "\nMapping SHA-256: " + snapshot.mappingSha256 + "\n");
            for (String kind : NativeIndexSnapshot.KINDS) {
                writer.write(kind + ": " + snapshot.rows(kind).size() + "\n");
            }
            writer.write("Coverage: " + CatalogJson.encode(snapshot.coverage) + "\n");
            writer.write("Scan status: " + CatalogJson.encode(status) + "\n");
            writer.write("Loaded state describes the captured census, not subsequent class loading.\n");
            writer.write("Source bodies and live object values are not included.\n");
            return 1;
        });
        session.writeTextFile("native_failures.txt", writer -> {
            if (status.get("lastFailure") != null) {
                writer.write(DebugExportNames.escapeText(String.valueOf(status.get("lastFailure"))) + "\n");
            }
            for (String warning : snapshot.warnings) {
                writer.write(DebugExportNames.escapeText(warning));
                writer.write('\n');
            }
            if (status.get("execution") instanceof Map) {
                writer.write("Native execution diagnostics: " + CatalogJson.encode(status.get("execution")) + "\n");
            }
            return snapshot.warnings.size() + (status.get("lastFailure") == null ? 0 : 1);
        });
        session.writeStructuredFile("native_failures.json", writer -> {
            writer.write(failureJson);
            return 1;
        });
        session.writeStructuredFile("native_hooks.json", writer -> {
            writer.write(CatalogJson.encode(CatalogValues.record("schemaVersion", 1, "generation", snapshot.generation,
                    "status", snapshot.coverage.get("hooks"), "revision", snapshot.hookRevision, "items",
                    snapshot.rows("hooks"))));
            writer.write('\n');
            return snapshot.rows("hooks").size();
        });
        if (!detailed) {
            return;
        }
        session.writeStructuredFile("native_origins.json", writer -> {
            writer.write(CatalogJson.encode(CatalogValues.record("schemaVersion", 1, "generation", snapshot.generation,
                    "coverage", snapshot.coverage, "origins", snapshot.rows("origins"), "loaders",
                    snapshot.rows("loaders"))));
            writer.write('\n');
            return snapshot.rows("origins").size() + snapshot.rows("loaders").size();
        });
        rows(session, snapshot, "classes", "native_classes.jsonl");
        rows(session, snapshot, "members", "native_members.jsonl");
        rows(session, snapshot, "sources", "native_sources.jsonl");
        NativeAuthoringCatalog.Payload[] payload = new NativeAuthoringCatalog.Payload[1];
        session.writeStructuredFile(NativeAuthoringCatalog.RECORDS_FILE, writer -> {
            payload[0] = NativeAuthoringCatalog.write(writer, snapshot);
            return payload[0].rows;
        });
        session.writeStructuredFile(NativeAuthoringCatalog.MANIFEST_FILE, writer -> {
            Map<String, Object> manifest = new LinkedHashMap<>(payload[0].manifest());
            manifest.put("diagnostics", CatalogValues.record("path", "native_failures.json", "bytes",
                    failureBytes.length, "sha256", CatalogFingerprint.sha256(failureBytes)));
            writer.write(CatalogJson.encode(manifest));
            writer.write('\n');
            return 1;
        });
    }

    private void rows(DebugExportSession session, NativeIndexSnapshot snapshot, String kind, String filename)
            throws Exception {
        List<Map<String, Object>> records = snapshot.rows(kind);
        session.writeStructuredFile(filename, writer -> {
            writer.write(CatalogJson.encode(
                    CatalogValues.record("recordType", "header", "schemaVersion", 1, "generation", snapshot.generation,
                            "kind", kind, "totalRecords", records.size(), "coverage", snapshot.coverage)));
            writer.write('\n');
            int count = 0;
            long characters = 0;
            for (Map<String, Object> record : records) {
                String json = CatalogJson.encode(record);
                if (count >= rowLimit || (characters += json.length()) > 16 * 1024 * 1024) {
                    break;
                }
                writer.write(json);
                writer.write('\n');
                count++;
            }
            boolean truncated = count != records.size();
            writer.write(CatalogJson.encode(CatalogValues.record("recordType", "completion", "generation",
                    snapshot.generation, "writtenRecords", count, "truncated", truncated)));
            writer.write('\n');
            if (truncated) {
                session.warn(filename + " truncated: " + count + " of " + records.size() + " records.");
                session.incomplete("Native catalog export reached its size limit; use targeted queries for more data.");
            }
            return count;
        });
    }
}
