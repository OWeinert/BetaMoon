package betamoon.interop.catalog;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Bounded archive extraction. Does not extract files, resolve dependencies, or call a classloader. */
public final class ClassArchiveReader {
    private final int maxEntries;
    private final int maxClassBytes;
    private final long maxTotalClassBytes;

    public ClassArchiveReader() {
        this(100000, 8 * 1024 * 1024, 512L * 1024 * 1024);
    }

    public ClassArchiveReader(int maxEntries, int maxClassBytes, long maxTotalClassBytes) {
        if (maxEntries < 1 || maxClassBytes < 1 || maxTotalClassBytes < 1) {
            throw new IllegalArgumentException("Archive limits must be positive");
        }
        this.maxEntries = maxEntries;
        this.maxClassBytes = maxClassBytes;
        this.maxTotalClassBytes = maxTotalClassBytes;
    }

    public ClassArchiveCatalog read(Path archive) throws IOException {
        String fingerprint = CatalogFingerprint.sha256(archive);
        Map<String, NativeClassInfo> classes = new TreeMap<>();
        Map<String, String> fingerprints = new TreeMap<>();
        Set<String> entries = new HashSet<>();
        long totalBytes = 0;
        long totalNameCharacters = 0;
        try (ZipFile zip = new ZipFile(archive.toFile())) {
            Enumeration<? extends ZipEntry> iterator = zip.entries();
            while (iterator.hasMoreElements()) {
                ZipEntry entry = iterator.nextElement();
                String name = entry.getName();
                validateEntryName(name);
                totalNameCharacters += name.length();
                if (totalNameCharacters > 8 * 1024 * 1024) {
                    throw new IOException("Archive entry-name size limit exceeded");
                }
                if (!entries.add(name)) {
                    throw new IOException("Duplicate archive entry: " + name);
                }
                if (entries.size() > maxEntries) {
                    throw new IOException("Archive entry limit exceeded");
                }
                // Java 8 does not select multi-release class overlays.
                if (entry.isDirectory() || !name.endsWith(".class") || name.startsWith("META-INF/versions/")) {
                    continue;
                }
                byte[] bytes;
                try (InputStream input = zip.getInputStream(entry)) {
                    bytes = readBounded(input, Math.min(maxClassBytes, maxTotalClassBytes - totalBytes));
                }
                totalBytes += bytes.length;
                NativeClassInfo type;
                try {
                    type = new ClassFileMetadataReader().read(bytes);
                } catch (RuntimeException exception) {
                    throw new IOException("Invalid class entry: " + name, exception);
                }
                if (!name.equals(type.getInternalName() + ".class")) {
                    throw new IOException("Class entry does not match its declared name: " + name);
                }
                if (classes.put(type.getInternalName(), type) != null) {
                    throw new IOException("Duplicate declared class: " + type.getInternalName());
                }
                fingerprints.put(type.getInternalName(), CatalogFingerprint.sha256(bytes));
            }
        }
        if (!fingerprint.equals(CatalogFingerprint.sha256(archive))) {
            throw new IOException("Archive changed during catalog extraction");
        }
        return new ClassArchiveCatalog(fingerprint, classes, fingerprints);
    }

    public static void validateEntryName(String name) throws IOException {
        if (name.isEmpty() || name.length() > 4096 || name.startsWith("/") || name.indexOf('\\') >= 0
                || name.indexOf(':') >= 0
                || name.indexOf('\u0000') >= 0) {
            throw new IOException("Invalid archive entry name");
        }
        String[] components = name.split("/", -1);
        for (int i = 0; i < components.length; i++) {
            if ("..".equals(components[i]) || ".".equals(components[i])
                    || (components[i].isEmpty() && i != components.length - 1)) {
                throw new IOException("Invalid archive entry name: " + name);
            }
        }
    }

    private static byte[] readBounded(InputStream input, long limit) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) != -1) {
            if ((long) output.size() + count > limit) {
                throw new IOException("Archive class-byte limit exceeded");
            }
            output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }
}
