package betamoon.worldgen.spatial;

/**
 * Compiled finite scalar graph. Dependency bits X/Y/Z are 1/2/4; seed and
 * anchor are 8/16.
 */
public strictfp final class ScalarField {
    public final double min;
    public final double max;
    public final int dependencies;
    public final int nodeCount;
    public final long sampleWork;
    private final Evaluator evaluator;
    private String debugLabel;

    ScalarField(double min, double max, int dependencies, int nodeCount, Evaluator evaluator) {
        this(min, max, dependencies, nodeCount, evaluator, nodeCount);
    }

    ScalarField(double min, double max, int dependencies, int nodeCount, Evaluator evaluator, long sampleWork) {
        this.sampleWork = sampleWork;
        if (!Double.isFinite(min) || !Double.isFinite(max) || min > max) {
            throw new IllegalArgumentException("Field range must be finite and ordered");
        }
        this.min = min;
        this.max = max;
        this.dependencies = dependencies;
        this.nodeCount = nodeCount;
        this.evaluator = evaluator;
    }

    public double sample(FieldContext context, double x, double y, double z) {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z) || Math.abs(x) > 1.0e12
                || Math.abs(y) > 1.0e12 || Math.abs(z) > 1.0e12) {
            throw new IllegalStateException("Field sampling domain exceeds +/-1e12");
        }
        double value = evaluator.sample(context, x, y, z);
        if (!Double.isFinite(value)) {
            throw new IllegalStateException("Non-finite field result at " + x + "," + y + "," + z);
        }
        double tolerance = Math.max(1, Math.max(Math.abs(min), Math.abs(max))) * 1.0e-12;
        if (value < min - tolerance || value > max + tolerance) {
            throw new IllegalStateException("Field escaped its compiled range at " + x + "," + y + "," + z);
        }
        if (context.trace != null && debugLabel != null) {
            context.trace.record(debugLabel, x, y, z, value);
        }
        return value;
    }

    void label(String label) {
        if (debugLabel == null) {
            debugLabel = label;
        }
    }

    public void sampleVolume(FieldContext context, double x, double y, double z, int sizeX, int sizeY, int sizeZ,
            double stepX, double stepY, double stepZ, double[] output) {
        if (sizeX < 1 || sizeY < 1 || sizeZ < 1 || sizeX > 32768 || sizeY > 32768 || sizeZ > 32768 || output == null
                || !Double.isFinite(stepX) || !Double.isFinite(stepY) || !Double.isFinite(stepZ)) {
            throw new IllegalArgumentException("Invalid field volume dimensions, steps or output buffer");
        }
        long count = (long) sizeX * sizeY * sizeZ;
        long samples = (dependencies & 2) == 0 ? (long) sizeX * sizeZ : count;
        if (count > 32768 || output.length < count || samples * sampleWork > 16000000) {
            throw new IllegalArgumentException("Field volume exceeds its sampling budget");
        }
        int cursor = 0;
        for (int ix = 0; ix < sizeX; ix++) {
            for (int iz = 0; iz < sizeZ; iz++) {
                double column = (dependencies & 2) == 0 ? sample(context, x + ix * stepX, y, z + iz * stepZ) : 0;
                for (int iy = 0; iy < sizeY; iy++) {
                    output[cursor++] = (dependencies & 2) == 0
                            ? column
                            : sample(context, x + ix * stepX, y + iy * stepY, z + iz * stepZ);
                }
            }
        }
    }

    interface Evaluator {
        double sample(FieldContext context, double x, double y, double z);
    }
}
