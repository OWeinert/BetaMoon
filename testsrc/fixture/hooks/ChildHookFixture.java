package fixture.hooks;

public final class ChildHookFixture {
    public static int bodies;
    public static final RuntimeException FAILURE = new IllegalStateException("child native body failure");

    public static int call(int value) {
        bodies++;
        if (value < 0) {
            throw FAILURE;
        }
        return value + 1;
    }
}
