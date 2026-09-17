package betamoon.assets.io;

import betamoon.assets.AssetPath;
import java.io.IOException;
import java.io.InputStream;

/** Reads only packaged model data through validated, relative asset paths. */
public final class BuiltinAssetProvider implements AssetProvider {
    public static final BuiltinAssetProvider INSTANCE = new BuiltinAssetProvider();

    private BuiltinAssetProvider() {
    }

    @Override
    public boolean exists(AssetPath path) throws IOException {
        try (InputStream input = resource(path)) {
            return input != null;
        }
    }

    @Override
    public byte[] read(AssetPath path, int maxBytes) throws IOException {
        try (InputStream input = resource(path)) {
            return input == null ? null : AssetStreams.read(input, maxBytes);
        }
    }

    private InputStream resource(AssetPath path) throws IOException {
        if (!path.toString().startsWith("builtin/minecraft/models/block/") || !path.toString().endsWith(".json")) {
            throw new IOException("Unsupported built-in model resource: " + path);
        }
        InputStream resource = BuiltinAssetProvider.class.getResourceAsStream("/resources/" + path);
        if (resource == null) {
            resource = BuiltinAssetProvider.class.getResourceAsStream("/" + path);
        }
        return resource;
    }

    @Override
    public String getName() {
        return "BetaMoon built-ins";
    }
}
