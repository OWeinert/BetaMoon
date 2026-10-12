package betamoon.worldgen;

import betamoon.assets.AssetKey;
import java.util.Objects;

/** Stable typed key for one world-generation definition. */
public final class WorldGenKey implements Comparable<WorldGenKey> {
    private final WorldGenKind kind;
    private final AssetKey key;

    private WorldGenKey(WorldGenKind kind, AssetKey key) {
        this.kind = kind;
        this.key = key;
    }

    public static WorldGenKey parse(String value, WorldGenKind expectedKind) {
        Objects.requireNonNull(expectedKind, "World-generation key kind");
        AssetKey parsed = AssetKey.parse(value);
        WorldGenKind suppliedKind = WorldGenKind.fromPath(parsed.getPath());
        if (suppliedKind != null && suppliedKind != expectedKind) {
            throw new IllegalArgumentException("Expected a " + expectedKind.getPath() + " key, got " + value);
        }
        if (suppliedKind == null) {
            parsed = AssetKey.parse(parsed.getNamespace() + ":" + expectedKind.getPath() + "/" + parsed.getPath());
        }
        return new WorldGenKey(expectedKind, parsed);
    }

    /** Parses a key accepted wherever a reusable feature reference is allowed. */
    public static WorldGenKey parseFeature(String value) {
        AssetKey parsed = AssetKey.parse(value);
        WorldGenKind supplied = WorldGenKind.fromPath(parsed.getPath());
        if (supplied == null) {
            return parse(value, WorldGenKind.FEATURE);
        }
        if (supplied != WorldGenKind.FEATURE && supplied != WorldGenKind.TREE
                && supplied != WorldGenKind.STRUCTURE) {
            throw new IllegalArgumentException("Expected a feature, tree, or structure key, got " + value);
        }
        return new WorldGenKey(supplied, parsed);
    }

    static WorldGenKey privateKey(WorldGenKind kind, String owner, int declarationIndex) {
        long ownerHash = SeedMixer.hash(owner == null ? "unknown" : owner);
        String value = "betamoon:" + kind.getPath() + "/private/" + Long.toHexString(ownerHash) + "/"
                + declarationIndex;
        return parse(value, kind);
    }

    public WorldGenKind getKind() {
        return kind;
    }

    public String getNamespace() {
        return key.getNamespace();
    }

    public String getPath() {
        return key.getPath();
    }

    @Override
    public int compareTo(WorldGenKey other) {
        return toString().compareTo(other.toString());
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof WorldGenKey)) {
            return false;
        }
        WorldGenKey typed = (WorldGenKey) other;
        return kind == typed.kind && key.equals(typed.key);
    }

    @Override
    public int hashCode() {
        return 31 * kind.hashCode() + key.hashCode();
    }

    @Override
    public String toString() {
        return key.toString();
    }
}
