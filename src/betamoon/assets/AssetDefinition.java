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
    private final TextureAnimation textureAnimation;
    private final NativeTextureSource nativeTexture;

    /**
     * Extension includes compound suffixes such as animation.json, without a
     * leading dot.
     */
    public AssetDefinition(AssetId id, AssetPath fallbackPath, String extension) {
        this(id, fallbackPath, extension, false, false, null, null, null);
    }

    private AssetDefinition(AssetId id, AssetPath fallbackPath, String extension, boolean builtin,
            boolean pathDerived, String defaultSource, TextureAnimation textureAnimation,
            NativeTextureSource nativeTexture) {
        this.id = Objects.requireNonNull(id, "Asset identity");
        this.fallbackPath = Objects.requireNonNull(fallbackPath, "Asset fallback path");
        this.extension = Objects.requireNonNull(extension, "Asset extension");
        this.builtin = builtin;
        this.pathDerived = pathDerived;
        this.defaultSource = defaultSource;
        this.textureAnimation = textureAnimation;
        this.nativeTexture = nativeTexture;
        if (!EXTENSION.matcher(extension).matches()) {
            throw new IllegalArgumentException("Invalid lowercase asset extension: " + extension);
        }
        if (!fallbackPath.toString().toLowerCase(Locale.ROOT).endsWith("." + extension)) {
            throw new IllegalArgumentException("Asset fallback path must end with ." + extension + ": " + fallbackPath);
        }
    }

    static AssetDefinition builtin(AssetKind kind, AssetKey key, AssetPath resource, String extension) {
        return new AssetDefinition(new AssetId(kind, key), resource, extension, true, false, null, null, null);
    }

    static AssetDefinition nativeTexture(AssetKey key, NativeTextureSource source) {
        return new AssetDefinition(new AssetId(AssetKind.TEXTURE, key), source.getPath(), "png", true, false,
                null, null, source);
    }

    public NativeTextureSource getNativeTexture() {
        return nativeTexture;
    }

    /** Creates a declaration whose fallback path was resolved from its key. */
    public static AssetDefinition derived(AssetId id, AssetPath fallbackPath, String extension) {
        return new AssetDefinition(id, fallbackPath, extension, false, true, null, null, null);
    }

    public AssetDefinition withTextureAnimation(TextureAnimation animation) {
        if (id.getKind() != AssetKind.TEXTURE) {
            throw new IllegalArgumentException("Only texture assets accept animation metadata");
        }
        if (nativeTexture != null) {
            throw new IllegalArgumentException("Native textures use Minecraft's animation playback");
        }
        return new AssetDefinition(id, fallbackPath, extension, builtin, pathDerived, defaultSource, animation, null);
    }

    public TextureAnimation getTextureAnimation() {
        return textureAnimation;
    }

    /** Associates a script asset with the Lua source that owns its default file. */
    public AssetDefinition fromSource(String source) {
        if (builtin) {
            throw new IllegalStateException("Built-in assets do not have a Lua source");
        }
        return new AssetDefinition(id, fallbackPath, extension, false, pathDerived,
                Objects.requireNonNull(source, "Asset default source"), textureAnimation, null);
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

    /** Native images retain vanilla paths; BetaMoon assets use the bm_assets pack root. */
    public AssetPath getOverridePath() {
        if (nativeTexture != null) {
            return nativeTexture.getPath();
        }
        if (!builtin) {
            return fallbackPath.getDirectOverridePath();
        }
        AssetKey key = id.getKey();
        return AssetPath.parse(AssetPath.PACK_ROOT + key.getNamespace() + "/" + id.getKind().getDirectory() + "/"
                + key.getPath() + "." + extension);
    }
}
