package fixture.hooks;

public class ConstructionFixture extends ConstructionBase {
    public static final RuntimeException FAILURE = new IllegalStateException("construction native failure");
    public final int value;
    public int outer;

    public ConstructionFixture() {
        this(2);
        outer = 7;
    }

    public ConstructionFixture(int value) {
        super(value);
        this.value = value;
        if (value < 0) {
            throw FAILURE;
        }
        if (value == 0) {
            return;
        }
        // The event still reports the saved entry argument when a parameter local
        // changes.
        value = 99;
        outer = new StringBuilder().append(value).length();
    }

    public class Inner {
        public final int value;

        public Inner(int value) {
            this.value = value;
        }
    }
}
