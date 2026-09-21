package betamoon.assets.io;

import betamoon.assets.AssetPath;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Reads actual pack entries without classpath fallback or retaining native ZIP
 * handles.
 */
public final class ZipAssetProvider implements AssetProvider {
    private final File archive;

    public ZipAssetProvider(File archive) {
        this.archive = archive;
    }

    @Override
    public boolean exists(AssetPath path) throws IOException {
        try (ZipFile zip = new ZipFile(archive)) {
            ZipEntry entry = zip.getEntry(path.toString());
            return entry != null && !entry.isDirectory();
        }
    }

    @Override
    public byte[] read(AssetPath path, int maxBytes) throws IOException {
        try (ZipFile zip = new ZipFile(archive)) {
            ZipEntry entry = zip.getEntry(path.toString());
            if (entry == null || entry.isDirectory()) {
                return null;
            }
            if (entry.getSize() > maxBytes) {
                throw new IOException("Pack entry exceeds " + maxBytes + " bytes: " + path);
            }
            try (InputStream input = zip.getInputStream(entry)) {
                return AssetStreams.read(input, maxBytes);
            }
        }
    }

    @Override
    public String getName() {
        return archive.getName();
    }
}
