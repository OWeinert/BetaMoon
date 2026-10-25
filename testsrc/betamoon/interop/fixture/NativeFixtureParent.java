package betamoon.interop.fixture;

public class NativeFixtureParent {
    protected int hidden = 11;
    public Object covariant() {
        return "parent";
    }
    private int parentSecret = 13;

    protected String protectedEcho(int value) {
        return "protected:" + value;
    }
}
