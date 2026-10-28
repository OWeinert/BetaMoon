package fixture.hooks;

public class HookOverrideFixture extends HookTargetFixture {
    @Override
    public int calculate(int value, long wide, double fraction, Object object) {
        return super.calculate(value, wide, fraction, object) + 1000;
    }
}
