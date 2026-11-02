package betamoon.gui.control;

import java.math.BigDecimal;
import java.util.regex.Pattern;

/**
 * Decimal entry policy; incomplete drafts stay separate from accepted numeric
 * values.
 */
public final class GuiNumericEntry {
    public static final int MAXIMUM_LENGTH = 128;
    private static final Pattern DRAFT = Pattern.compile("[0-9eE+.-]*");
    private static final Pattern NUMBER = Pattern
            .compile("[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?");
    private final GuiNumericRange range;
    public final double step;

    public GuiNumericEntry(double minimum, double maximum, double step) {
        if (Math.abs(minimum) > 1.0E12 || Math.abs(maximum) > 1.0E12 || !Double.isFinite(step) || step < 0
                || step > 1.0E12 || step > 0 && step < 1.0E-6) {
            throw new IllegalArgumentException("Numeric entry requires bounded endpoints and zero or explicit step");
        }
        range = new GuiNumericRange(minimum, maximum, step == 0 ? 1 : step);
        this.step = step;
    }

    public double accept(double value) {
        if (!Double.isFinite(value) || value < range.minimum || value > range.maximum) {
            throw new IllegalArgumentException("Numeric entry value is outside its finite range");
        }
        return step == 0 ? value == 0 ? 0 : value : range.snapDecimal(value);
    }

    /** Reconcile an existing local value after its declared range changes. */
    public double reconcile(double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("Numeric entry state must be finite");
        }
        return accept(Math.max(range.minimum, Math.min(range.maximum, value)));
    }

    public double parse(String text) {
        if (!draft(text) || !NUMBER.matcher(text).matches()) {
            throw new IllegalArgumentException("Enter a decimal number");
        }
        double value = Double.parseDouble(text);
        if (value == 0 && new BigDecimal(text).signum() != 0) {
            throw new IllegalArgumentException("Numeric entry is smaller than a representable number");
        }
        return accept(value);
    }

    public static boolean draft(String text) {
        return text != null && text.length() <= MAXIMUM_LENGTH && DRAFT.matcher(text).matches();
    }

    public static String format(double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("Numeric entry formatting requires a finite number");
        }
        BigDecimal decimal = BigDecimal.valueOf(value == 0 ? 0 : value).stripTrailingZeros();
        return value == 0 || Math.abs(value) >= 1.0E-6 ? decimal.toPlainString() : decimal.toString();
    }
}
