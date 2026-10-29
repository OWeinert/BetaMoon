package fixture.hooks;

public final class EngineHookFixture {
    public static final RuntimeException FAILURE = new IllegalStateException("original-engine-fixture-failure");
    public static int bodies;

    public static int compute(int value) {
        bodies++;
        if (value < 0) {
            throw FAILURE;
        }
        return value + 10;
    }

    public static void cleanup(boolean fail) {
        bodies++;
        if (fail) {
            throw FAILURE;
        }
    }

    public static synchronized int blocked() {
        return 1;
    }
}
