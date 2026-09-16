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
    public byte[] read(AssetPath path, int maxBytes) throws IOException {
        if (!path.toString().startsWith("builtin/minecraft/models/block/") || !path.toString().endsWith(".json")) {
            throw new IOException("Unsupported built-in model resource: " + path);
        }
        InputStream resource = BuiltinAssetProvider.class.getResourceAsStream("/resources/" + path);
        if (resource == null) {
            resource = BuiltinAssetProvider.class.getResourceAsStream("/" + path);
        }
        try (InputStream input = resource) {
            return input == null ? null : AssetStreams.read(input, maxBytes);
        }
    }

    @Override
    public String getName() {
        return "BetaMoon built-ins";
    }
}
