package betamoon.multiblock;

/**
 * Authored north-facing coordinates rotate clockwise; mirroring precedes
 * rotation.
 */
public final class MultiblockOrientation {
    private static final String[] FACINGS = {"north", "east", "south", "west"};

    private MultiblockOrientation() {
    }

    public static int facing(String name) {
        for (int i = 0; i < FACINGS.length; i++) {
            if (FACINGS[i].equals(name)) {
                return i;
            }
        }
        throw new IllegalArgumentException("Expected north, east, south or west: " + name);
    }

    public static String name(int facing) {
        return FACINGS[facing & 3];
    }

    public static int x(int x, int z, int facing, boolean mirror) {
        int right = mirror ? -x : x;
        switch (facing & 3) {
            case 1:
                return -z;
            case 2:
                return -right;
            case 3:
                return z;
            default:
                return right;
        }
    }

    public static int z(int x, int z, int facing, boolean mirror) {
        int right = mirror ? -x : x;
        switch (facing & 3) {
            case 1:
                return right;
            case 2:
                return -z;
            case 3:
                return -right;
            default:
                return z;
        }
    }

    public static String direction(String value, int facing, boolean mirror) {
        int authored = facing(value);
        return name((mirror ? -authored : authored) + facing);
    }
}
