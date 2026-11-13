package betamoon.gui.control;

import java.util.Arrays;
import java.util.Map;

/**
 * Immutable bounded icon geometry and fractional values; independent of player
 * state.
 */
public final class GuiIconMeterModel {
    public final double minimum;
    public final double maximum;
    public final double value;
    public final Double reference;
    public final double units;
    public final int capacity;
    public final int maxIcons;
    public final int columns;
    public final int width;
    public final int height;
    public final int pitchX;
    public final int pitchY;
    public final String order;
    public final boolean steps;

    public GuiIconMeterModel(Map<String, Object> values, int skinWidth, int skinHeight, int skinPitchX,
            int skinPitchY) {
        minimum = number(values, "minimum", 0);
        maximum = number(values, "maximum", 10);
        value = number(values, "value", 0);
        reference = values.get("referenceValue") instanceof Number ? number(values, "referenceValue", 0) : null;
        units = number(values, "unitsPerIcon", 1);
        maxIcons = (int) number(values, "maxIcons", 256);
        double count = Math.ceil((maximum - minimum) / units);
        if (maximum <= minimum || units <= 0 || !Double.isFinite(count) || count < 1 || count > maxIcons || maxIcons < 1
                || maxIcons > 256) {
            throw new IllegalArgumentException(
                    "Icon meter requires increasing bounds, positive units and capacity within maxIcons (1..256)");
        }
        capacity = (int) count;
        int requestedColumns = (int) number(values, "columns", 0);
        columns = requestedColumns == 0 ? capacity : requestedColumns;
        width = intrinsic(values, "iconWidth", skinWidth, 256);
        height = intrinsic(values, "iconHeight", skinHeight, 256);
        pitchX = intrinsic(values, "pitchX", skinPitchX, 512);
        pitchY = intrinsic(values, "pitchY", skinPitchY, 512);
        order = (String) values.getOrDefault("fillOrder", "left_to_right");
        String mode = (String) values.getOrDefault("partialMode", "clip");
        if (columns < 1 || columns > 256
                || !Arrays.asList("left_to_right", "right_to_left", "top_to_bottom", "bottom_to_top").contains(order)
                || !Arrays.asList("clip", "steps").contains(mode)) {
            throw new IllegalArgumentException("Invalid icon meter columns, fillOrder or partialMode");
        }
        steps = mode.equals("steps");
    }

    private static int intrinsic(Map<String, Object> values, String name, int fallback, int maximum) {
        int value = (int) number(values, name, 0);
        value = value == 0 ? fallback : value;
        if (value < 1 || value > maximum) {
            throw new IllegalArgumentException("Icon meter " + name + " is outside its supported range");
        }
        return value;
    }

    public double fraction(int index, double value) {
        return Math.max(0,
                Math.min(1, (Math.max(minimum, Math.min(maximum, value)) - minimum - index * units) / units));
    }

    public int column(int index) {
        int column = vertical() ? index / rows() : index % columns;
        return order.equals("right_to_left") ? Math.min(columns, capacity) - 1 - column : column;
    }

    public int row(int index) {
        int row = vertical() ? index % rows() : index / columns;
        return order.equals("bottom_to_top") ? rows() - 1 - row : row;
    }

    private boolean vertical() {
        return order.equals("top_to_bottom") || order.equals("bottom_to_top");
    }

    public int rows() {
        return (capacity + columns - 1) / columns;
    }

    public int measuredWidth() {
        return (Math.min(columns, capacity) - 1) * pitchX + width;
    }

    public int measuredHeight() {
        return (rows() - 1) * pitchY + height;
    }

    private static double number(Map<String, Object> values, String name, double fallback) {
        Object value = values.get(name);
        double number = value instanceof Number ? ((Number) value).doubleValue() : fallback;
        if (!Double.isFinite(number)) {
            throw new IllegalArgumentException("Icon meter " + name + " must be finite");
        }
        return number;
    }
}
