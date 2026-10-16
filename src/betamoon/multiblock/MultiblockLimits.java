package betamoon.multiblock;

/**
 * Validated engine limits shared by compilation, diagnostics and managed work.
 */
public final class MultiblockLimits {
    private static int requiredCells = 4096;
    private static int visitsPerTick = 65536;
    private static int worldInstances = 4096;
    private static int ownerInstances = 1024;

    private MultiblockLimits() {
    }

    public static void configure(int cells, int visits, int world, int owner) {
        requiredCells = bounded(cells, 1, 32768);
        visitsPerTick = bounded(visits, 256, 1048576);
        worldInstances = bounded(world, 1, 65535);
        ownerInstances = bounded(owner, 1, 65535);
    }

    private static int bounded(int value, int min, int max) {
        if (value < min || value > max) {
            throw new IllegalArgumentException("Multiblock limit outside " + min + ".." + max);
        }
        return value;
    }

    public static int requiredCells() {
        return requiredCells;
    }

    public static int visitsPerTick() {
        return visitsPerTick;
    }

    public static int worldInstances() {
        return worldInstances;
    }

    public static int ownerInstances() {
        return ownerInstances;
    }
}
