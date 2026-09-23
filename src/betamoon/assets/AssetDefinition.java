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

    /**
     * Extension includes compound suffixes such as animation.json, without a
     * leading dot.
     */
    public AssetDefinition(AssetId id, AssetPath fallbackPath, String extension) {
        this(id, fallbackPath, extension, false, false);
    }

    private AssetDefinition(AssetId id, AssetPath fallbackPath, String extension, boolean builtin,
            boolean pathDerived) {
        this.id = Objects.requireNonNull(id, "Asset identity");
        this.fallbackPath = Objects.requireNonNull(fallbackPath, "Asset fallback path");
        this.extension = Objects.requireNonNull(extension, "Asset extension");
        this.builtin = builtin;
        this.pathDerived = pathDerived;
        if (!EXTENSION.matcher(extension).matches()) {
            throw new IllegalArgumentException("Invalid lowercase asset extension: " + extension);
        }
        if (!fallbackPath.toString().toLowerCase(Locale.ROOT).endsWith("." + extension)) {
            throw new IllegalArgumentException("Asset fallback path must end with ." + extension + ": " + fallbackPath);
        }
    }

    static AssetDefinition builtinModel(AssetKey key, AssetPath resource) {
        return new AssetDefinition(new AssetId(AssetKind.MODEL, key), resource, "json", true, false);
    }

    /** Creates a declaration whose fallback path was resolved from its key. */
    public static AssetDefinition derived(AssetId id, AssetPath fallbackPath, String extension) {
        return new AssetDefinition(id, fallbackPath, extension, false, true);
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
