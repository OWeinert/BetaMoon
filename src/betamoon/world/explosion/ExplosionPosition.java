package betamoon.world.explosion;

/** Immutable block coordinate captured by an explosion calculation. */
public final class ExplosionPosition implements Comparable<ExplosionPosition> {
    public final int x;
    public final int y;
    public final int z;

    public ExplosionPosition(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    @Override
    public int compareTo(ExplosionPosition other) {
        int xOrder = Integer.compare(x, other.x);
        if (xOrder != 0) {
            return xOrder;
        }
        int yOrder = Integer.compare(y, other.y);
        return yOrder != 0 ? yOrder : Integer.compare(z, other.z);
    }

    @Override
    public boolean equals(Object value) {
        if (!(value instanceof ExplosionPosition)) {
            return false;
        }
        ExplosionPosition other = (ExplosionPosition) value;
        return x == other.x && y == other.y && z == other.z;
    }

    @Override
    public int hashCode() {
        return x * 8976890 + y * 981131 + z;
    }
}
