package betamoon.gui.control;

import java.math.BigDecimal;

/**
 * Shared numeric bounds/step operations. Domain adapters decide whether and how
 * proposed values commit.
 */
public final class GuiNumericRange {
    public final double minimum;
    public final double maximum;
    public final double step;
    private final BigDecimal decimalMinimum;
    private final BigDecimal decimalStep;

    public GuiNumericRange(double minimum, double maximum, double step) {
        if (!Double.isFinite(minimum) || !Double.isFinite(maximum) || !Double.isFinite(step) || maximum < minimum
                || step <= 0) {
            throw new IllegalArgumentException("Numeric range requires finite ordered bounds and a positive step");
        }
        this.minimum = minimum;
        this.maximum = maximum;
        this.step = step;
        decimalMinimum = BigDecimal.valueOf(minimum);
        decimalStep = BigDecimal.valueOf(step);
    }

    /** Preserves the existing container proposal snapping contract exactly. */
    public static double snap(double minimum, double maximum, double step, double proposed) {
        double clamped = Math.max(minimum, Math.min(maximum, proposed));
        double snapped = minimum + Math.round((clamped - minimum) / step) * step;
        return Math.max(minimum, Math.min(maximum, snapped));
    }

    public double snap(double proposed) {
        if (!Double.isFinite(proposed)) {
            throw new IllegalArgumentException("Numeric proposals must be finite");
        }
        return snap(minimum, maximum, step, proposed);
    }

    public double valueAt(long index) {
        double value = decimalMinimum.add(decimalStep.multiply(BigDecimal.valueOf(index))).doubleValue();
        return Math.max(minimum, Math.min(maximum, value));
    }

    public long index(double value) {
        return Math.max(0, Math.min(steps(), Math.round((value - minimum) / step)));
    }

    public long steps() {
        return Math.max(0, Math.round((maximum - minimum) / step));
    }

    public double fraction(double value) {
        if (minimum == maximum) {
            return 0;
        }
        double span = maximum - minimum;
        double ratio = Double.isFinite(span)
                ? (value - minimum) / span
                : (value / 2 - minimum / 2) / (maximum / 2 - minimum / 2);
        return Math.max(0, Math.min(1, ratio));
    }
}
