package betamoon.assets.io;

import betamoon.assets.AssetPath;
import betamoon.io.ZipArchiveIndex;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/** Resolves legacy root assets and assets inside manifested directory or ZIP mods. */
public final class PackageAssetProvider implements AssetProvider {
    private static final String MANIFEST = "betamoon.mod.json";

    private final File root;
    private final AssetProvider rootProvider;
    private final List<AssetProvider> packages;

    public PackageAssetProvider(File root) throws IOException {
        this.root = root.getCanonicalFile();
        rootProvider = new FileAssetProvider(this.root);
        packages = discoverPackages(this.root);
    }

    @Override
    public boolean exists(AssetPath path) throws IOException {
        return resolve(path) != null;
    }

    @Override
    public byte[] read(AssetPath path, int maxBytes) throws IOException {
        AssetProvider provider = resolve(path);
        return provider == null ? null : provider.read(path, maxBytes);
    }

    @Override
    public String getName() {
        return root.toString();
    }

    private AssetProvider resolve(AssetPath path) throws IOException {
        AssetProvider selected = rootProvider.exists(path) ? rootProvider : null;
        for (int i = 0; i < packages.size(); i++) {
            AssetProvider candidate = packages.get(i);
            if (!candidate.exists(path)) {
                continue;
            }
            if (selected != null) {
                throw new IOException("Asset path is provided by both '" + selected.getName() + "' and '"
                        + candidate.getName() + "': " + path);
            }
            selected = candidate;
        }
        return selected;
    }

    private static List<AssetProvider> discoverPackages(File root) throws IOException {
        List<AssetProvider> result = new ArrayList<>();
        File[] children = root.listFiles();
        if (children == null) {
            return result;
        }
        Arrays.sort(children, new Comparator<File>() {
            @Override
            public int compare(File left, File right) {
                return left.getName().compareToIgnoreCase(right.getName());
            }
        });
        for (int i = 0; i < children.length; i++) {
            File child = children[i];
            if (child.isDirectory() && new File(child, MANIFEST).isFile()) {
                result.add(new FileAssetProvider(child));
            } else if (child.isFile() && child.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".zip")) {
                try {
                    ZipArchiveIndex archive = ZipArchiveIndex.open(child);
                    if (archive.containsFile(MANIFEST)) {
                        result.add(new ArchiveProvider(archive));
                    }
                } catch (IOException ignored) {
                    // The Lua loader reports invalid packages; keep unrelated assets available.
                }
            }
        }
        return result;
    }

    private static final class ArchiveProvider implements AssetProvider {
        private final ZipArchiveIndex archive;

        private ArchiveProvider(ZipArchiveIndex archive) {
            this.archive = archive;
        }

        @Override
        public boolean exists(AssetPath path) {
            return archive.containsFile(path.toString());
        }

        @Override
        public byte[] read(AssetPath path, int maxBytes) throws IOException {
            return archive.read(path.toString(), maxBytes);
        }

        @Override
        public String getName() {
            return archive.getArchive().getName();
        }
    }
}
