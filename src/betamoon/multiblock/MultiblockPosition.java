package betamoon.multiblock;

/**
 * Value key shared by footprint membership, controllers and exclusive claims.
 */
public final class MultiblockPosition implements Comparable<MultiblockPosition> {
    public final int x;
    public final int y;
    public final int z;

    public MultiblockPosition(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public long chunk() {
        return chunk(x >> 4, z >> 4);
    }

    public static long chunk(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }

    public int compareTo(MultiblockPosition other) {
        int order = Integer.compare(x, other.x);
        if (order == 0) {
            order = Integer.compare(y, other.y);
        }
        return order == 0 ? Integer.compare(z, other.z) : order;
    }

    public boolean equals(Object other) {
        if (!(other instanceof MultiblockPosition)) {
            return false;
        }
        MultiblockPosition position = (MultiblockPosition) other;
        return x == position.x && y == position.y && z == position.z;
    }

    public int hashCode() {
        return (x * 31 + y) * 31 + z;
    }

    public String toString() {
        return x + ":" + y + ":" + z;
    }
}
