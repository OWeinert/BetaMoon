package betamoon.worldgen.spatial;

import betamoon.worldgen.SeedMixer;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import net.minecraft.src.NoiseGeneratorPerlin;

import static betamoon.worldgen.spatial.GenerationValues.error;
import static betamoon.worldgen.spatial.GenerationValues.fields;
import static betamoon.worldgen.spatial.GenerationValues.integer;
import static betamoon.worldgen.spatial.GenerationValues.list;
import static betamoon.worldgen.spatial.GenerationValues.number;
import static betamoon.worldgen.spatial.GenerationValues.string;

/**
 * Versioned basis samplers with definition-local seeds; no shared world RNG.
 */
public strictfp final class NoiseDefinition {
    private final String algorithm;
    private final long salt;
    private final double[] amplitudes;
    private final int firstOctave;
    private final double normalization;
    private final ThreadLocal<NativeNoise> nativeNoise = new ThreadLocal<NativeNoise>();

    public NoiseDefinition(String key, Map<String, Object> value) {
        fields(value, key, "key", "algorithm", "seedSalt", "firstOctave", "amplitudes", "normalization");
        algorithm = string(value, "algorithm", "value_noise_v1", key);
        if (!algorithm.equals("value_noise_v1") && !algorithm.equals("perlin_v1") && !algorithm.equals("simplex_2d_v1")
                && !algorithm.equals("beta_octaves_v1")) {
            throw error(key + ".algorithm", "unknown versioned noise algorithm");
        }
        salt = SeedMixer.hash(key + ":" + String.valueOf(value.getOrDefault("seedSalt", "0")));
        firstOctave = value.containsKey("firstOctave")
                ? integer(value.get("firstOctave"), key + ".firstOctave", -24, 16)
                : 0;
        List<Object> values = value.containsKey("amplitudes")
                ? list(value.get("amplitudes"), key + ".amplitudes", 1, 16)
                : Collections.<Object>singletonList(1.0);
        amplitudes = new double[values.size()];
        double total = 0;
        for (int i = 0; i < amplitudes.length; i++) {
            amplitudes[i] = number(values.get(i), key + ".amplitudes[" + (i + 1) + "]");
            total += Math.abs(amplitudes[i]);
        }
        if (!(total > 0) || total > 1024) {
            throw error(key + ".amplitudes", "absolute amplitude sum must be in (0,1024]");
        }
        String mode = string(value, "normalization", "bounded", key);
        if (!mode.equals("bounded") && !mode.equals("none")) {
            throw error(key + ".normalization", "expected bounded or none");
        }
        normalization = mode.equals("bounded") ? total : 1;
    }

    public double bound() {
        double total = 0;
        for (double amplitude : amplitudes) {
            total += Math.abs(amplitude);
        }
        return total / normalization;
    }

    public boolean is2D() {
        return algorithm.equals("simplex_2d_v1");
    }

    public double sample(long worldSeed, double x, double y, double z) {
        long seed = SeedMixer.derive(worldSeed, salt);
        double sum = 0;
        NativeNoise cache = null;
        if (algorithm.equals("beta_octaves_v1")) {
            cache = nativeNoise.get();
            if (cache == null || cache.seed != seed) {
                cache = new NativeNoise(seed, amplitudes.length);
                nativeNoise.set(cache);
            }
        }
        for (int i = 0; i < amplitudes.length; i++) {
            double scale = Math.scalb(1.0, firstOctave + i);
            double sx = (x * scale) % (cache == null ? 0x1.0p48 : 256);
            double sy = (y * scale) % (cache == null ? 0x1.0p48 : 256);
            double sz = (z * scale) % (cache == null ? 0x1.0p48 : 256);
            long octaveSeed = SeedMixer.derive(seed, i);
            double basis;
            if (cache != null) {
                basis = cache.octaves[i].generateNoise(sx, sy, sz);
            } else if (algorithm.equals("simplex_2d_v1")) {
                basis = simplex(octaveSeed, sx, sz);
            } else {
                basis = lattice(octaveSeed, sx, sy, sz, algorithm.equals("perlin_v1"));
            }
            sum += amplitudes[i] * Math.max(-1, Math.min(1, basis));
        }
        return sum / normalization;
    }

    public static double unit(long seed, long x, long y, long z) {
        long value = SeedMixer.derive(SeedMixer.derive(SeedMixer.derive(seed, x), y), z);
        return (value >>> 11) * 0x1.0p-53;
    }

    private static double lattice(long seed, double x, double y, double z, boolean perlin) {
        long ix = (long) Math.floor(x);
        long iy = (long) Math.floor(y);
        long iz = (long) Math.floor(z);
        double fx = x - ix;
        double fy = y - iy;
        double fz = z - iz;
        double sum = 0;
        for (int dx = 0; dx < 2; dx++) {
            for (int dy = 0; dy < 2; dy++) {
                for (int dz = 0; dz < 2; dz++) {
                    double value = unit(seed, ix + dx, iy + dy, iz + dz) * 2 - 1;
                    if (perlin) {
                        int gradient = (int) (unit(seed, ix + dx, iy + dy, iz + dz) * 16);
                        value = gradient(gradient, fx - dx, fy - dy, fz - dz);
                    }
                    sum += value * (dx == 0 ? 1 - fade(fx) : fade(fx)) * (dy == 0 ? 1 - fade(fy) : fade(fy))
                            * (dz == 0 ? 1 - fade(fz) : fade(fz));
                }
            }
        }
        return sum;
    }

    private static double gradient(int hash, double x, double y, double z) {
        int h = hash & 15;
        double u = h < 8 ? x : y;
        double v = h < 4 ? y : h == 12 || h == 14 ? x : z;
        return ((h & 1) == 0 ? u : -u) + ((h & 2) == 0 ? v : -v);
    }

    private static double fade(double value) {
        return value * value * value * (value * (value * 6 - 15) + 10);
    }

    private static double simplex(long seed, double x, double z) {
        double skew = (x + z) * 0.3660254037844386;
        long i = (long) Math.floor(x + skew);
        long j = (long) Math.floor(z + skew);
        double unskew = (i + j) * 0.21132486540518713;
        double dx = x - (i - unskew);
        double dz = z - (j - unskew);
        int ox = dx > dz ? 1 : 0;
        int oz = dx > dz ? 0 : 1;
        return 70 * (corner(seed, i, j, dx, dz)
                + corner(seed, i + ox, j + oz, dx - ox + 0.21132486540518713, dz - oz + 0.21132486540518713)
                + corner(seed, i + 1, j + 1, dx - 1 + 0.42264973081037427, dz - 1 + 0.42264973081037427));
    }

    private static double corner(long seed, long i, long j, double x, double z) {
        double t = 0.5 - x * x - z * z;
        return t < 0 ? 0 : t * t * t * t * gradient((int) (unit(seed, i, 0, j) * 16), x, 0, z);
    }

    private static final class NativeNoise {
        private final long seed;
        private final NoiseGeneratorPerlin[] octaves;

        private NativeNoise(long seed, int count) {
            this.seed = seed;
            Random random = new Random(seed);
            octaves = new NoiseGeneratorPerlin[count];
            for (int i = 0; i < count; i++) {
                octaves[i] = new NoiseGeneratorPerlin(random);
            }
        }
    }
}
