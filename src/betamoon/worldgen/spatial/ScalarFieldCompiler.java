package betamoon.worldgen.spatial;

import betamoon.worldgen.SeedMixer;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.function.DoubleBinaryOperator;
import java.util.function.DoubleUnaryOperator;

import static betamoon.worldgen.spatial.GenerationValues.bool;
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
 * Bounded graph compilation, interval checks, structural interning and constant
 * folding.
 */
public strictfp final class ScalarFieldCompiler {
    public static final int MAX_NODES = 256;
    public static final int MAX_DEPTH = 32;
    private final Resolver resolver;
    private final String key;
    private final Map<String, ScalarField> interned = new HashMap<String, ScalarField>();
    private int count;
    private final Set<String> requiredDimensions = new LinkedHashSet<String>();

    public ScalarFieldCompiler(String key, Resolver resolver) {
        this.key = key;
        this.resolver = resolver;
    }

    public ScalarField compile(Object value) {
        count = 0;
        interned.clear();
        requiredDimensions.clear();
        return node(value, key + ".node", 0).requiringDimensions(requiredDimensions);
    }

    private ScalarField reference(String key) {
        ScalarField field = resolver.field(key);
        requiredDimensions.addAll(field.requiredDimensions());
        return field;
    }

    private ScalarField node(Object value, String path, int depth) {
        if (++count > MAX_NODES || depth > MAX_DEPTH) {
            throw error(path, "field graph exceeds node/depth budget");
        }
        String canonical = canonical(value);
        ScalarField existing = interned.get(canonical);
        if (existing != null) {
            return existing;
        }
        ScalarField result;
        if (value instanceof Number) {
            result = constant(number(value, path));
        } else if (value instanceof String) {
            result = reference((String) value);
        } else {
            Map<String, Object> object = object(value, path);
            String type = string(required(object, "type", path), path + ".type");
            result = operation(type, object, path, depth + 1);
        }
        if (result.nodeCount > MAX_NODES) {
            throw error(path, "expanded field reference graph exceeds node budget");
        }
        long work = sampleWork(value, result);
        if (work > 32768) {
            throw error(path, "field exceeds the 32768-operation per-sample work budget");
        }
        if (work != result.sampleWork) {
            ScalarField compiled = result;
            result = new ScalarField(result.min, result.max, result.dependencies, result.nodeCount, compiled::sample,
                    work).withColumnFactory(compiled.columnFactory);
        }
        result.label(path);
        interned.put(canonical, result);
        return result;
    }

    private long sampleWork(Object declaration, ScalarField result) {
        if (result.dependencies == 0 || declaration instanceof Number) {
            return 1;
        }
        if (declaration instanceof String) {
            return resolver.field((String) declaration).sampleWork;
        }
        Map<String, Object> value = object(declaration, key);
        String type = string(value.get("type"), key);
        if (type.equals("reference")) {
            return resolver.field(string(value.get("field"), key)).sampleWork;
        }
        long work = type.equals("noise") ? 16 : type.equals("spots") ? 64 : 1;
        for (String child : new String[]{"input", "left", "right", "alpha", "from", "to", "whenTrue", "whenFalse"}) {
            if (value.containsKey(child)) {
                work += childWork(value.get(child));
            }
        }
        if (value.containsKey("inputs")) {
            for (Object child : list(value.get("inputs"), key, 1, 256)) {
                work += childWork(child);
            }
        }
        if (type.equals("shift") || type.equals("scale_coordinates")) {
            for (String axis : new String[]{"x", "y", "z"}) {
                work += value.containsKey(axis) ? childWork(value.get(axis)) : 1;
            }
        }
        if (type.equals("find_top_surface")) {
            Object input = value.get("input");
            ScalarField child = input instanceof String
                    ? resolver.field((String) input)
                    : interned.get(canonical(input));
            if (child != null && (child.dependencies & 2) != 0) {
                int min = integer(value.getOrDefault("minY", 0.0), key, 0, 127);
                int max = integer(value.getOrDefault("maxY", 127.0), key, min, 127);
                work = 1 + child.sampleWork * (max - min + 1);
            }
        }
        return work;
    }

    private long childWork(Object value) {
        if (value instanceof String) {
            return resolver.field((String) value).sampleWork;
        }
        ScalarField child = interned.get(canonical(value));
        return child == null ? 1 : child.sampleWork;
    }

    private ScalarField operation(String type, Map<String, Object> value, String path, int depth) {
        switch (type) {
            case "constant":
                fields(value, path, "type", "value");
                return constant(number(required(value, "value", path), path + ".value"));
            case "reference":
                fields(value, path, "type", "field");
                return reference(string(required(value, "field", path), path + ".field"));
            case "x":
            case "y":
            case "z":
                fields(value, path, "type");
                return field(-1.0e12, 1.0e12, type.equals("x") ? 1 : type.equals("y") ? 2 : 4, 1,
                        (context, x, y, z) -> type.equals("x") ? x : type.equals("y") ? y : z);
            case "noise":
                return noise(value, path);
            case "biome_value": {
                fields(value, path, "type", "property");
                String property = string(required(value, "property", path), path + ".property");
                if (!property.equals("temperature") && !property.equals("humidity")) {
                    throw error(path + ".property", "expected temperature or humidity");
                }
                return field(0, 1, 45, 1, (context, x, y, z) -> {
                    if (context.environment == null) {
                        throw new IllegalStateException("Biome sampling context is unavailable");
                    }
                    if (Math.abs(x) > 30000000 || Math.abs(z) > 30000000) {
                        throw new IllegalStateException("Biome field sampling exceeds the world coordinate bounds");
                    }
                    return context.environment.biomeValue(property, (int) Math.floor(x), (int) Math.floor(z));
                });
            }
            case "distance":
                return distance(value, path);
            case "spots":
                return spots(value, path);
            case "random_hash": {
                fields(value, path, "type", "salt");
                long salt = SeedMixer.hash(key + ":" + value.getOrDefault("salt", "0"));
                return field(0, 1, 15, 1,
                        (context, x, y, z) -> NoiseDefinition.unit(SeedMixer.derive(context.seed, salt),
                                (long) Math.floor(x), (long) Math.floor(y), (long) Math.floor(z)));
            }
            case "add":
            case "multiply":
            case "min":
            case "max": {
                fields(value, path, "type", "inputs");
                List<Object> inputs = list(required(value, "inputs", path), path + ".inputs", 2, 8);
                ScalarField result = node(inputs.get(0), path + ".inputs[1]", depth);
                for (int i = 1; i < inputs.size(); i++) {
                    result = binary(type, result, node(inputs.get(i), path + ".inputs[" + (i + 1) + "]", depth), path);
                }
                return result;
            }
            case "subtract":
            case "divide": {
                fields(value, path, "type", "left", "right");
                return binary(type, node(required(value, "left", path), path + ".left", depth),
                        node(required(value, "right", path), path + ".right", depth), path);
            }
            case "abs":
            case "negate":
            case "square":
            case "cube":
            case "sqrt":
            case "floor":
            case "ceil":
            case "round": {
                fields(value, path, "type", "input");
                ScalarField input = node(required(value, "input", path), path + ".input", depth);
                DoubleUnaryOperator op;
                double min;
                double max;
                switch (type) {
                    case "abs":
                        op = Math::abs;
                        break;
                    case "negate":
                        op = x -> -x;
                        break;
                    case "square":
                        op = x -> x * x;
                        break;
                    case "cube":
                        op = x -> x * x * x;
                        break;
                    case "sqrt":
                        if (input.min < 0) {
                            throw error(path, "sqrt input can be negative; clamp it first");
                        }
                        op = StrictMath::sqrt;
                        break;
                    case "floor":
                        op = Math::floor;
                        break;
                    case "ceil":
                        op = Math::ceil;
                        break;
                    default:
                        op = x -> Math.floor(x + 0.5);
                        break;
                }
                min = Math.min(op.applyAsDouble(input.min), op.applyAsDouble(input.max));
                max = Math.max(op.applyAsDouble(input.min), op.applyAsDouble(input.max));
                if ((type.equals("abs") || type.equals("square")) && input.min <= 0 && input.max >= 0) {
                    min = 0;
                }
                return unary(input, min, max, op);
            }
            case "power": {
                fields(value, path, "type", "input", "exponent");
                ScalarField input = node(required(value, "input", path), path + ".input", depth);
                int exponent = integer(required(value, "exponent", path), path + ".exponent", 0, 16);
                double a = StrictMath.pow(input.min, exponent);
                double b = StrictMath.pow(input.max, exponent);
                double low = exponent > 0 && exponent % 2 == 0 && input.min <= 0 && input.max >= 0 ? 0 : Math.min(a, b);
                return unary(input, low, Math.max(a, b), x -> StrictMath.pow(x, exponent));
            }
            case "clamp":
            case "smoothstep": {
                fields(value, path, "type", "input", "min", "max");
                ScalarField input = node(required(value, "input", path), path + ".input", depth);
                double low = number(required(value, "min", path), path + ".min");
                double high = number(required(value, "max", path), path + ".max");
                if (low >= high) {
                    throw error(path, "expected min < max");
                }
                return type.equals("clamp")
                        ? unary(input, Math.max(low, Math.min(high, input.min)),
                                Math.max(low, Math.min(high, input.max)), x -> Math.max(low, Math.min(high, x)))
                        : unary(input, 0, 1, x -> {
                            double t = Math.max(0, Math.min(1, (x - low) / (high - low)));
                            return t * t * (3 - 2 * t);
                        });
            }
            case "map_range": {
                fields(value, path, "type", "input", "fromMin", "fromMax", "toMin", "toMax", "clamp");
                ScalarField input = node(required(value, "input", path), path + ".input", depth);
                double from = number(required(value, "fromMin", path), path + ".fromMin");
                double to = number(required(value, "fromMax", path), path + ".fromMax");
                double low = number(required(value, "toMin", path), path + ".toMin");
                double high = number(required(value, "toMax", path), path + ".toMax");
                if (from >= to) {
                    throw error(path, "fromMin must be smaller than fromMax");
                }
                boolean clamp = bool(value, "clamp", true, path);
                DoubleUnaryOperator mapping = x -> low + (high - low)
                        * (clamp ? Math.max(0, Math.min(1, (x - from) / (to - from))) : (x - from) / (to - from));
                return unary(input, Math.min(mapping.applyAsDouble(input.min), mapping.applyAsDouble(input.max)),
                        Math.max(mapping.applyAsDouble(input.min), mapping.applyAsDouble(input.max)), mapping);
            }
            case "curve":
            case "spline":
                return curve(value, path, depth, type.equals("spline"));
            case "terrace": {
                fields(value, path, "type", "input", "stepHeight", "blend");
                ScalarField input = node(required(value, "input", path), path + ".input", depth);
                double step = number(required(value, "stepHeight", path), path + ".stepHeight");
                double blend = number(value, "blend", 0, path);
                if (!(step > 0 && step <= 128 && blend >= 0 && blend <= 1)) {
                    throw error(path, "terrace requires stepHeight in (0,128] and blend in 0..1");
                }
                return unary(input, Math.floor(input.min / step) * step, Math.ceil(input.max / step) * step, x -> {
                    double base = Math.floor(x / step);
                    double t = x / step - base;
                    double weight = blend == 0 ? 0 : Math.max(0, (t - (1 - blend)) / blend);
                    return (base + weight * weight * (3 - 2 * weight)) * step;
                });
            }
            case "lerp": {
                fields(value, path, "type", "alpha", "from", "to");
                ScalarField alpha = node(required(value, "alpha", path), path + ".alpha", depth);
                ScalarField from = node(required(value, "from", path), path + ".from", depth);
                ScalarField to = node(required(value, "to", path), path + ".to", depth);
                if (alpha.min < 0 || alpha.max > 1) {
                    throw error(path + ".alpha", "must remain within 0..1");
                }
                return field(Math.min(from.min, to.min), Math.max(from.max, to.max),
                        alpha.dependencies | from.dependencies | to.dependencies,
                        1 + alpha.nodeCount + from.nodeCount + to.nodeCount, (context, x, y, z) -> {
                            double a = alpha.sample(context, x, y, z);
                            double f = from.sample(context, x, y, z);
                            return f + (to.sample(context, x, y, z) - f) * a;
                        });
            }
            case "select":
            case "range_choice": {
                fields(value, path, "type", "input", "min", "max", "whenTrue", "whenFalse");
                ScalarField input = node(required(value, "input", path), path + ".input", depth);
                ScalarField yes = node(required(value, "whenTrue", path), path + ".whenTrue", depth);
                ScalarField no = node(required(value, "whenFalse", path), path + ".whenFalse", depth);
                double min = number(required(value, "min", path), path + ".min");
                double max = number(required(value, "max", path), path + ".max");
                if (min > max) {
                    throw error(path, "selection range must be ordered");
                }
                return field(Math.min(yes.min, no.min), Math.max(yes.max, no.max),
                        input.dependencies | yes.dependencies | no.dependencies,
                        1 + input.nodeCount + yes.nodeCount + no.nodeCount, (context, x, y, z) -> {
                            double v = input.sample(context, x, y, z);
                            return (v >= min && v <= max ? yes : no).sample(context, x, y, z);
                        });
            }
            case "y_gradient": {
                fields(value, path, "type", "fromY", "toY", "fromValue", "toValue");
                double fromY = number(required(value, "fromY", path), path + ".fromY");
                double toY = number(required(value, "toY", path), path + ".toY");
                double from = number(required(value, "fromValue", path), path + ".fromValue");
                double to = number(required(value, "toValue", path), path + ".toValue");
                if (fromY >= toY) {
                    throw error(path, "fromY must be smaller than toY");
                }
                return field(Math.min(from, to), Math.max(from, to), 2, 1, (context, x, y, z) -> from
                        + (to - from) * Math.max(0, Math.min(1, (y - fromY) / (toY - fromY))));
            }
            case "shift":
            case "scale_coordinates":
            case "rotate_2d":
                return transform(type, value, path, depth);
            case "cache_2d":
            case "cache_cell":
            case "cache_once": {
                fields(value, path, "type", "input");
                ScalarField input = node(required(value, "input", path), path + ".input", depth);
                if (type.equals("cache_2d") && (input.dependencies & 2) != 0) {
                    throw error(path, "cache_2d cannot cache a Y-dependent field");
                }
                return cached(input);
            }
            case "find_top_surface": {
                fields(value, path, "type", "input", "threshold", "minY", "maxY");
                ScalarField input = node(required(value, "input", path), path + ".input", depth);
                int min = value.containsKey("minY") ? integer(value.get("minY"), path + ".minY", 0, 127) : 0;
                int max = value.containsKey("maxY") ? integer(value.get("maxY"), path + ".maxY", min, 127) : 127;
                double threshold = number(value, "threshold", 0, path);
                if ((input.dependencies & 2) != 0 && input.sampleWork * (max - min + 1) > 32768) {
                    throw error(path, "surface scan exceeds per-column work budget");
                }
                if ((input.dependencies & 2) == 0) {
                    return field(min - 1, max, input.dependencies, input.nodeCount + 1,
                            (context, x, y, z) -> input.sample(context, x, 0, z) > threshold ? max : min - 1);
                }
                return field(min - 1, max, input.dependencies & ~2, input.nodeCount + 1, (context, x, y, z) -> {
                    for (int iy = max; iy >= min; iy--) {
                        if (input.sample(context, x, iy, z) > threshold) {
                            return iy;
                        }
                    }
                    return min - 1;
                });
            }
            default:
                throw error(path + ".type", "unknown field node: " + type);
        }
    }

    private ScalarField noise(Map<String, Object> value, String path) {
        fields(value, path, "type", "noise", "scale");
        Object declaration = required(value, "noise", path);
        NoiseDefinition noise = declaration instanceof String
                ? resolver.noise((String) declaration)
                : new NoiseDefinition(key + ":inline", object(declaration, path + ".noise"));
        Map<String, Object> scale = value.containsKey("scale")
                ? object(value.get("scale"), path + ".scale")
                : Collections.<String, Object>emptyMap();
        fields(scale, path + ".scale", "x", "y", "z");
        double sx = number(scale, "x", 1, path + ".scale");
        double sy = number(scale, "y", 0, path + ".scale");
        double sz = number(scale, "z", 1, path + ".scale");
        if (Math.max(Math.abs(sx), Math.max(Math.abs(sy), Math.abs(sz))) > 1024 || noise.is2D() && sy != 0) {
            throw error(path, "noise scale exceeds limits or 2D noise has a Y scale");
        }
        return field(-noise.bound(), noise.bound(), (sx != 0 ? 1 : 0) | (sy != 0 ? 2 : 0) | (sz != 0 ? 4 : 0) | 8, 1,
                (context, x, y, z) -> noise.sample(context.seed, x * sx, y * sy, z * sz))
                .withColumnFactory((context, x, z, initialY) -> {
                    DoubleUnaryOperator prepared = noise.prepareColumn(context.seed, x * sx, z * sz);
                    return y -> prepared.applyAsDouble(y * sy);
                });
    }

    private ScalarField distance(Map<String, Object> value, String path) {
        fields(value, path, "type", "anchor", "blocksPerUnit", "metric");
        String anchorKey = string(required(value, "anchor", path), path + ".anchor");
        boolean original = anchorKey.equals("minecraft:original_spawn")
                || anchorKey.equals("minecraft:anchor/original_spawn");
        AnchorDefinition fixed = original ? null : resolver.anchor(anchorKey);
        double scale = number(value, "blocksPerUnit", 1, path);
        String metric = string(value, "metric", "euclidean", path);
        if (!(scale > 0) || !Arrays.asList("euclidean", "manhattan", "chebyshev").contains(metric)) {
            throw error(path, "invalid distance scale or metric");
        }
        return field(0, 4.0e12 / scale, 5 | (original ? 16 : 0), 1, (context, x, y, z) -> {
            if (original && !context.hasOriginalSpawn) {
                throw new IllegalStateException("Original spawn anchor is unavailable; explicit migration is required");
            }
            if (fixed != null && !fixed.dimensionKey.equals(context.dimensionKey)) {
                throw new IllegalStateException("Field anchor belongs to another dimension");
            }
            double dx = Math.abs(x - (original ? context.spawnX : fixed.x));
            double dz = Math.abs(z - (original ? context.spawnZ : fixed.z));
            return (metric.equals("manhattan")
                    ? dx + dz
                    : metric.equals("chebyshev") ? Math.max(dx, dz) : StrictMath.sqrt(dx * dx + dz * dz)) / scale;
        });
    }

    private ScalarField spots(Map<String, Object> value, String path) {
        fields(value, path, "type", "cellSize", "chance", "radius", "amplitude", "salt", "excludeRadius");
        int cell = integer(required(value, "cellSize", path), path + ".cellSize", 64, 30000000);
        double chance = number(required(value, "chance", path), path + ".chance");
        Map<String, Object> radius = object(required(value, "radius", path), path + ".radius");
        fields(radius, path + ".radius", "min", "max");
        double min = number(required(radius, "min", path), path + ".radius.min");
        double max = number(required(radius, "max", path), path + ".radius.max");
        double amplitude = number(required(value, "amplitude", path), path + ".amplitude");
        double exclude = number(value, "excludeRadius", 0, path);
        long salt = SeedMixer.hash(key + ":" + value.getOrDefault("salt", "0"));
        if (chance < 0 || chance > 1 || min <= 0 || max < min || max > cell || exclude < 0) {
            throw error(path, "invalid spot chance, radius or exclusion");
        }
        return field(Math.min(0, amplitude) * 9, Math.max(0, amplitude) * 9, 13 | (exclude > 0 ? 16 : 0), 1,
                (context, x, y, z) -> {
                    if (exclude > 0 && !context.hasOriginalSpawn) {
                        throw new IllegalStateException("Original spawn anchor is unavailable");
                    }
                    long seed = SeedMixer.derive(context.seed, salt);
                    long cx = (long) Math.floor(x / cell);
                    long cz = (long) Math.floor(z / cell);
                    double sum = 0;
                    for (long ix = cx - 1; ix <= cx + 1; ix++) {
                        for (long iz = cz - 1; iz <= cz + 1; iz++) {
                            if (NoiseDefinition.unit(seed, ix, 0, iz) >= chance) {
                                continue;
                            }
                            double px = (ix + NoiseDefinition.unit(seed, ix, 1, iz)) * cell;
                            double pz = (iz + NoiseDefinition.unit(seed, ix, 2, iz)) * cell;
                            if (exclude > 0 && StrictMath.hypot(px - context.spawnX, pz - context.spawnZ) < exclude) {
                                continue;
                            }
                            double r = min + (max - min) * NoiseDefinition.unit(seed, ix, 3, iz);
                            double t = Math.max(0, 1 - StrictMath.hypot(x - px, z - pz) / r);
                            sum += amplitude * t * t * (3 - 2 * t);
                        }
                    }
                    return sum;
                });
    }

    private ScalarField curve(Map<String, Object> value, String path, int depth, boolean smooth) {
        fields(value, path, "type", "input", "points");
        ScalarField input = node(required(value, "input", path), path + ".input", depth);
        List<Object> points = list(required(value, "points", path), path + ".points", 2, 64);
        double[] at = new double[points.size()];
        double[] values = new double[points.size()];
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < points.size(); i++) {
            Map<String, Object> point = object(points.get(i), path + ".points[" + (i + 1) + "]");
            fields(point, path, "at", "value");
            at[i] = number(required(point, "at", path), path + ".at");
            values[i] = number(required(point, "value", path), path + ".value");
            if (i > 0 && at[i] <= at[i - 1]) {
                throw error(path, "curve points must have strictly increasing at values");
            }
            min = Math.min(min, values[i]);
            max = Math.max(max, values[i]);
        }
        return unary(input, min, max, x -> {
            if (x <= at[0]) {
                return values[0];
            }
            for (int i = 1; i < at.length; i++) {
                if (x <= at[i]) {
                    double t = (x - at[i - 1]) / (at[i] - at[i - 1]);
                    if (smooth) {
                        t = t * t * (3 - 2 * t);
                    }
                    return values[i - 1] + (values[i] - values[i - 1]) * t;
                }
            }
            return values[values.length - 1];
        });
    }

    private ScalarField transform(String type, Map<String, Object> value, String path, int depth) {
        fields(value, path, "type", "input", "x", "y", "z", "degrees");
        ScalarField input = node(required(value, "input", path), path + ".input", depth);
        ScalarField dx = node(value.getOrDefault("x", type.equals("scale_coordinates") ? 1.0 : 0.0), path + ".x",
                depth);
        ScalarField dy = node(value.getOrDefault("y", type.equals("scale_coordinates") ? 1.0 : 0.0), path + ".y",
                depth);
        ScalarField dz = node(value.getOrDefault("z", type.equals("scale_coordinates") ? 1.0 : 0.0), path + ".z",
                depth);
        double degrees = number(value, "degrees", 0, path);
        double sin = StrictMath.sin(degrees * Math.PI / 180);
        double cos = StrictMath.cos(degrees * Math.PI / 180);
        int dependencies = input.dependencies | dx.dependencies | dy.dependencies | dz.dependencies;
        if (type.equals("rotate_2d") && (input.dependencies & 5) != 0) {
            dependencies |= 5;
        }
        ScalarField result = field(input.min, input.max, dependencies,
                1 + input.nodeCount + dx.nodeCount + dy.nodeCount + dz.nodeCount, (context, x, y, z) -> {
                    if (type.equals("rotate_2d")) {
                        return input.sample(context, x * cos - z * sin, y, x * sin + z * cos);
                    }
                    double ox = dx.sample(context, x, y, z);
                    double oy = dy.sample(context, x, y, z);
                    double oz = dz.sample(context, x, y, z);
                    return type.equals("shift")
                            ? input.sample(context, x + ox, y + oy, z + oz)
                            : input.sample(context, x * ox, y * oy, z * oz);
                });
        if (input.columnFactory != null && ((dx.dependencies | dy.dependencies | dz.dependencies) & 2) == 0) {
            result.withColumnFactory((context, x, z, initialY) -> {
                if (type.equals("rotate_2d")) {
                    return input.prepareColumn(context, x * cos - z * sin, x * sin + z * cos, initialY);
                }
                double ox = dx.sample(context, x, initialY, z);
                double oy = dy.sample(context, x, initialY, z);
                double oz = dz.sample(context, x, initialY, z);
                boolean shift = type.equals("shift");
                DoubleUnaryOperator prepared = input.prepareColumn(context, shift ? x + ox : x * ox,
                        shift ? z + oz : z * oz, shift ? initialY + oy : initialY * oy);
                return y -> prepared.applyAsDouble(shift ? y + oy : y * oy);
            });
        }
        return result;
    }

    private static ScalarField binary(String type, ScalarField left, ScalarField right, String path) {
        DoubleBinaryOperator op;
        switch (type) {
            case "add":
                op = (a, b) -> a + b;
                break;
            case "subtract":
                op = (a, b) -> a - b;
                break;
            case "multiply":
                op = (a, b) -> a * b;
                break;
            case "divide":
                if (right.min <= 0 && right.max >= 0) {
                    throw error(path, "divisor can be zero; use a bounded nonzero field");
                }
                op = (a, b) -> a / b;
                break;
            case "min":
                op = Math::min;
                break;
            default:
                op = Math::max;
                break;
        }
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        for (double a : new double[]{left.min, left.max}) {
            for (double b : new double[]{right.min, right.max}) {
                double v = op.applyAsDouble(a, b);
                min = Math.min(min, v);
                max = Math.max(max, v);
            }
        }
        return field(min, max, left.dependencies | right.dependencies, left.nodeCount + right.nodeCount + 1,
                (context, x, y, z) -> op.applyAsDouble(left.sample(context, x, y, z), right.sample(context, x, y, z)));
    }

    private static ScalarField unary(ScalarField input, double min, double max, DoubleUnaryOperator operation) {
        return field(min, max, input.dependencies, input.nodeCount + 1,
                (context, x, y, z) -> operation.applyAsDouble(input.sample(context, x, y, z)));
    }

    private static ScalarField constant(double value) {
        return new ScalarField(value, value, 0, 1, (context, x, y, z) -> value);
    }

    private static ScalarField field(double min, double max, int dependencies, int count,
            ScalarField.Evaluator operation) {
        if (dependencies == 0) {
            return constant(operation.sample(new FieldContext(0, 0), 0, 0, 0));
        }
        ScalarField result = new ScalarField(min, max, dependencies, count, operation);
        return (dependencies & 2) == 0 ? cached(result) : result;
    }

    private static ScalarField cached(ScalarField input) {
        return new ScalarField(input.min, input.max, input.dependencies, input.nodeCount, (context, x, y, z) -> {
            if (context.trace != null) {
                return input.sample(context, x, y, z);
            }
            SampleCache saved = context.samplingData(input, SampleCache::new);
            double cacheX = (input.dependencies & 1) == 0 ? 0 : x;
            double cacheY = (input.dependencies & 2) == 0 ? 0 : y;
            double cacheZ = (input.dependencies & 4) == 0 ? 0 : z;
            if (!saved.valid || saved.x != cacheX || saved.y != cacheY || saved.z != cacheZ) {
                saved.value = input.sample(context, x, y, z);
                saved.valid = true;
                saved.x = cacheX;
                saved.y = cacheY;
                saved.z = cacheZ;
            }
            return saved.value;
        });
    }

    private static final class SampleCache {
        private boolean valid;
        private double x;
        private double y;
        private double z;
        private double value;
    }

    public interface Resolver {
        ScalarField field(String key);

        NoiseDefinition noise(String key);

        AnchorDefinition anchor(String key);
    }
}
