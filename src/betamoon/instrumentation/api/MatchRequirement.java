package betamoon.instrumentation.api;

/** Inclusive range describing how many target methods a hook may match. */
public final class MatchRequirement {
    private final int minimum;
    private final int maximum;

    private MatchRequirement(int minimum, int maximum) {
        if (minimum < 0 || maximum < minimum) {
            throw new IllegalArgumentException("Invalid match range: " + minimum + ".." + maximum);
        }
        this.minimum = minimum;
        this.maximum = maximum;
    }

    public static MatchRequirement exactly(int count) {
        return new MatchRequirement(count, count);
    }

    public static MatchRequirement between(int minimum, int maximum) {
        return new MatchRequirement(minimum, maximum);
    }

    public boolean accepts(int count) {
        return count >= minimum && count <= maximum;
    }

    @Override
    public String toString() {
        return minimum == maximum ? String.valueOf(minimum) : minimum + ".." + maximum;
    }
}
