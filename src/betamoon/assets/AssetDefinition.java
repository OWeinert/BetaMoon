package betamoon.assets;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Immutable declaration metadata. Creating it never opens, decodes, or uploads
 * an asset.
 */
public final class AssetDefinition {
    private static final Pattern EXTENSION = Pattern.compile("[a-z0-9]+(?:\\.[a-z0-9]+)*");

    private final AssetId id;
    private final AssetPath fallbackPath;
    private final String extension;
    private final boolean builtin;
    private final boolean pathDerived;
    private final String defaultSource;

    /**
     * Extension includes compound suffixes such as animation.json, without a
     * leading dot.
     */
    public AssetDefinition(AssetId id, AssetPath fallbackPath, String extension) {
        this(id, fallbackPath, extension, false, false, null);
    }

    private AssetDefinition(AssetId id, AssetPath fallbackPath, String extension, boolean builtin,
            boolean pathDerived, String defaultSource) {
        this.id = Objects.requireNonNull(id, "Asset identity");
        this.fallbackPath = Objects.requireNonNull(fallbackPath, "Asset fallback path");
        this.extension = Objects.requireNonNull(extension, "Asset extension");
        this.builtin = builtin;
        this.pathDerived = pathDerived;
        this.defaultSource = defaultSource;
        if (!EXTENSION.matcher(extension).matches()) {
            throw new IllegalArgumentException("Invalid lowercase asset extension: " + extension);
        }
        if (!fallbackPath.toString().toLowerCase(Locale.ROOT).endsWith("." + extension)) {
            throw new IllegalArgumentException("Asset fallback path must end with ." + extension + ": " + fallbackPath);
        }
    }

    static AssetDefinition builtin(AssetKind kind, AssetKey key, AssetPath resource, String extension) {
        return new AssetDefinition(new AssetId(kind, key), resource, extension, true, false, null);
    }

    /** Creates a declaration whose fallback path was resolved from its key. */
    public static AssetDefinition derived(AssetId id, AssetPath fallbackPath, String extension) {
        return new AssetDefinition(id, fallbackPath, extension, false, true, null);
    }

    /** Associates a script asset with the Lua source that owns its default file. */
    public AssetDefinition fromSource(String source) {
        if (builtin) {
            throw new IllegalStateException("Built-in assets do not have a Lua source");
        }
        return new AssetDefinition(id, fallbackPath, extension, false, pathDerived,
                Objects.requireNonNull(source, "Asset default source"));
    }

    public boolean isBuiltin() {
        return builtin;
    }

    public boolean isPathDerived() {
        return pathDerived;
    }

    public AssetId getId() {
        return id;
    }

    public AssetPath getFallbackPath() {
        return fallbackPath;
    }

    public String getExtension() {
        return extension;
    }

    public String getDefaultSource() {
        return defaultSource;
    }

    /** Script assets mirror their default path; built-ins retain their catalog key. */
    public AssetPath getOverridePath() {
        if (!builtin) {
            return fallbackPath.getDirectOverridePath();
        }
        AssetKey key = id.getKey();
        return AssetPath.parse(AssetPath.PACK_ROOT + key.getNamespace() + "/" + id.getKind().getDirectory() + "/"
                + key.getPath() + "." + extension);
    }
}
