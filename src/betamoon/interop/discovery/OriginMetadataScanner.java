package betamoon.interop.discovery;

import betamoon.interop.catalog.CatalogFingerprint;
import betamoon.interop.catalog.CatalogJson;
import betamoon.interop.catalog.CatalogValues;
import betamoon.interop.catalog.ClassArchiveReader;
import betamoon.interop.catalog.ClassFileMetadataReader;
import betamoon.interop.catalog.NativeClassInfo;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** No class definition, dependency resolution, archive extraction or symlink traversal. */
public final class OriginMetadataScanner {
    public static final int MAX_FILES = 100000;
    public static final int MAX_CLASS_BYTES = 8 * 1024 * 1024;
    public static final int MAX_CLASSES = 20000;
    public static final long MAX_TOTAL_CLASS_BYTES = 64L * 1024 * 1024;
    public static final long MAX_TOTAL_SOURCE_BYTES = 64L * 1024 * 1024;

    public Inspection inspect(Path path) throws IOException {
        interrupted();
        if (!Files.isDirectory(path)) {
            if (Files.size(path) > 512L * 1024 * 1024) {
                throw new IOException("origin_size_limit");
            }
            return new Inspection(CatalogFingerprint.sha256(path, 512L * 1024 * 1024),
                    "archive_sha256", Collections.emptyList());
        }
        Map<String, Map<String, Object>> files = new TreeMap<>();
        long[] nameCharacters = {0};
        long[] classBytes = {0};
        Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path entry, BasicFileAttributes attributes) throws IOException {
                interrupted();
                if (!attributes.isRegularFile()) {
                    return FileVisitResult.CONTINUE;
                }
                String name = path.relativize(entry).toString().replace('\\', '/');
                ClassArchiveReader.validateEntryName(name);
                if (files.size() >= MAX_FILES || (nameCharacters[0] += name.length()) > 8 * 1024 * 1024) {
                    throw new IOException("origin_entry_limit");
                }
                String hash = null;
                if (name.endsWith(".class") || name.endsWith(".java")) {
                    if (attributes.size() > MAX_CLASS_BYTES
                            || (classBytes[0] += attributes.size()) > MAX_TOTAL_CLASS_BYTES) {
                        throw new IOException("origin_class_byte_limit");
                    }
                    try (InputStream input = Files.newInputStream(entry)) {
                        hash = CatalogFingerprint.sha256(CatalogValues.bytes(input, MAX_CLASS_BYTES));
                    }
                }
                files.put(name, CatalogValues.record("path", name, "size", attributes.size(), "sha256", hash));
                return FileVisitResult.CONTINUE;
            }
        });
        List<Map<String, Object>> entries = new ArrayList<>(files.values());
        String hash = CatalogFingerprint.sha256(CatalogJson.encode(entries).getBytes(StandardCharsets.UTF_8));
        return new Inspection(hash, "directory_class_source_manifest", entries);
    }

    public OriginMetadata extract(Path path, Inspection inspected, Map<String, NativeClassInfo> bundled)
            throws IOException {
        Map<String, NativeClassInfo> classes = new TreeMap<>();
        List<Map<String, Object>> files;
        if ("archive_sha256".equals(inspected.kind)) {
            if (bundled != null) {
                classes.putAll(bundled);
            } else {
                classes.putAll(new ClassArchiveReader(MAX_FILES, MAX_CLASS_BYTES, MAX_TOTAL_CLASS_BYTES)
                        .read(path).getClasses());
            }
            files = archiveFiles(path);
        } else {
            files = inspected.files;
            for (Map<String, Object> entry : files) {
                interrupted();
                String name = (String) entry.get("path");
                if (name.endsWith(".class")) {
                    Path file = path.resolve(name);
                    byte[] bytes;
                    try (InputStream input = Files.newInputStream(file)) {
                        bytes = CatalogValues.bytes(input, MAX_CLASS_BYTES);
                    }
                    if (bytes.length > MAX_CLASS_BYTES
                            || !entry.get("sha256").equals(CatalogFingerprint.sha256(bytes))) {
                        throw new IOException("origin_changed_during_scan");
                    }
                    NativeClassInfo type = new ClassFileMetadataReader().read(bytes);
                    if (!name.equals(type.getInternalName() + ".class")) {
                        throw new IOException("class_entry_name_mismatch: " + name);
                    }
                    classes.put(type.getInternalName(), type);
                }
            }
        }
        if (classes.size() > MAX_CLASSES || !inspected.fingerprint.equals(inspect(path).fingerprint)) {
            throw new IOException("origin_changed_or_class_limit");
        }
        return new OriginMetadata(inspected.fingerprint, inspected.kind, classes, files);
    }

    private List<Map<String, Object>> archiveFiles(Path path) throws IOException {
        Map<String, Map<String, Object>> files = new TreeMap<>();
        long nameCharacters = 0;
        long sourceBytes = 0;
        try (ZipFile zip = new ZipFile(path.toFile())) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                interrupted();
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                ClassArchiveReader.validateEntryName(name);
                if (files.size() >= MAX_FILES || (nameCharacters += name.length()) > 8 * 1024 * 1024) {
                    throw new IOException("origin_entry_limit");
                }
                String hash = null;
                if (!entry.isDirectory() && name.endsWith(".java")) {
                    try (InputStream input = zip.getInputStream(entry)) {
                        byte[] bytes = CatalogValues.bytes(input, 2 * 1024 * 1024);
                        if ((sourceBytes += bytes.length) > MAX_TOTAL_SOURCE_BYTES) {
                            throw new IOException("origin_source_byte_limit");
                        }
                        hash = CatalogFingerprint.sha256(bytes);
                    }
                }
                if (files.put(name, CatalogValues.record("path", name, "size", entry.getSize(),
                        "directory", entry.isDirectory(), "sha256", hash)) != null) {
                    throw new IOException("duplicate_origin_entry");
                }
            }
        }
        return new ArrayList<>(files.values());
    }

    public static void interrupted() throws IOException {
        if (Thread.currentThread().isInterrupted()) {
            throw new IOException("scan_cancelled");
        }
    }

    public static final class Inspection {
        public final String fingerprint;
        public final String kind;
        private final List<Map<String, Object>> files;

        private Inspection(String fingerprint, String kind, List<Map<String, Object>> files) {
            this.fingerprint = fingerprint;
            this.kind = kind;
            this.files = files;
        }
    }
}
