package betamoon.worldgen;

/** Central safety limits for declarations executed during chunk generation. */
public final class WorldGenLimits {
    public static final int MIN_HEIGHT = 0;
    public static final int MAX_HEIGHT = 127;
    public static final int MAX_ATTEMPTS_PER_CHUNK = 256;
    public static final int MAX_ORE_VEIN_SIZE = 128;
    public static final int MAX_BIOME_FILTERS = 64;
    public static final int MAX_BLOCK_CHANGES_PER_FEATURE = 8192;
    public static final int MAX_FEATURE_RADIUS = 32;
    public static final int MAX_REGIONAL_SPACING = 512;
    public static final int MAX_REGIONAL_DEPTH = 16;
    public static final int MAX_REGIONAL_PIECES = 128;
    public static final int MAX_REGIONAL_DISTANCE = 512;
    public static final int MAX_REGIONAL_BLOCKS_PER_CHUNK = 32768;
    public static final int MAX_TERRAIN_READS_PER_FEATURE = 1048576;
    public static final int MAX_REGIONAL_TERRAIN_CHANGES = 32768;

    private WorldGenLimits() {
    }
}
