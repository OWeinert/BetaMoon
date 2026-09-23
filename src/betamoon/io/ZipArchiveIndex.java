package betamoon.io;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Validated, immutable index of a ZIP archive with bounded entry reads. */
public final class ZipArchiveIndex {
    public static final int MAX_ENTRIES = 10000;
    public static final int MAX_ENTRY_NAME_LENGTH = 512;

    private final File archive;
    private final Map<String, Entry> entries;
    private final List<String> files;

    private ZipArchiveIndex(File archive, Map<String, Entry> entries, List<String> files) {
        this.archive = archive;
        this.entries = Collections.unmodifiableMap(entries);
        this.files = Collections.unmodifiableList(files);
    }

    public static ZipArchiveIndex open(File archive) throws IOException {
        if (archive == null || !archive.isFile()) {
            throw new IOException("ZIP archive does not exist: " + archive);
        }
        File canonical = archive.getCanonicalFile();
        Map<String, Entry> entries = new LinkedHashMap<>();
        Map<String, String> caseInsensitiveNames = new HashMap<>();
        List<String> files = new ArrayList<>();
        try (ZipFile zip = new ZipFile(canonical)) {
            Enumeration<? extends ZipEntry> values = zip.entries();
            int count = 0;
            while (values.hasMoreElements()) {
                if (++count > MAX_ENTRIES) {
                    throw new IOException("ZIP archive exceeds " + MAX_ENTRIES + " entries: " + canonical.getName());
                }
                ZipEntry value = values.nextElement();
                String name = validateName(value.getName(), value.isDirectory());
                if (name == null) {
                    continue;
                }
                if (entries.containsKey(name)) {
                    throw new IOException("ZIP archive contains duplicate entry: " + name);
                }
                String collisionKey = name.toLowerCase(Locale.ROOT);
                String previous = caseInsensitiveNames.put(collisionKey, name);
                if (previous != null) {
                    throw new IOException("ZIP archive contains case-colliding entries: " + previous + " and " + name);
                }
                entries.put(name, new Entry(value.isDirectory(), value.getSize()));
                if (!value.isDirectory()) {
                    files.add(name);
                }
            }
        }
        Collections.sort(files);
        return new ZipArchiveIndex(canonical, entries, files);
    }

    public File getArchive() {
        return archive;
    }

    public boolean containsFile(String path) {
        Entry entry = entries.get(path);
        return entry != null && !entry.directory;
    }

    public long sizeOf(String path) {
        Entry entry = entries.get(path);
        return entry == null ? -1L : entry.size;
    }

    public List<String> getFiles() {
        return files;
    }

    public byte[] read(String path, int maxBytes) throws IOException {
        if (maxBytes <= 0) {
            throw new IllegalArgumentException("ZIP entry byte limit must be positive");
        }
        Entry indexed = entries.get(path);
        if (indexed == null || indexed.directory) {
            return null;
        }
        if (indexed.size > maxBytes) {
            throw new IOException("ZIP entry exceeds " + maxBytes + " bytes: " + path);
        }
        try (ZipFile zip = new ZipFile(archive)) {
            ZipEntry entry = zip.getEntry(path);
            if (entry == null || entry.isDirectory()) {
                throw new IOException("ZIP entry disappeared while reading: " + path);
            }
            try (InputStream input = zip.getInputStream(entry)) {
                return readBounded(input, maxBytes, path);
            }
        }
    }

    private static byte[] readBounded(InputStream input, int maxBytes, String path) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) != -1) {
            if (count > maxBytes - output.size()) {
                throw new IOException("ZIP entry exceeds " + maxBytes + " bytes: " + path);
            }
            output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }

    private static String validateName(String raw, boolean directory) throws IOException {
        if (raw == null || raw.isEmpty() || raw.length() > MAX_ENTRY_NAME_LENGTH || raw.indexOf('\\') >= 0
                || raw.startsWith("/")) {
            throw new IOException("ZIP archive contains an invalid entry path: " + raw);
        }
        String name = directory && raw.endsWith("/") ? raw.substring(0, raw.length() - 1) : raw;
        if (name.isEmpty()) {
            return null;
        }
        String[] segments = name.split("/", -1);
        for (int i = 0; i < segments.length; i++) {
            String segment = segments[i];
            if (segment.isEmpty() || ".".equals(segment) || "..".equals(segment) || segment.endsWith(".")) {
                throw new IOException("ZIP archive contains an invalid entry path: " + raw);
            }
            for (int characterIndex = 0; characterIndex < segment.length(); characterIndex++) {
                char character = segment.charAt(characterIndex);
                if (Character.isISOControl(character) || ":*?\"<>|".indexOf(character) >= 0) {
                    throw new IOException("ZIP archive contains an invalid entry path: " + raw);
                }
            }
        }
        return name;
    }

    private static final class Entry {
        private final boolean directory;
        private final long size;

        private Entry(boolean directory, long size) {
            this.directory = directory;
            this.size = size;
        }
    }
}
