package betamoon.worldgen;

/** Stable world-generation seed derivation independent of registration order. */
public final class SeedMixer {
    private static final long FNV_OFFSET = 0xcbf29ce484222325L;
    private static final long FNV_PRIME = 0x100000001b3L;

    private SeedMixer() {
    }

    public static long generationSeed(long worldSeed, String dimensionKey, String stage, int chunkX, int chunkZ,
            WorldGenKey definitionKey, long salt) {
        long mixed = mix(worldSeed ^ 0x6a09e667f3bcc909L);
        mixed = mix(mixed ^ hash(dimensionKey));
        mixed = mix(mixed ^ hash(stage));
        mixed = mix(mixed ^ (((long) chunkX) << 32) ^ (chunkZ & 0xffffffffL));
        mixed = mix(mixed ^ hash(definitionKey.toString()));
        return mix(mixed ^ salt);
    }

    public static long hash(String value) {
        long result = FNV_OFFSET;
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            result ^= character & 0xff;
            result *= FNV_PRIME;
            result ^= character >>> 8;
            result *= FNV_PRIME;
        }
        return mix(result);
    }

    private static long mix(long value) {
        value += 0x9e3779b97f4a7c15L;
        value = (value ^ value >>> 30) * 0xbf58476d1ce4e5b9L;
        value = (value ^ value >>> 27) * 0x94d049bb133111ebL;
        return value ^ value >>> 31;
    }
}
