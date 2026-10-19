package betamoon.worldgen.terrain;

import net.minecraft.src.MapGenCaves;

/**
 * Audited native cave pass: its origin callback consumes only RNG, coordinates
 * and the supplied byte buffer.
 */
final class NativeCaveSampler extends MapGenCaves {
    void carve(long seed, int chunkX, int chunkZ, byte[] blocks) {
        rand.setSeed(seed);
        long xSalt = rand.nextLong() / 2L * 2L + 1L;
        long zSalt = rand.nextLong() / 2L * 2L + 1L;
        for (int x = chunkX - field_1306_a; x <= chunkX + field_1306_a; x++) {
            for (int z = chunkZ - field_1306_a; z <= chunkZ + field_1306_a; z++) {
                rand.setSeed((long) x * xSalt + (long) z * zSalt ^ seed);
                super.func_868_a(null, x, z, chunkX, chunkZ, blocks);
            }
        }
    }
}
