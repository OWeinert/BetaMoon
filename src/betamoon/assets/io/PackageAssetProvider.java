package betamoon.assets.io;

import betamoon.assets.AssetPath;
import java.io.File;
import java.io.IOException;

/**
 * Selects the default asset root belonging to a loose script or manifested
 * directory/ZIP package.
 */
public final class PackageAssetProvider implements AssetProvider {
    private static final String MANIFEST = "betamoon.mod.json";
    private static final String ASSET_ROOT = "assets";

    private final File root;
    private final AssetProvider looseAssets;

    public PackageAssetProvider(File root) throws IOException {
        this.root = root.getCanonicalFile();
        looseAssets = new FileAssetProvider(this.root);
    }

    @Override
    public AssetProvider forSource(String source) throws IOException {
        if (source == null || source.isEmpty()) {
            return looseAssets;
        }

        String normalized = source.replace('\\', '/');
        int archiveSeparator = normalized.indexOf("!/");
        if (archiveSeparator >= 0) {
            return archiveAssets(normalized.substring(0, archiveSeparator));
        }

        int pathSeparator = normalized.indexOf('/');
        if (pathSeparator < 0) {
            return looseAssets;
        }
        return directoryAssets(normalized.substring(0, pathSeparator));
    }

    @Override
    public boolean exists(AssetPath path) throws IOException {
        return looseAssets.exists(path);
    }

    @Override
    public byte[] read(AssetPath path, int maxBytes) throws IOException {
        return looseAssets.read(path, maxBytes);
    }

    @Override
    public String getName() {
        return root.toString();
    }

    private AssetProvider directoryAssets(String directoryName) throws IOException {
        File packageRoot = containedChild(directoryName);
        if (!packageRoot.isDirectory() || !new File(packageRoot, MANIFEST).isFile()) {
            throw new IOException("Lua package source is unavailable: " + directoryName);
        }
        return new FileAssetProvider(new File(packageRoot, ASSET_ROOT));
    }

    private AssetProvider archiveAssets(String archiveName) throws IOException {
        File archive = containedChild(archiveName);
        if (!archive.isFile()) {
            throw new IOException("Lua package archive is unavailable: " + archiveName);
        }
        return new PrefixedAssetProvider(new ZipAssetProvider(archive), ASSET_ROOT + "/");
    }

    private File containedChild(String name) throws IOException {
        if (name.isEmpty() || name.indexOf('/') >= 0 || name.indexOf('\\') >= 0 || name.equals(".")
                || name.equals("..")) {
            throw new IOException("Invalid Lua package source: " + name);
        }
        File child = new File(root, name).getCanonicalFile();
        if (!child.getParentFile().equals(root)) {
            throw new IOException("Lua package source escapes the scripts directory: " + name);
        }
        return child;
    }

    private static final class PrefixedAssetProvider implements AssetProvider {
        private final AssetProvider delegate;
        private final String prefix;

        private PrefixedAssetProvider(AssetProvider delegate, String prefix) {
            this.delegate = delegate;
            this.prefix = prefix;
        }

        @Override
        public boolean exists(AssetPath path) throws IOException {
            return delegate.exists(prefixed(path));
        }

        @Override
        public byte[] read(AssetPath path, int maxBytes) throws IOException {
            return delegate.read(prefixed(path), maxBytes);
        }

        @Override
        public String getName() {
            return delegate.getName() + "!/" + ASSET_ROOT;
        }

        private AssetPath prefixed(AssetPath path) {
            return AssetPath.parse(prefix + path);
        }
    }
}
