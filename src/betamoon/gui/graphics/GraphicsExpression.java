package betamoon.gui.graphics;

import java.util.Map;

/**
 * Immutable bounded numeric input for prepared geometry. Resolution never
 * crosses into Lua.
 */
public final class GraphicsExpression {
    public enum Source {
        CONSTANT, WIDTH, HEIGHT, PROPERTY
    }

    public final Source source;
    public final String property;
    private final double factor;
    private final double offset;
    private final double minimum;
    private final double maximum;

    private GraphicsExpression(Source source, String property, double factor, double offset, double minimum,
            double maximum) {
        if (source == null || !Double.isFinite(factor) || !Double.isFinite(offset) || !Double.isFinite(minimum)
                || !Double.isFinite(maximum) || minimum > maximum || Math.abs(minimum * factor + offset) > 1e9
                || Math.abs(maximum * factor + offset) > 1e9 || !Double.isFinite(minimum * factor + offset)
                || !Double.isFinite(maximum * factor + offset)) {
            throw new IllegalArgumentException("Prepared graphics expressions must stay within finite geometry bounds");
        }
        this.source = source;
        this.property = property;
        this.factor = factor;
        this.offset = offset;
        this.minimum = minimum;
        this.maximum = maximum;
    }

    public static GraphicsExpression constant(double value) {
        return new GraphicsExpression(Source.CONSTANT, null, 0, value, 0, 0);
    }

    public static GraphicsExpression width(double factor, double offset) {
        return new GraphicsExpression(Source.WIDTH, null, factor, offset, 0, 16384);
    }

    public static GraphicsExpression height(double factor, double offset) {
        return new GraphicsExpression(Source.HEIGHT, null, factor, offset, 0, 16384);
    }

    public static GraphicsExpression property(String name, double minimum, double maximum, double factor,
            double offset) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("Graphics property references require a declared name");
        }
        return new GraphicsExpression(Source.PROPERTY, name, factor, offset, minimum, maximum);
    }

    public double sample(double width, double height, Map<String, Object> properties) {
        double input = 0;
        if (source == Source.WIDTH) {
            input = width;
        } else if (source == Source.HEIGHT) {
            input = height;
        } else if (source == Source.PROPERTY) {
            Object value = properties.get(property);
            if (!(value instanceof Number)) {
                throw new IllegalArgumentException("Missing numeric graphics property: " + property);
            }
            input = ((Number) value).doubleValue();
        }
        if (!Double.isFinite(input) || input < minimum || input > maximum) {
            throw new IllegalArgumentException("Graphics input escaped its prepared bounds: " + source);
        }
        return input * factor + offset;
    }
}
