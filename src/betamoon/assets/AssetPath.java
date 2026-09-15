package betamoon.assets;

import java.util.Objects;

/**
 * Portable relative resource path. Validation does not perform filesystem
 * access.
 */
public final class AssetPath {
    private final String value;

    private AssetPath(String value) {
        this.value = value;
    }

    public static AssetPath parse(String path) {
        Objects.requireNonNull(path, "Asset path");
        String normalized = path.replace('\\', '/');
        for (String segment : normalized.split("/", -1)) {
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..") || !segment.equals(segment.trim())
                    || segment.endsWith(".")) {
                throw new IllegalArgumentException("Asset path must contain relative file segments: " + path);
            }
            for (int i = 0; i < segment.length(); i++) {
                char character = segment.charAt(i);
                if (Character.isISOControl(character) || ":*?\"<>|".indexOf(character) >= 0) {
                    throw new IllegalArgumentException("Invalid character in asset path: " + path);
                }
            }
        }
        return new AssetPath(normalized);
    }

    /**
     * Direct paths retain their directory structure and case beneath the pack root.
     */
    public AssetPath getDirectOverridePath() {
        return parse("betamoon/" + value);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof AssetPath && value.equals(((AssetPath) other).value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return value;
    }
}
