package fixture.hooks;

public final class ConstructionDerived extends ConstructionFixture {
    public int derived = 42;

    public ConstructionDerived() {
        super(4);
    }
}
