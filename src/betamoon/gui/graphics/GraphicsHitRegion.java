package betamoon.gui.graphics;

/**
 * Immutable local geometry. Polygon regions accept simple polygons and include
 * their boundary.
 */
@FunctionalInterface
public interface GraphicsHitRegion {
    boolean contains(double x, double y);

    static GraphicsHitRegion rectangle(double x, double y, double width, double height) {
        coordinate(x);
        coordinate(y);
        dimension(width);
        dimension(height);
        return (px, py) -> px >= x && py >= y && px < x + width && py < y + height;
    }

    static GraphicsHitRegion circle(double x, double y, double radius) {
        coordinate(x);
        coordinate(y);
        dimension(radius);
        return (px, py) -> radius > 0 && Math.hypot(px - x, py - y) <= radius;
    }

    static GraphicsHitRegion polygon(double[] input) {
        if (input == null || input.length < 6 || input.length > 512 || input.length % 2 != 0) {
            throw new IllegalArgumentException("Hit polygons require 3..256 vertices");
        }
        double[] points = input.clone();
        double area = 0;
        int count = points.length / 2;
        for (double point : points) {
            coordinate(point);
        }
        for (int a = 0; a < count; a++) {
            int b = (a + 1) % count;
            if (points[a * 2] == points[b * 2] && points[a * 2 + 1] == points[b * 2 + 1]) {
                throw new IllegalArgumentException("Hit polygon contains a zero-length edge");
            }
            area += points[a * 2] * points[b * 2 + 1] - points[b * 2] * points[a * 2 + 1];
            for (int c = a + 1; c < count; c++) {
                int d = (c + 1) % count;
                if (a != d && b != c && intersects(points, a, b, c, d)) {
                    throw new IllegalArgumentException("Hit polygon must be simple");
                }
            }
        }
        if (Math.abs(area) < 1e-8) {
            throw new IllegalArgumentException("Hit polygon has no area");
        }
        return (x, y) -> {
            boolean inside = false;
            for (int a = 0, b = count - 1; a < count; b = a++) {
                double ax = points[a * 2];
                double ay = points[a * 2 + 1];
                double bx = points[b * 2];
                double by = points[b * 2 + 1];
                if (onEdge(ax, ay, bx, by, x, y)) {
                    return true;
                }
                if ((ay > y) != (by > y) && x < (bx - ax) * (y - ay) / (by - ay) + ax) {
                    inside = !inside;
                }
            }
            return inside;
        };
    }

    default GraphicsHitRegion transformed(GraphicsAffine transform) {
        return (x, y) -> contains(transform.localX(x, y), transform.localY(x, y));
    }

    static boolean intersects(double[] points, int a, int b, int c, int d) {
        double ax = points[a * 2];
        double ay = points[a * 2 + 1];
        double bx = points[b * 2];
        double by = points[b * 2 + 1];
        double cx = points[c * 2];
        double cy = points[c * 2 + 1];
        double dx = points[d * 2];
        double dy = points[d * 2 + 1];
        double first = cross(ax, ay, bx, by, cx, cy);
        double second = cross(ax, ay, bx, by, dx, dy);
        double third = cross(cx, cy, dx, dy, ax, ay);
        double fourth = cross(cx, cy, dx, dy, bx, by);
        return first * second < 0 && third * fourth < 0 || onEdge(ax, ay, bx, by, cx, cy)
                || onEdge(ax, ay, bx, by, dx, dy) || onEdge(cx, cy, dx, dy, ax, ay) || onEdge(cx, cy, dx, dy, bx, by);
    }

    static boolean onEdge(double ax, double ay, double bx, double by, double x, double y) {
        return Math.abs(cross(ax, ay, bx, by, x, y)) <= 1e-8 && x >= Math.min(ax, bx) && x <= Math.max(ax, bx)
                && y >= Math.min(ay, by) && y <= Math.max(ay, by);
    }

    static double cross(double ax, double ay, double bx, double by, double x, double y) {
        return (bx - ax) * (y - ay) - (by - ay) * (x - ax);
    }

    static void coordinate(double value) {
        if (!Double.isFinite(value) || Math.abs(value) > 1e6) {
            throw new IllegalArgumentException("Hit geometry requires finite coordinates within +/-1000000");
        }
    }

    static void dimension(double value) {
        coordinate(value);
        if (value < 0) {
            throw new IllegalArgumentException("Hit geometry dimensions cannot be negative");
        }
    }
}
