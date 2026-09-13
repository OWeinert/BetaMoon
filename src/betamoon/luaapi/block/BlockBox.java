package betamoon.luaapi.block;

import net.minecraft.src.AxisAlignedBB;

/**
 * Immutable local block bounds, independent of Minecraft's mutable bounding-box
 * pool.
 */
public final class BlockBox {
    public final double minX;
    public final double minY;
    public final double minZ;
    public final double maxX;
    public final double maxY;
    public final double maxZ;

    public BlockBox(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        validateAxis(minX, maxX);
        validateAxis(minY, maxY);
        validateAxis(minZ, maxZ);
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxY = maxY;
        this.maxZ = maxZ;
    }

    static BlockBox fromCoordinates(double[] coordinates) {
        return new BlockBox(coordinates[0], coordinates[1], coordinates[2], coordinates[3], coordinates[4],
                coordinates[5]);
    }

    public boolean isFullCube() {
        return minX == 0 && minY == 0 && minZ == 0 && maxX == 1 && maxY == 1 && maxZ == 1;
    }

    public AxisAlignedBB boundsAt(int x, int y, int z) {
        return AxisAlignedBB.getBoundingBoxFromPool(x + minX, y + minY, z + minZ, x + maxX, y + maxY, z + maxZ);
    }

    private static void validateAxis(double min, double max) {
        if (!(min >= 0 && max <= 1 && min < max)) {
            throw new IllegalArgumentException("Block bounds require 0 <= min < max <= 1");
        }
    }
}
