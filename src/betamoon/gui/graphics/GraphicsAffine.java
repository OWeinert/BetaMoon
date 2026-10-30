package betamoon.gui.graphics;

/** Mesh-local affine transform. Scene layout/clipping remains axis-aligned. */
public final class GraphicsAffine {
    public static final GraphicsAffine IDENTITY = new GraphicsAffine(1, 0, 0, 1, 0, 0);
    public final double m00;
    public final double m01;
    public final double m10;
    public final double m11;
    public final double x;
    public final double y;
    private final double determinant;

    public GraphicsAffine(double m00, double m01, double m10, double m11, double x, double y) {
        for (double value : new double[]{m00, m01, m10, m11, x, y}) {
            if (!Double.isFinite(value) || Math.abs(value) > 1e9) {
                throw new IllegalArgumentException("Prepared affine transform exceeds finite geometry bounds");
            }
        }
        double determinant = m00 * m11 - m01 * m10;
        if (!Double.isFinite(determinant) || Math.abs(determinant) < 1e-12) {
            throw new IllegalArgumentException("Prepared affine transforms must be invertible");
        }
        this.m00 = m00;
        this.m01 = m01;
        this.m10 = m10;
        this.m11 = m11;
        this.x = x;
        this.y = y;
        this.determinant = determinant;
    }

    public static GraphicsAffine transform(double x, double y, double scaleX, double scaleY, double degrees) {
        if (!Double.isFinite(degrees)) {
            throw new IllegalArgumentException("Rotation must be finite");
        }
        double radians = Math.toRadians(degrees % 360);
        double cosine = Math.cos(radians);
        double sine = Math.sin(radians);
        return new GraphicsAffine(cosine * scaleX, -sine * scaleY, sine * scaleX, cosine * scaleY, x, y);
    }

    public GraphicsAffine compose(GraphicsAffine child) {
        return new GraphicsAffine(m00 * child.m00 + m01 * child.m10, m00 * child.m01 + m01 * child.m11,
                m10 * child.m00 + m11 * child.m10, m10 * child.m01 + m11 * child.m11, mapX(child.x, child.y),
                mapY(child.x, child.y));
    }

    public double mapX(double x, double y) {
        return this.x + m00 * x + m01 * y;
    }

    public double mapY(double x, double y) {
        return this.y + m10 * x + m11 * y;
    }

    public double localX(double x, double y) {
        return (m11 * (x - this.x) - m01 * (y - this.y)) / determinant;
    }

    public double localY(double x, double y) {
        return (-m10 * (x - this.x) + m00 * (y - this.y)) / determinant;
    }

    public boolean axisAligned() {
        return Math.abs(m01) < 1e-12 && Math.abs(m10) < 1e-12;
    }
}
