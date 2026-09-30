package betamoon.worldgen.structure;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Immutable two-dimensional integer mask with deterministic cell ordering and
 * reusable set operations.
 */
final class TerrainFootprint {
    private static final int[][] NEIGHBORS = new int[][]{{-1, 0}, {1, 0}, {0, -1}, {0, 1}};

    private final Set<Cell> cells;
    private final List<Cell> ordered;
    final int minX;
    final int maxX;
    final int minZ;
    final int maxZ;

    TerrainFootprint(Collection<Cell> source) {
        LinkedHashSet<Cell> unique = new LinkedHashSet<Cell>(source);
        List<Cell> sorted = new ArrayList<Cell>(unique);
        Collections.sort(sorted, new Comparator<Cell>() {
            @Override
            public int compare(Cell left, Cell right) {
                int z = Integer.compare(left.z, right.z);
                return z == 0 ? Integer.compare(left.x, right.x) : z;
            }
        });
        cells = Collections.unmodifiableSet(new LinkedHashSet<Cell>(sorted));
        ordered = Collections.unmodifiableList(sorted);
        int minimumX = Integer.MAX_VALUE;
        int maximumX = Integer.MIN_VALUE;
        int minimumZ = Integer.MAX_VALUE;
        int maximumZ = Integer.MIN_VALUE;
        for (Cell cell : sorted) {
            minimumX = Math.min(minimumX, cell.x);
            maximumX = Math.max(maximumX, cell.x);
            minimumZ = Math.min(minimumZ, cell.z);
            maximumZ = Math.max(maximumZ, cell.z);
        }
        minX = sorted.isEmpty() ? 0 : minimumX;
        maxX = sorted.isEmpty() ? -1 : maximumX;
        minZ = sorted.isEmpty() ? 0 : minimumZ;
        maxZ = sorted.isEmpty() ? -1 : maximumZ;
    }

    static TerrainFootprint empty() {
        return new TerrainFootprint(Collections.<Cell>emptyList());
    }

    boolean isEmpty() {
        return ordered.isEmpty();
    }

    int size() {
        return ordered.size();
    }

    List<Cell> cells() {
        return ordered;
    }

    boolean contains(int x, int z) {
        return cells.contains(new Cell(x, z));
    }

    TerrainFootprint union(TerrainFootprint other) {
        Set<Cell> result = new LinkedHashSet<Cell>(cells);
        result.addAll(other.cells);
        return new TerrainFootprint(result);
    }

    TerrainFootprint intersection(TerrainFootprint other) {
        Set<Cell> result = new LinkedHashSet<Cell>(cells);
        result.retainAll(other.cells);
        return new TerrainFootprint(result);
    }

    TerrainFootprint difference(TerrainFootprint other) {
        Set<Cell> result = new LinkedHashSet<Cell>(cells);
        result.removeAll(other.cells);
        return new TerrainFootprint(result);
    }

    TerrainFootprint bounds() {
        if (isEmpty()) {
            return this;
        }
        Set<Cell> result = new LinkedHashSet<Cell>();
        for (int z = minZ; z <= maxZ; z++) {
            for (int x = minX; x <= maxX; x++) {
                result.add(new Cell(x, z));
            }
        }
        return new TerrainFootprint(result);
    }

    TerrainFootprint roundedBounds(int radius) {
        if (isEmpty() || radius <= 0) {
            return bounds();
        }
        int effective = Math.min(radius, Math.min((maxX - minX + 1) / 2, (maxZ - minZ + 1) / 2));
        Set<Cell> result = new LinkedHashSet<Cell>();
        for (int z = minZ; z <= maxZ; z++) {
            for (int x = minX; x <= maxX; x++) {
                int dx = Math.max(0, Math.max(minX + effective - x, x - (maxX - effective)));
                int dz = Math.max(0, Math.max(minZ + effective - z, z - (maxZ - effective)));
                if (dx * dx + dz * dz <= effective * effective) {
                    result.add(new Cell(x, z));
                }
            }
        }
        return new TerrainFootprint(result);
    }

