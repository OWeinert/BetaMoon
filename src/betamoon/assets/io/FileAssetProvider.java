package betamoon.assets.io;

import betamoon.assets.AssetPath;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Script defaults, contained within the canonical script root even through
 * symbolic links.
 */
public final class FileAssetProvider implements AssetProvider {
    private final File root;

    public FileAssetProvider(File root) throws IOException {
        this.root = root.getCanonicalFile();
    }

    @Override
    public byte[] read(AssetPath path, int maxBytes) throws IOException {
        File file = new File(root, path.toString()).getCanonicalFile();
        if (!file.toPath().startsWith(root.toPath()) || file.equals(root)) {
            throw new IOException("Asset escapes the script root: " + path);
        }
        if (!file.isFile()) {
            return null;
        }
        if (file.length() > maxBytes) {
            throw new IOException("Asset exceeds " + maxBytes + " bytes: " + path);
        }
        try (InputStream input = new FileInputStream(file)) {
            return AssetStreams.read(input, maxBytes);
        }
    }

    @Override
    public String getName() {
        return root.toString();
    }
}
