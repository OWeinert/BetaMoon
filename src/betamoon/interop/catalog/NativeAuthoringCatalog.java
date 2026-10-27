package betamoon.interop.catalog;

import betamoon.interop.discovery.NativeIndexSnapshot;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Portable editor handoff over detached index records. No class loading, source
 * reads or live calls.
 */
public final class NativeAuthoringCatalog {
    public static final String FORMAT = "betamoon-native-catalog";
    public static final int SCHEMA_VERSION = 1;
    public static final String MANIFEST_FILE = "native_catalog.json";
    public static final String RECORDS_FILE = "native_catalog.jsonl";
    public static final long MAXIMUM_BYTES = 512L * 1024 * 1024;
    public static final int MAXIMUM_ROW_BYTES = 1024 * 1024;
    public static final int MAXIMUM_ROWS = 1000000;

    private NativeAuthoringCatalog() {
    }

    /**
     * The caller publishes the manifest only after this entire payload has
     * succeeded.
     */
    public static Payload write(Writer writer, NativeIndexSnapshot snapshot) throws IOException {
        return write(writer, snapshot, false);
    }

    /** Build-only baseline export omits local installation paths. */
    public static Payload writeBaseline(Writer writer, NativeIndexSnapshot snapshot) throws IOException {
        return write(writer, snapshot, true);
    }

    private static Payload write(Writer writer, NativeIndexSnapshot snapshot, boolean baseline) throws IOException {
        if (!Boolean.TRUE.equals(snapshot.coverage.get("ready"))) {
            throw new IOException("native_authoring_index_pending");
        }
        MessageDigest digest = digest();
        Map<String, Object> counts = new LinkedHashMap<>();
        long bytes = 0;
        int rows = 0;
        for (String kind : NativeIndexSnapshot.KINDS) {
            counts.put(kind, snapshot.rows(kind).size());
            for (Map<String, Object> record : snapshot.rows(kind)) {
                if (Thread.currentThread().isInterrupted()) {
                    throw new IOException("native_authoring_cancelled");
                }
                Map<String, Object> portable = record;
                if (baseline && "origins".equals(kind)) {
                    portable = new LinkedHashMap<>(record);
                    portable.remove("path");
                    portable.put("name", "Minecraft and Forge baseline");
                }
                String line = CatalogJson.encode(CatalogValues.record("kind", kind, "record", portable)) + "\n";
                byte[] encoded = line.getBytes(StandardCharsets.UTF_8);
                bytes += encoded.length;
                rows++;
                if (encoded.length > MAXIMUM_ROW_BYTES || bytes > MAXIMUM_BYTES || rows > MAXIMUM_ROWS) {
                    throw new IOException("native_authoring_transport_limit");
                }
                digest.update(encoded);
                writer.write(line);
            }
        }
        return new Payload(snapshot, bytes, rows, hex(digest.digest()), counts);
    }

    private static MessageDigest digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Java must provide SHA-256", exception);
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder text = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            text.append(Character.forDigit((value & 255) >>> 4, 16));
            text.append(Character.forDigit(value & 15, 16));
        }
        return text.toString();
    }

    public static final class Payload {
        public final int rows;
        private final Map<String, Object> manifest;

        private Payload(NativeIndexSnapshot snapshot, long bytes, int rows, String sha256, Map<String, Object> counts) {
            this.rows = rows;
            String identity = snapshot.namespace + "\n" + snapshot.mappingSha256 + "\n" + snapshot.generation + "\n"
                    + sha256;
            Map<String, Object> file = CatalogValues.record("path", RECORDS_FILE, "bytes", bytes, "rows", rows,
                    "sha256", sha256);
            manifest = CatalogValues.record("format", FORMAT, "schemaVersion", SCHEMA_VERSION, "extractorVersion",
                    NativeCatalogRecords.EXTRACTOR_VERSION, "minecraftTarget", "b1.7.3", "side", "client", "namespace",
                    snapshot.namespace, "mappingSha256", snapshot.mappingSha256, "generation", snapshot.generation,
                    "snapshotId", CatalogFingerprint.sha256(identity.getBytes(StandardCharsets.UTF_8)),
                    "transportComplete", true, "coverage", snapshot.coverage, "counts", counts, "files",
                    Collections.singletonList(file));
        }

        public Map<String, Object> manifest() {
            return CatalogValues.object(CatalogValues.freeze(manifest));
        }
    }
}
