package fixture.hooks;

public class MappingChild extends MappingParent {
    @Override
    public int a() {
        return 5;
    }

    @Override
    protected int q() {
        return 6;
    }

    private int p() {
        return 7;
    }

    public static int s() {
        return 8;
    }
}
