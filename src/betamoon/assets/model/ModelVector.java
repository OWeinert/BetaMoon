package betamoon.assets.model;

import java.io.IOException;
import java.util.List;

/** Immutable vector in model units, with +Y up and forward -Z. */
public final class ModelVector {
    public static final ModelVector ZERO = new ModelVector(0, 0, 0);
    public static final ModelVector ONE = new ModelVector(1, 1, 1);
    public final double x;
    public final double y;
    public final double z;

    public ModelVector(double x, double y, double z) {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            throw new IllegalArgumentException("Model vector must be finite");
        }
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public static ModelVector read(Object value, ModelVector fallback, String path) throws IOException {
        if (value == null && fallback != null) {
            return fallback;
        }
        List<Object> array = ModelJson.array(value, path);
        if (array.size() != 3) {
            throw new IOException(path + ": expected three numbers");
        }
        return new ModelVector(ModelJson.number(array.get(0), path), ModelJson.number(array.get(1), path),
                ModelJson.number(array.get(2), path));
    }

    public ModelVector add(ModelVector other) {
        return new ModelVector(x + other.x, y + other.y, z + other.z);
    }

    public ModelVector multiply(ModelVector other) {
        return new ModelVector(x * other.x, y * other.y, z * other.z);
    }

    public ModelVector times(double factor) {
        return new ModelVector(x * factor, y * factor, z * factor);
    }

    public ModelVector mix(ModelVector other, double weight) {
        return times(1 - weight).add(other.times(weight));
    }

    /**
     * Bedrock geometry positions use the opposite X direction to Blockbench's
     * preview.
     */
    public ModelVector fromBedrockPosition() {
        return new ModelVector(-x, y, z);
    }
}
