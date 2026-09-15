package betamoon.assets;

import java.util.Objects;

/**
 * Stable typed identity; never a GPU handle, network integer, or registry
 * position.
 */
public final class AssetId {
    private final AssetKind kind;
    private final AssetKey key;

    public AssetId(AssetKind kind, AssetKey key) {
        this.kind = Objects.requireNonNull(kind, "Asset kind");
        this.key = Objects.requireNonNull(key, "Asset key");
    }

    public AssetKind getKind() {
        return kind;
    }

    public AssetKey getKey() {
        return key;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof AssetId)) {
            return false;
        }
        AssetId id = (AssetId) other;
        return kind == id.kind && key.equals(id.key);
    }

    @Override
    public int hashCode() {
        return 31 * kind.hashCode() + key.hashCode();
    }

    @Override
    public String toString() {
        return kind.getDirectory() + "/" + key;
    }
}
