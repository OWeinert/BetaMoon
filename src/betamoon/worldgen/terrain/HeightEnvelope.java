package betamoon.worldgen.terrain;

import betamoon.worldgen.spatial.FieldContext;
import betamoon.worldgen.spatial.NoiseDefinition;
import betamoon.worldgen.spatial.ScalarField;
import betamoon.worldgen.spatial.ScalarFieldCompiler;
import betamoon.worldgen.spatial.SpatialRegistry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static betamoon.worldgen.spatial.GenerationValues.canonical;
import static betamoon.worldgen.spatial.GenerationValues.error;
import static betamoon.worldgen.spatial.GenerationValues.fields;
import static betamoon.worldgen.spatial.GenerationValues.integer;
import static betamoon.worldgen.spatial.GenerationValues.list;
import static betamoon.worldgen.spatial.GenerationValues.number;
import static betamoon.worldgen.spatial.GenerationValues.object;
import static betamoon.worldgen.spatial.GenerationValues.required;
import static betamoon.worldgen.spatial.GenerationValues.string;

/**
 * Ordered bounded height contributions, preserving fractional height until
 * final terrain filling.
 */
public strictfp final class HeightEnvelope {
    public final String key;
    public final String signature;
    public final double surfaceSlopeLimit;
    private final double base;
    private final double min;
    private final double max;
    private final List<Layer> layers = new ArrayList<Layer>();

    public HeightEnvelope(String key, Map<String, Object> value, SpatialRegistry.Snapshot spatial) {
        fields(value, key, "key", "dimension", "baseHeight", "layers", "heightClamp", "surfaceSlopeLimit");
        if (!string(value, "dimension", "overworld", key).equals("overworld")) {
            throw error(key, "height profiles support the overworld");
        }
        this.key = key;
        base = number(required(value, "baseHeight", key), key + ".baseHeight");
        Map<String, Object> clamp = value.containsKey("heightClamp")
                ? object(value.get("heightClamp"), key)
                : Collections.<String, Object>emptyMap();
        fields(clamp, key + ".heightClamp", "min", "max");
        min = integer(clamp.getOrDefault("min", 1.0), key + ".heightClamp.min", 1, 126);
        max = integer(clamp.getOrDefault("max", 126.0), key + ".heightClamp.max", (int) min, 126);
        if (base < 1 || base > 126) {
            throw error(key + ".baseHeight", "expected 1..126");
        }
        surfaceSlopeLimit = value.containsKey("surfaceSlopeLimit")
                ? number(value.get("surfaceSlopeLimit"), key)
                : Double.POSITIVE_INFINITY;
        if (surfaceSlopeLimit <= 0) {
            throw error(key + ".surfaceSlopeLimit", "expected a positive diagnostic threshold");
        }
        Set<String> keys = new HashSet<String>();
        List<Object> declarations = value.containsKey("layers")
                ? list(value.get("layers"), key + ".layers", 0, 8)
                : Collections.<Object>emptyList();
        for (Object declaration : declarations) {
            Map<String, Object> layer = object(declaration, key + ".layers");
            fields(layer, key, "key", "type", "scale", "amplitude", "salt", "stepHeight", "blend", "fieldCurve");
            String local = string(required(layer, "key", key), key + ".layers.key");
            if (!keys.add(local)) {
                throw error(key, "duplicate height layer key: " + local);
            }
            String type = string(required(layer, "type", key), key + ".type");
            if (type.equals("terrace")) {
                double step = integer(layer.getOrDefault("stepHeight", 4.0), key + ".stepHeight", 1, 32);
                double blend = number(layer, "blend", 0, key);
                if (blend < 0 || blend > 1) {
                    throw error(key + ".blend", "expected 0..1");
                }
                layers.add((context, x, z, height) -> {
                    double floor = Math.floor(height / step) * step;
                    return floor + (height - floor) * blend;
                });
                continue;
            }
            if (!type.equals("noise") && !type.equals("ridge") && !type.equals("valley")) {
                throw error(key, "unknown height layer: " + type);
            }
            double scale = number(layer, "scale", 256, key);
            double amplitude = number(layer, "amplitude", 16, key);
            if (scale < 16 || scale > 30000000 || Math.abs(amplitude) > 64) {
                throw error(key, "height layer scale or amplitude exceeds its bounds");
            }
            Map<String, Object> noiseConfig = new LinkedHashMap<String, Object>();
            noiseConfig.put("seedSalt", layer.getOrDefault("salt", "0"));
            NoiseDefinition noise = new NoiseDefinition(key + "/" + local, noiseConfig);
            ScalarField factor = layer.containsKey("fieldCurve")
                    ? new ScalarFieldCompiler(key + "/" + local, spatial).compile(layer.get("fieldCurve"))
                    : null;
            if (factor != null
                    && ((factor.dependencies & 2) != 0 || Math.abs(factor.min) > 4 || Math.abs(factor.max) > 4)) {
                throw error(key + ".fieldCurve", "expected a bounded X/Z field within -4..4");
            }
            layers.add((context, x, z, height) -> {
                double sample = noise.sample(context.seed, x / scale, 0, z / scale);
                double contribution = type.equals("noise") ? sample : 1 - Math.abs(sample);
                if (type.equals("valley")) {
                    contribution = -contribution;
                }
                return height + amplitude * contribution * (factor == null ? 1 : factor.sample(context, x, 0, z));
            });
        }
        Map<String, String> semantics = new TreeMap<String, String>(spatial.dependenciesIn(value));
        semantics.put("profile", canonical(value));
        signature = GenerationSignature.of(semantics);
    }

    public double height(FieldContext context, int x, int z) {
        double result = base;
        for (Layer layer : layers) {
            result = layer.apply(context, x, z, result);
        }
        return Math.max(min, Math.min(max, result));
    }

    public double slope(FieldContext context, int x, int z) {
        double dx = (height(context, x + 1, z) - height(context, x - 1, z)) / 2;
        double dz = (height(context, x, z + 1) - height(context, x, z - 1)) / 2;
        return StrictMath.sqrt(dx * dx + dz * dz);
    }

    private interface Layer {
        double apply(FieldContext context, int x, int z, double height);
    }
}
