package betamoon.interop;

import betamoon.interop.catalog.CatalogFingerprint;
import betamoon.interop.catalog.CatalogJson;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

/** Writes deterministic UTF-8 resources, recording content identities for the final manifest. */
public final class CatalogBundleWriter {
    private final Path directory;
    private final Map<String, String> fileHashes = new TreeMap<>();
    private final Map<String, String> recordHashes = new TreeMap<>();

    public CatalogBundleWriter(Path directory) throws IOException {
        this.directory = directory;
        Files.createDirectories(directory);
    }

    public void records(String name, Map<String, Map<String, Object>> records) throws IOException {
        Path temporary = Files.createTempFile(directory, name, ".tmp");
        try {
            try (BufferedWriter writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                for (Map.Entry<String, Map<String, Object>> entry : new TreeMap<>(records).entrySet()) {
                    String json = CatalogJson.encode(entry.getValue());
                    writer.write(json);
                    writer.write('\n');
                    String id = name + ":" + entry.getKey();
                    recordHashes.put(id, CatalogFingerprint.sha256(json.getBytes(StandardCharsets.UTF_8)));
                }
            }
            publish(temporary, directory.resolve(name));
            fileHashes.put(name, CatalogFingerprint.sha256(directory.resolve(name)));
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    public void document(String name, Object record) throws IOException {
        writeDocument(directory.resolve(name), record);
        fileHashes.put(name, CatalogFingerprint.sha256(directory.resolve(name)));
    }

    public Map<String, String> getFileHashes() {
        return Collections.unmodifiableMap(new TreeMap<>(fileHashes));
    }

    public Map<String, String> getRecordHashes() {
        return Collections.unmodifiableMap(recordHashes);
    }

    public static void writeDocument(Path path, Object record) throws IOException {
        Files.createDirectories(path.getParent());
        Path temporary = Files.createTempFile(path.getParent(), path.getFileName().toString(), ".tmp");
        try {
            Files.write(temporary, (CatalogJson.encode(record) + "\n").getBytes(StandardCharsets.UTF_8));
            publish(temporary, path);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static void publish(Path temporary, Path destination) throws IOException {
        try {
            Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
