package betamoon.worldgen.spatial;

import betamoon.worldgen.GenerationMetrics;
import java.util.function.DoubleUnaryOperator;

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
    private final double minimumSample;
    private final double maximumSample;
    ColumnFactory columnFactory;
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
        double tolerance = Math.max(1, Math.max(Math.abs(min), Math.abs(max))) * 1.0e-12;
        minimumSample = min - tolerance;
        maximumSample = max + tolerance;
    }

    public double sample(FieldContext context, double x, double y, double z) {
        GenerationMetrics.record(GenerationMetrics.Counter.FIELD_SAMPLES, 1);
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z) || Math.abs(x) > 1.0e12
                || Math.abs(y) > 1.0e12 || Math.abs(z) > 1.0e12) {
            throw new IllegalStateException("Field sampling domain exceeds +/-1e12");
        }
        double value = evaluator.sample(context, x, y, z);
        if (!Double.isFinite(value)) {
            throw new IllegalStateException("Non-finite field result at " + x + "," + y + "," + z);
        }
        if (value < minimumSample || value > maximumSample) {
            throw new IllegalStateException("Field escaped its compiled range at " + x + "," + y + "," + z);
        }
        if (context.trace != null && debugLabel != null) {
            context.trace.record(debugLabel, x, y, z, value);
        }
        return value;
    }

    ScalarField withColumnFactory(ColumnFactory factory) {
        columnFactory = factory;
        return this;
    }

    DoubleUnaryOperator prepareColumn(FieldContext context, double x, double z, double initialY) {
        if (!Double.isFinite(x) || !Double.isFinite(z) || Math.abs(x) > 1.0e12 || Math.abs(z) > 1.0e12) {
            throw new IllegalStateException("Field sampling domain exceeds +/-1e12");
        }
        DoubleUnaryOperator prepared = columnFactory.prepare(context, x, z, initialY);
        return y -> {
            if (!Double.isFinite(y) || Math.abs(y) > 1.0e12) {
                throw new IllegalStateException("Field sampling domain exceeds +/-1e12");
            }
            return prepared.applyAsDouble(y);
        };
    }

    public void sampleColumn(FieldContext context, double x, double z, int minY, int maxY, double[] output) {
        if (minY < 0 || maxY > 127 || minY > maxY || output == null || output.length <= maxY) {
            throw new IllegalArgumentException("Invalid field column range or output buffer");
        }
        if (columnFactory == null || context.trace != null) {
            for (int y = minY; y <= maxY; y++) {
                output[y] = sample(context, x, y, z);
            }
            return;
        }
        DoubleUnaryOperator prepared = prepareColumn(context, x, z, minY);
        GenerationMetrics.record(GenerationMetrics.Counter.PREPARED_COLUMN_SAMPLES, maxY - minY + 1);
        for (int y = minY; y <= maxY; y++) {
            double value = prepared.applyAsDouble(y);
            if (!Double.isFinite(value) || value < minimumSample || value > maximumSample) {
                throw new IllegalStateException("Field escaped its compiled range at " + x + "," + y + "," + z);
            }
            output[y] = value;
        }
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

    interface ColumnFactory {
        DoubleUnaryOperator prepare(FieldContext context, double x, double z, double initialY);
    }
}
