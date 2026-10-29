package fixture.hooks;

/**
 * Separately defined by the hook test loader; never initialized by preparation.
 */
public class HookTargetFixture {
    static {
        System.setProperty("betamoon.hookFixture.initialized", "yes");
    }

    public static int bodyCalls;
    public static RuntimeException injectedFailure;
    public static Error injectedFatal;
    public static Exception checkedFailure;

    public int calculate(int value, long wide, double fraction, Object object) {
        bodyCalls++;
        if (value == -3 && injectedFatal != null) {
            throw injectedFatal;
        }
        if (value == -2 && injectedFailure != null) {
            throw injectedFailure;
        }
        if (value < 0) {
            throw new IllegalArgumentException("native-body-failure");
        }
        if (object == null) {
            return value + (int) wide;
        }
        return value + (int) wide + (int) fraction;
    }

    public static long longValue(long value) {
        return value;
    }

    public static double doubleValue(double value) {
        return value;
    }

    public static float floatValue(float value) {
        return value;
    }

    public static boolean booleanValue(boolean value) {
        return value;
    }

    public static byte byteValue(byte value) {
        return value;
    }

    public static char charValue(char value) {
        return value;
    }

    public static short shortValue(short value) {
        return value;
    }

    public static Object objectValue(Object value) {
        return value;
    }

    public static int[] arrayValue(int[] value) {
        return value;
    }

    public static void voidValue() {
        bodyCalls++;
    }

    public static int onlyThrows() {
        throw new IllegalStateException("only-throws");
    }

    public int checked() throws Exception {
        throw checkedFailure;
    }

    public int recursive(int value) {
        bodyCalls++;
        return value == 0 ? 1 : recursive(value - 1) + 1;
    }

    public synchronized int synchronizedMethod() {
        return 1;
    }

    private int privateMethod() {
        return 7;
    }
}
