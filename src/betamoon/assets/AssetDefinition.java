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

    /**
     * Extension includes compound suffixes such as geo.json, without a leading dot.
     */
    public AssetDefinition(AssetId id, AssetPath fallbackPath, String extension) {
        this.id = Objects.requireNonNull(id, "Asset identity");
        this.fallbackPath = Objects.requireNonNull(fallbackPath, "Asset fallback path");
        this.extension = Objects.requireNonNull(extension, "Asset extension");
        if (!EXTENSION.matcher(extension).matches()) {
            throw new IllegalArgumentException("Invalid lowercase asset extension: " + extension);
        }
        if (!fallbackPath.toString().toLowerCase(Locale.ROOT).endsWith("." + extension)) {
            throw new IllegalArgumentException("Asset fallback path must end with ." + extension + ": " + fallbackPath);
        }
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

    /**
     * Registered override locations depend on identity, not the fallback file
     * location.
     */
    public AssetPath getOverridePath() {
        AssetKey key = id.getKey();
        return AssetPath.parse("betamoon/" + key.getNamespace() + "/" + id.getKind().getDirectory() + "/"
                + key.getPath() + "." + extension);
    }
}
