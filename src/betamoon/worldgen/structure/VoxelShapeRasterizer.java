package betamoon.worldgen.structure;

/**
 * Shared integer containment rules for declarative and terrain voxel shapes.
 */
final class VoxelShapeRasterizer {
    private VoxelShapeRasterizer() {
    }

    static boolean ellipse(int first, int second, int radiusFirst, int radiusSecond) {
        long firstSquared = (long) first * first;
        long secondSquared = (long) second * second;
        long radiusFirstSquared = (long) radiusFirst * radiusFirst;
        long radiusSecondSquared = (long) radiusSecond * radiusSecond;
        return firstSquared * radiusSecondSquared + secondSquared * radiusFirstSquared <= radiusFirstSquared
                * radiusSecondSquared;
    }

    static boolean ellipsoid(int x, int y, int z, int radiusX, int radiusY, int radiusZ) {
        long rx2 = (long) radiusX * radiusX;
        long ry2 = (long) radiusY * radiusY;
        long rz2 = (long) radiusZ * radiusZ;
        return (long) x * x * ry2 * rz2 + (long) y * y * rx2 * rz2 + (long) z * z * rx2 * ry2 <= rx2 * ry2 * rz2;
    }
}
