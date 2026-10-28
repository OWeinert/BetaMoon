package fixture.hooks;

public class MappingParent {
    static {
        System.setProperty("betamoon.mappingParent.initialized", "yes");
    }

    public int a() {
        return 1;
    }

    protected int q() {
        return 2;
    }

    private int p() {
        return 3;
    }

    public static int s() {
        return 4;
    }
}
