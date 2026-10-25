package betamoon.interop.source;

import betamoon.interop.catalog.CatalogFingerprint;
import betamoon.interop.catalog.CatalogValues;
import betamoon.interop.discovery.NativeOriginCache;
import betamoon.interop.discovery.OriginMetadata;
import java.io.ByteArrayInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** File/type navigation with bounded source reads from validated IDs, never caller-supplied filesystem paths. */
public final class NativeSourceIndex {
    private final Map<String, Location> locations = new TreeMap<>();
    private final List<Map<String, Object>> files = new ArrayList<>();
    private final Map<String, List<Map<String, Object>>> filesByPath = new TreeMap<>();

    public NativeSourceIndex(List<NativeSourceAttachment> attachments, NativeOriginCache cache,
            List<Map<String, Object>> bundledFiles, List<String> warnings) {
        for (Map<String, Object> file : bundledFiles) {
            Map<String, Object> record = new LinkedHashMap<>(file);
            record.put("id", "bundled:" + file.get("rootId") + ":" + file.get("path"));
            record.put("available", false);
            record.put("confidence", "file_manifest_only");
            files.add(CatalogValues.object(CatalogValues.freeze(record)));
        }
        for (NativeSourceAttachment attachment : attachments) {
            try {
                OriginMetadata metadata = cache.load(attachment.path, null, null, warnings).metadata;
                for (Map<String, Object> file : metadata.files) {
                    String path = CatalogValues.text(file, "path");
                    if (path.endsWith(".java") && file.get("sha256") instanceof String) {
                        String id = "source-" + CatalogFingerprint.sha256((attachment.id + ":" + path)
                                .getBytes(StandardCharsets.UTF_8)).substring(0, 24);
                        if (locations.size() >= 10000 || locations.containsKey(id)) {
                            throw new IOException("source_count_or_id_limit");
                        }
                        locations.put(id, new Location(attachment, path, (String) file.get("sha256")));
                        files.add(CatalogValues.object(CatalogValues.freeze(CatalogValues.record("id", id,
                                "rootId", attachment.id, "path", path, "sha256", file.get("sha256"),
                                "available", true, "originSha256", attachment.originFingerprint,
                                "confidence", "attached_file", "memberLine", null))));
                    }
                }
            } catch (IOException | IllegalArgumentException exception) {
                NativeOriginCache.warn(warnings, "source_attachment_failed: " + attachment.id + ": "
                        + exception.getMessage());
            }
        }
        for (Map<String, Object> file : files) {
            filesByPath.computeIfAbsent(CatalogValues.text(file, "path"), key -> new ArrayList<>()).add(file);
        }
    }

    public List<Map<String, Object>> files() {
        return Collections.unmodifiableList(files);
    }

    public List<Map<String, Object>> find(String canonicalOrRuntimeName, String sourceFile, String originFingerprint) {
        if (sourceFile == null) {
            return Collections.emptyList();
        }
        String internal = canonicalOrRuntimeName.replace('.', '/');
        String packagePath = internal.substring(0, internal.lastIndexOf('/') + 1);
        String path = packagePath + sourceFile;
        List<Map<String, Object>> found = new ArrayList<>();
        for (Map<String, Object> file : filesByPath.getOrDefault(path, Collections.emptyList())) {
            Map<String, Object> record = new LinkedHashMap<>(file);
            String expected = (String) file.get("originSha256");
            record.put("confidence", expected != null ? expected.equals(originFingerprint)
                    ? "origin_fingerprint_attached" : "source_mismatch" : "package_sourcefile_unverified");
            record.put("memberLine", null);
            found.add(CatalogValues.object(CatalogValues.freeze(record)));
        }
        return found;
    }

    public Map<String, Object> read(String sourceId, int startLine, int limit) throws IOException {
        if (startLine < 1 || limit < 1 || limit > 200 || startLine > 1000000) {
            throw new IllegalArgumentException("source_page_limit");
        }
        Location location = locations.get(sourceId);
        if (location == null) {
            throw new IOException("source_unavailable");
        }
        byte[] bytes = location.read();
        if (!location.sha256.equals(CatalogFingerprint.sha256(bytes))) {
            throw new IOException("source_mismatch");
        }
        List<String> lines = new ArrayList<>();
        int lineNumber = 1;
        int byteCount = 0;
        boolean more = false;
        try (BufferedReader reader = CatalogValues.utf8(new ByteArrayInputStream(bytes))) {
            long[] remaining = {2L * 1024 * 1024};
            String line;
            while ((line = CatalogValues.line(reader, 65536, remaining)) != null) {
                if (lineNumber++ < startLine) {
                    continue;
                }
                int size = line.getBytes(StandardCharsets.UTF_8).length + 1;
                if (lines.size() == limit || byteCount + size > 65536) {
                    if (lines.isEmpty()) {
                        throw new IOException("source_page_limit");
                    }
                    more = true;
                    break;
                }
                lines.add(line);
                byteCount += size;
            }
        }
        return CatalogValues.object(CatalogValues.freeze(CatalogValues.record("sourceId", sourceId,
                "startLine", startLine, "lines", lines, "nextLine", more ? startLine + lines.size() : null,
                "lineEndings", "normalized_lf", "sha256", location.sha256)));
    }

    private static final class Location {
        private final NativeSourceAttachment attachment;
        private final String path;
        private final String sha256;

        private Location(NativeSourceAttachment attachment, String path, String sha256) {
            this.attachment = attachment;
            this.path = path;
            this.sha256 = sha256;
        }

        private byte[] read() throws IOException {
            if (Files.isDirectory(attachment.path)) {
                Path root = attachment.path.toRealPath();
                Path file = root.resolve(path).toRealPath();
                if (!file.startsWith(root)) {
                    throw new IOException("source_outside_attachment");
                }
                try (InputStream input = Files.newInputStream(file)) {
                    return CatalogValues.bytes(input, 2 * 1024 * 1024);
                }
            }
            try (ZipFile zip = new ZipFile(attachment.path.toFile())) {
                ZipEntry entry = zip.getEntry(path);
                if (entry == null || entry.isDirectory()) {
                    throw new IOException("source_unavailable");
                }
                try (InputStream input = zip.getInputStream(entry)) {
                    return CatalogValues.bytes(input, 2 * 1024 * 1024);
                }
            }
        }
    }
}
