package betamoon.worldgen.spatial;

import betamoon.worldgen.WorldGenKey;
import betamoon.worldgen.WorldGenKind;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static betamoon.worldgen.spatial.GenerationValues.canonical;
import static betamoon.worldgen.spatial.GenerationValues.error;
import static betamoon.worldgen.spatial.GenerationValues.fields;
import static betamoon.worldgen.spatial.GenerationValues.list;
import static betamoon.worldgen.spatial.GenerationValues.number;
import static betamoon.worldgen.spatial.GenerationValues.object;
import static betamoon.worldgen.spatial.GenerationValues.required;
import static betamoon.worldgen.spatial.GenerationValues.string;

/**
 * One inclusive eligibility gate and optional bounded weight curve for all
 * position-aware consumers.
 */
public final class SpatialSelector {
    public final String key;
    public final String signature;
    private final ScalarField field;
    private final double min;
    private final boolean declaredMin;
    private final boolean declaredMax;
    private final String declarationSignature;
    private final double max;
    private final double[] at;
    private final double[] weights;
    public final double maximumWeight;

    public SpatialSelector(Object declaration, String path, SpatialRegistry.Snapshot snapshot) {
        Map<String, Object> value = object(declaration, path);
        fields(value, path, "field", "min", "max", "weight");
        key = WorldGenKey.parse(string(required(value, "field", path), path + ".field"), WorldGenKind.FIELD).toString();
        field = snapshot.field(key);
        if ((field.dependencies & 2) != 0) {
            throw error(path, "spatial selectors require an X/Z field");
        }
        declaredMin = value.containsKey("min");
        declaredMax = value.containsKey("max");
        declarationSignature = canonical(value);
        min = number(value, "min", field.min, path);
        max = number(value, "max", field.max, path);
        if (min > max) {
            throw error(path, "spatial selector bounds must be ordered");
        }
        signature = snapshot.signature(key, WorldGenKind.FIELD) + ":" + canonical(value);
        List<Object> points = value.containsKey("weight")
                ? list(value.get("weight"), path + ".weight", 2, 64)
                : Collections.<Object>emptyList();
        at = new double[points.size()];
        weights = new double[points.size()];
        for (int i = 0; i < points.size(); i++) {
            Map<String, Object> point = object(points.get(i), path + ".weight[" + (i + 1) + "]");
            fields(point, path, "at", "value");
            at[i] = number(required(point, "at", path), path + ".at");
            weights[i] = number(required(point, "value", path), path + ".value");
            if (weights[i] < 0 || weights[i] > 4 || i > 0 && at[i] <= at[i - 1]) {
                throw error(path, "weight curve requires increasing at coordinates and weights in 0..4");
            }
        }
        double maximum = 1;
        for (double weight : weights) {
            maximum = Math.max(maximum, weight);
        }
        maximumWeight = maximum;
    }

    private ScalarField field(FieldContext context) {
        if (context.spatial == null) {
            return field;
        }
        return context.spatial.fieldIfPresent(key);
    }

    public double value(FieldContext context, double x, double z) {
        ScalarField selected = field(context);
        if (selected == null) {
            throw new IllegalStateException("Spatial field is unavailable in this world's generation snapshot: " + key);
        }
        return selected.sample(context, x, 0, z);
    }

    public String signatureForSnapshot(SpatialRegistry.Snapshot snapshot) {
        return snapshot == null ? signature : snapshot.signature(key, WorldGenKind.FIELD) + ":" + declarationSignature;
    }

    public String declarationSignature() {
        return declarationSignature;
    }

    public boolean requiresOriginalSpawn(SpatialRegistry.Snapshot snapshot) {
        return snapshot == null ? requiresOriginalSpawn() : (snapshot.field(key).dependencies & 16) != 0;
    }

    public boolean requiresOriginalSpawn() {
        return (field.dependencies & 16) != 0;
    }

    public double weight(FieldContext context, double x, double z) {
        ScalarField selected = field(context);
        if (selected == null || context.dimension != 0
                || (selected.dependencies & 16) != 0 && !context.hasOriginalSpawn) {
            return 0;
        }
        double sample = selected.sample(context, x, 0, z);
        if (sample < (declaredMin ? min : selected.min) || sample > (declaredMax ? max : selected.max)) {
            return 0;
        }
        if (at.length == 0) {
            return 1;
        }
        if (sample <= at[0]) {
            return weights[0];
        }
        for (int i = 1; i < at.length; i++) {
            if (sample <= at[i]) {
                double t = (sample - at[i - 1]) / (at[i] - at[i - 1]);
                return weights[i - 1] + (weights[i] - weights[i - 1]) * t;
            }
        }
        return weights[weights.length - 1];
    }
}