    TerrainFootprint convexHull() {
        if (ordered.size() < 3) {
            return ordered.size() == 2 ? rasterizedSegment(ordered.get(0), ordered.get(1)) : this;
        }
        List<Cell> points = new ArrayList<Cell>(ordered);
        Collections.sort(points, new Comparator<Cell>() {
            @Override
            public int compare(Cell left, Cell right) {
                int x = Integer.compare(left.x, right.x);
                return x == 0 ? Integer.compare(left.z, right.z) : x;
            }
        });
        List<Cell> hull = new ArrayList<Cell>();
        for (Cell point : points) {
            while (hull.size() >= 2 && cross(hull.get(hull.size() - 2), hull.get(hull.size() - 1), point) <= 0) {
                hull.remove(hull.size() - 1);
            }
            hull.add(point);
        }
        int lower = hull.size();
        for (int index = points.size() - 2; index >= 0; index--) {
            Cell point = points.get(index);
            while (hull.size() > lower && cross(hull.get(hull.size() - 2), hull.get(hull.size() - 1), point) <= 0) {
                hull.remove(hull.size() - 1);
            }
            hull.add(point);
        }
        if (hull.size() > 1) {
            hull.remove(hull.size() - 1);
        }
        if (hull.size() == 2) {
            return rasterizedSegment(hull.get(0), hull.get(1));
        }
        Set<Cell> result = new LinkedHashSet<Cell>();
        for (int z = minZ; z <= maxZ; z++) {
            for (int x = minX; x <= maxX; x++) {
                if (insideHull(x, z, hull)) {
                    result.add(new Cell(x, z));
                }
            }
        }
        return new TerrainFootprint(result);
    }

    private static TerrainFootprint rasterizedSegment(Cell start, Cell end) {
        Set<Cell> result = new LinkedHashSet<Cell>();
        int x = start.x;
        int z = start.z;
        int dx = Math.abs(end.x - start.x);
        int dz = Math.abs(end.z - start.z);
        int sx = start.x < end.x ? 1 : -1;
        int sz = start.z < end.z ? 1 : -1;
        int error = dx - dz;
        while (true) {
            result.add(new Cell(x, z));
            if (x == end.x && z == end.z) {
                break;
            }
            int doubled = error * 2;
            if (doubled > -dz) {
                error -= dz;
                x += sx;
            }
            if (doubled < dx) {
                error += dx;
                z += sz;
            }
        }
        return new TerrainFootprint(result);
    }

    TerrainFootprint dilate(int radius) {
        if (radius <= 0 || isEmpty()) {
            return this;
        }
        Set<Cell> result = new LinkedHashSet<Cell>();
        for (Cell cell : ordered) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dx = -radius; dx <= radius; dx++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) <= radius) {
                        result.add(new Cell(cell.x + dx, cell.z + dz));
                    }
                }
            }
        }
        return new TerrainFootprint(result);
    }

    TerrainFootprint erode(int radius) {
        TerrainFootprint result = this;
        for (int pass = 0; pass < radius && !result.isEmpty(); pass++) {
            Set<Cell> retained = new LinkedHashSet<Cell>();
            for (Cell cell : result.ordered) {
                boolean interior = true;
                for (int[] neighbor : NEIGHBORS) {
                    interior &= result.contains(cell.x + neighbor[0], cell.z + neighbor[1]);
                }
                if (interior) {
                    retained.add(cell);
                }
            }
            result = new TerrainFootprint(retained);
        }
        return result;
    }

    int distanceFrom(TerrainFootprint core, Cell cell, int maximum) {
        if (core.contains(cell.x, cell.z)) {
            return 0;
        }
        for (int radius = 1; radius <= maximum; radius++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dx = -radius; dx <= radius; dx++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) == radius && core.contains(cell.x + dx, cell.z + dz)) {
                        return radius;
                    }
                }
            }
        }
        return maximum + 1;
    }

    private static long cross(Cell origin, Cell left, Cell right) {
        return (long) (left.x - origin.x) * (right.z - origin.z) - (long) (left.z - origin.z) * (right.x - origin.x);
    }

    private static boolean insideHull(int x, int z, List<Cell> hull) {
        if (hull.size() < 3) {
            return true;
        }
        boolean positive = false;
        boolean negative = false;
        Cell point = new Cell(x, z);
        for (int index = 0; index < hull.size(); index++) {
            long cross = cross(hull.get(index), hull.get((index + 1) % hull.size()), point);
            positive |= cross > 0;
            negative |= cross < 0;
            if (positive && negative) {
                return false;
            }
        }
        return true;
    }

    static final class Cell {
        final int x;
        final int z;

        Cell(int x, int z) {
            this.x = x;
            this.z = z;
        }

        @Override
        public boolean equals(Object value) {
            return value instanceof Cell && x == ((Cell) value).x && z == ((Cell) value).z;
        }

        @Override
        public int hashCode() {
            return x * 31 + z;
        }

        @Override
        public String toString() {
            return x + "," + z;
        }
    }
}
