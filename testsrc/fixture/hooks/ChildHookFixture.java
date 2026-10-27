package fixture.hooks;

public final class ChildHookFixture {
    public static int bodies;

    public static int call(int value) {
        bodies++;
        return value + 1;
    }
}
