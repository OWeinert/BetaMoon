package betamoon.dimension;

import betamoon.worldgen.WorldGenKey;
import betamoon.worldgen.WorldGenKind;
import java.util.Objects;

/**
 * Canonical dimension identity; numeric provider IDs are save-local transport
 * details.
 */
public final class DimensionKey implements Comparable<DimensionKey> {
    public static final DimensionKey OVERWORLD = new DimensionKey("minecraft:overworld");
    public static final DimensionKey NETHER = new DimensionKey("minecraft:nether");
    private final String value;

    private DimensionKey(String value) {
        this.value = value;
    }

    public static DimensionKey parse(String value) {
        Objects.requireNonNull(value, "Dimension key");
        if (value.equals("minecraft:overworld") || value.equals("overworld")) {
            return OVERWORLD;
        }
        if (value.equals("minecraft:nether") || value.equals("nether") || value.equals("hell")) {
            return NETHER;
        }
        WorldGenKey key = WorldGenKey.parse(value, WorldGenKind.DIMENSION);
        if (key.getNamespace().equals("minecraft") || key.getNamespace().equals("betamoon")) {
            throw new IllegalArgumentException("Reserved dimension namespace: " + value);
        }
        return new DimensionKey(key.toString());
    }

    public boolean isNative() {
        return equals(OVERWORLD) || equals(NETHER);
    }

    public static DimensionKey nativeKey(int id) {
        if (id == 0) {
            return OVERWORLD;
        }
        if (id == -1) {
            return NETHER;
        }
        throw new IllegalArgumentException("Unknown native dimension ID: " + id);
    }

    public String storagePath() {
        if (isNative()) {
            throw new IllegalStateException("Native dimensions retain their existing storage paths");
        }
        return value.replace(':', '/');
    }

    @Override
    public String toString() {
        return value;
    }

    @Override
    public int compareTo(DimensionKey other) {
        return value.compareTo(other.value);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof DimensionKey && value.equals(((DimensionKey) other).value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }
}
