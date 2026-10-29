package betamoon.interop.discovery;

import betamoon.interop.catalog.CatalogJson;
import betamoon.interop.catalog.CatalogValues;
import betamoon.interop.catalog.ClassArchiveReader;
import betamoon.interop.catalog.NativeCatalogRecords;
import betamoon.interop.catalog.NativeClassInfo;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeMap;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/** Disposable content-addressed metadata cache. No Java serialization or persisted runtime authority. */
public final class NativeOriginCache {
    private final Path root;

    public NativeOriginCache(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    public Result load(Path origin, Map<String, NativeClassInfo> bundled, String bundledFingerprint,
            List<String> warnings) throws IOException {
        OriginMetadataScanner scanner = new OriginMetadataScanner();
        OriginMetadataScanner.Inspection inspected = scanner.inspect(origin);
        Path cache = location(inspected.fingerprint);
        if (Files.isRegularFile(cache, LinkOption.NOFOLLOW_LINKS)) {
            try {
                OriginMetadata metadata = read(cache, inspected.fingerprint, inspected.kind);
                if (!inspected.fingerprint.equals(scanner.inspect(origin).fingerprint)) {
                    throw new IOException("origin_changed_during_cache_verification");
                }
                return new Result(metadata, true);
            } catch (IOException | IllegalArgumentException exception) {
                warn(warnings, "cache_rebuilt: " + inspected.fingerprint + ": " + exception.getMessage());
            }
        }
        OriginMetadata metadata = scanner.extract(origin, inspected,
                inspected.fingerprint.equals(bundledFingerprint) ? bundled : null);
        try {
            write(cache, metadata);
            evict();
        } catch (IOException | SecurityException exception) {
            warn(warnings, "cache_write_failed: " + exception.getMessage());
        }
        return new Result(metadata, false);
    }

    private Path location(String fingerprint) {
        if (!fingerprint.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Invalid cache content identity");
        }
        return root.resolve("origins").resolve(fingerprint).resolve("metadata-v1.json.gz");
    }

    private OriginMetadata read(Path path, String fingerprint, String kind) throws IOException {
        Map<String, NativeClassInfo> classes = new TreeMap<>();
        List<Map<String, Object>> files = new ArrayList<>();
        Set<String> paths = new HashSet<>();
        long[] remaining = {64L * 1024 * 1024};
        try (BufferedReader reader = CatalogValues.utf8(new GZIPInputStream(Files.newInputStream(path)))) {
            String line = CatalogValues.line(reader, 2 * 1024 * 1024, remaining);
            Map<String, Object> header = CatalogValues.object(CatalogJson.decode(line));
            if (CatalogValues.integer(header, "schemaVersion") != 1
                    || !NativeCatalogRecords.EXTRACTOR_VERSION.equals(header.get("extractorVersion"))
                    || !fingerprint.equals(header.get("fingerprint")) || !kind.equals(header.get("fingerprintKind"))) {
                throw new IOException("cache_header_mismatch");
            }
            int expectedClasses = CatalogValues.integer(header, "classes");
            int expectedFiles = CatalogValues.integer(header, "files");
            while ((line = CatalogValues.line(reader, 2 * 1024 * 1024, remaining)) != null) {
                OriginMetadataScanner.interrupted();
                Map<String, Object> record = CatalogValues.object(CatalogJson.decode(line));
                if (record.containsKey("type")) {
                    NativeClassInfo type = NativeCatalogRecords.readType(CatalogValues.object(record.get("type")));
                    if (classes.size() >= OriginMetadataScanner.MAX_CLASSES
                            || classes.put(type.getInternalName(), type) != null) {
                        throw new IOException("cache_class_limit_or_duplicate");
                    }
                } else if (record.containsKey("file")) {
                    Map<String, Object> file = CatalogValues.object(record.get("file"));
                    String name = CatalogValues.text(file, "path");
                    ClassArchiveReader.validateEntryName(name);
                    if (files.size() >= OriginMetadataScanner.MAX_FILES || !paths.add(name)) {
                        throw new IOException("cache_file_limit");
                    }
                    files.add(file);
                } else {
                    throw new IOException("cache_record_invalid");
                }
            }
            if (classes.size() != expectedClasses || files.size() != expectedFiles) {
                throw new IOException("cache_record_count_mismatch");
            }
        }
        return new OriginMetadata(fingerprint, kind, classes, files);
    }

    private void write(Path path, OriginMetadata metadata) throws IOException {
        Files.createDirectories(path.getParent());
        Path temporary = Files.createTempFile(path.getParent(), "metadata-", ".tmp");
        try {
            try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                    new GZIPOutputStream(Files.newOutputStream(temporary)), StandardCharsets.UTF_8))) {
                long[] remaining = {64L * 1024 * 1024};
                writeRecord(writer, CatalogValues.record("schemaVersion", 1,
                        "extractorVersion", NativeCatalogRecords.EXTRACTOR_VERSION,
                        "fingerprint", metadata.fingerprint, "fingerprintKind", metadata.fingerprintKind,
                        "classes", metadata.classes.size(), "files", metadata.files.size()), remaining);
                for (NativeClassInfo type : metadata.classes.values()) {
                    OriginMetadataScanner.interrupted();
                    writeRecord(writer, CatalogValues.record("type", NativeCatalogRecords.type(type)), remaining);
                }
                for (Map<String, Object> file : metadata.files) {
                    OriginMetadataScanner.interrupted();
                    writeRecord(writer, CatalogValues.record("file", file), remaining);
                }
            }
            try {
                Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static void writeRecord(BufferedWriter writer, Map<String, Object> record, long[] remaining)
            throws IOException {
        String json = CatalogJson.encode(record);
        remaining[0] -= json.length() + 1L;
        if (json.length() >= 2 * 1024 * 1024 || remaining[0] < 0) {
            throw new IOException("cache_text_size_limit");
        }
        writer.write(json);
        writer.write('\n');
    }

    private void evict() throws IOException {
        Path origins = root.resolve("origins").toRealPath();
        List<Path> entries = new ArrayList<>();
        long bytes = 0;
        try (DirectoryStream<Path> directories = Files.newDirectoryStream(origins)) {
            for (Path directory : directories) {
                if (!Files.isSymbolicLink(directory) && directory.getFileName().toString().matches("[0-9a-f]{64}")) {
                    Path file = directory.resolve("metadata-v1.json.gz");
                    if (Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
                        entries.add(file);
                        bytes += Files.size(file);
                    }
                }
            }
        }
        entries.sort(Comparator.comparingLong(path -> path.toFile().lastModified()));
        int count = entries.size();
        for (Path file : entries) {
            if (count <= 128 && bytes <= 256L * 1024 * 1024) {
                break;
            }
            Path resolved = file.toRealPath();
            if (!resolved.startsWith(origins) || !resolved.getParent().getParent().equals(origins)) {
                throw new IOException("cache_eviction_path_invalid");
            }
            bytes -= Files.size(resolved);
            // Delete only this verified disposable file; never recursively delete an origin or user path.
            Files.delete(resolved);
            count--;
        }
    }

    public static void warn(List<String> warnings, String message) {
        if (warnings.size() < 64) {
            warnings.add(message);
        }
    }

    public static final class Result {
        public final OriginMetadata metadata;
        public final boolean cacheHit;

        private Result(OriginMetadata metadata, boolean cacheHit) {
            this.metadata = metadata;
            this.cacheHit = cacheHit;
        }
    }
}
