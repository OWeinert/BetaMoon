package betamoon.assets.io;

import betamoon.assets.AssetPath;
import java.io.IOException;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Selects a valid pack override, then a script default. Does not own decoded
 * resource lifetime.
 */
public final class AssetResolver {
    private final AssetProvider defaults;
    private final AssetProvider pack;
    private final Consumer<String> diagnostics;
    private final String defaultKind;

    public AssetResolver(AssetProvider defaults, AssetProvider pack, Consumer<String> diagnostics) {
        this(defaults, pack, diagnostics, "script");
    }

    private AssetResolver(AssetProvider defaults, AssetProvider pack, Consumer<String> diagnostics,
            String defaultKind) {
        this.defaults = Objects.requireNonNull(defaults, "Script asset provider");
        this.pack = pack;
        this.diagnostics = Objects.requireNonNull(diagnostics, "Asset diagnostics");
        this.defaultKind = defaultKind;
    }

    public AssetResolver withDefaults(AssetProvider provider, String sourceKind) {
        return new AssetResolver(provider, pack, diagnostics, sourceKind);
    }

    /** Selects the package-local default root associated with a Lua source. */
    public AssetResolver forSource(String source) throws IOException {
        return new AssetResolver(defaults.forSource(source), pack, diagnostics, defaultKind);
    }

    public boolean defaultExists(AssetPath path) throws IOException {
        return defaults.exists(path);
    }

    public <T> ResolvedAsset<T> resolve(String identity, AssetPath fallback, AssetPath override, int byteLimit,
            AssetDecoder<T> decoder) throws IOException {
        return resolve(identity, fallback, override, byteLimit, decoder, decoder);
    }

    public <T> ResolvedAsset<T> resolve(String identity, AssetPath fallback, AssetPath override, int byteLimit,
            AssetDecoder<T> packDecoder, AssetDecoder<T> defaultDecoder) throws IOException {
        if (pack != null) {
            try {
                ResolvedAsset<T> candidate = read(pack, "pack", override, byteLimit, packDecoder);
                if (candidate != null) {
                    return candidate;
                }
            } catch (IOException | IllegalArgumentException error) {
                diagnostics.accept("Asset " + identity + ": pack '" + pack.getName() + "', entry '" + override + "': "
                        + error.getMessage() + "; using " + defaultKind + " default");
            }
        }
        return resolveDefault(identity, fallback, byteLimit, defaultDecoder);
    }

    public <T> ResolvedAsset<T> resolveDefault(String identity, AssetPath fallback, int byteLimit,
            AssetDecoder<T> decoder) throws IOException {
        ResolvedAsset<T> result = read(defaults, defaultKind, fallback, byteLimit, decoder);
        if (result == null) {
            throw new IOException("Asset " + identity + ": default not found: " + fallback);
        }
        return result;
    }

    private <T> ResolvedAsset<T> read(AssetProvider provider, String kind, AssetPath path, int limit,
            AssetDecoder<T> decoder) throws IOException {
        byte[] bytes = provider.read(path, limit);
        if (bytes == null) {
            return null;
        }
        T value = decoder.decode(bytes);
        if (value == null) {
            throw new IOException("Decoder returned no content for " + path);
        }
        return new ResolvedAsset<>(value, kind, provider.getName(), path);
    }
}
