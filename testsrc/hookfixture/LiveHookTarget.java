package hookfixture;

/**
 * Test-only third-party API, packaged into a separately installed ModLoader
 * mod.
 */
public final class LiveHookTarget {
    public static int earlyBodies;
    public static int choiceBodies;
    public static int invalidBodies;

    public static void beforeOwnerInit() {
        earlyBodies++;
    }

    public static int sum(int value, long wide, double fraction) {
        return value + (int) wide + (int) fraction;
    }

    public static int choose(int value) {
        choiceBodies++;
        return value * 100;
    }

    public static int invalid(int value) {
        invalidBodies++;
        return value;
    }

    public static int fail() {
        throw new IllegalStateException("hook-fixture-native-failure");
    }

    public static int unprepared() {
        return 123;
    }
}
