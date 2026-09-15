package betamoon.assets;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Exact, lowercase namespace:path identity, independent of a script's file
 * location.
 */
public final class AssetKey {
    private static final Pattern NAMESPACE = Pattern.compile("[a-z0-9_-]+(?:\\.[a-z0-9_-]+)*");
    private static final Pattern SEGMENT = Pattern.compile("[a-z0-9_.-]+");

    private final String namespace;
    private final String path;

    private AssetKey(String namespace, String path) {
        this.namespace = namespace;
        this.path = path;
    }

    public static AssetKey parse(String value) {
        Objects.requireNonNull(value, "Asset key");
        int separator = value.indexOf(':');
        if (separator <= 0 || separator != value.lastIndexOf(':')) {
            throw new IllegalArgumentException("Asset key must be namespace:path: " + value);
        }
        String namespace = value.substring(0, separator);
        String path = value.substring(separator + 1);
        if (!NAMESPACE.matcher(namespace).matches()) {
            throw new IllegalArgumentException("Invalid lowercase asset namespace: " + value);
        }
        for (String segment : path.split("/", -1)) {
            if (!SEGMENT.matcher(segment).matches() || segment.equals(".") || segment.equals("..")
                    || segment.endsWith(".")) {
                throw new IllegalArgumentException("Invalid lowercase asset path: " + value);
            }
        }
        return new AssetKey(namespace, path);
    }

    public String getNamespace() {
        return namespace;
    }

    public String getPath() {
        return path;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof AssetKey)) {
            return false;
        }
        AssetKey key = (AssetKey) other;
        return namespace.equals(key.namespace) && path.equals(key.path);
    }

    @Override
    public int hashCode() {
        return 31 * namespace.hashCode() + path.hashCode();
    }

    @Override
    public String toString() {
        return namespace + ":" + path;
    }
}
