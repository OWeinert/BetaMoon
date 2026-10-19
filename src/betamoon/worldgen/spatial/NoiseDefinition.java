package betamoon.worldgen.spatial;

import betamoon.worldgen.GenerationMetrics;
import betamoon.worldgen.SeedMixer;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.DoubleUnaryOperator;
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
    private final double[] scales;
    private final double normalization;
    private final ThreadLocal<Sampling> sampling = new ThreadLocal<Sampling>();

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
        scales = new double[values.size()];
        double total = 0;
        for (int i = 0; i < amplitudes.length; i++) {
            amplitudes[i] = number(values.get(i), key + ".amplitudes[" + (i + 1) + "]");
            scales[i] = Math.scalb(1.0, firstOctave + i);
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
        GenerationMetrics.record(GenerationMetrics.Counter.NOISE_OCTAVES, amplitudes.length);
        Sampling prepared = prepare(worldSeed);
        double sum = 0;
        for (int i = 0; i < amplitudes.length; i++) {
            double scale = scales[i];
            double period = prepared.nativeNoise == null ? 0x1.0p48 : 256;
            double sx = (x * scale) % period;
            double sy = (y * scale) % period;
            double sz = (z * scale) % period;
            long octaveSeed = prepared.seeds[i];
            double basis;
            if (prepared.nativeNoise != null) {
                basis = prepared.nativeNoise.octaves[i].generateNoise(sx, sy, sz);
            } else if (algorithm.equals("simplex_2d_v1")) {
                basis = simplex(octaveSeed, sx, sz);
            } else {
                basis = lattice(octaveSeed, sx, sy, sz, algorithm.equals("perlin_v1"), prepared.cells[i]);
            }
            sum += amplitudes[i] * Math.max(-1, Math.min(1, basis));
        }
        return sum / normalization;
    }

    private Sampling prepare(long worldSeed) {
        Sampling prepared = sampling.get();
        if (prepared == null || prepared.worldSeed != worldSeed) {
            prepared = new Sampling(worldSeed, SeedMixer.derive(worldSeed, salt), amplitudes.length,
                    algorithm.equals("beta_octaves_v1"));
            sampling.set(prepared);
        }
        return prepared;
    }

    public DoubleUnaryOperator prepareColumn(long worldSeed, double x, double z) {
        Sampling prepared = prepare(worldSeed);
        ColumnOctave[] columns = new ColumnOctave[amplitudes.length];
        for (int i = 0; i < amplitudes.length; i++) {
            double scale = scales[i];
            double period = prepared.nativeNoise == null ? 0x1.0p48 : 256;
            double sx = (x * scale) % period;
            double sz = (z * scale) % period;
            columns[i] = new ColumnOctave(prepared.seeds[i], sx, sz, algorithm.equals("perlin_v1"));
        }
        return y -> {
            GenerationMetrics.record(GenerationMetrics.Counter.NOISE_OCTAVES, amplitudes.length);
            double sum = 0;
            for (int i = 0; i < amplitudes.length; i++) {
                ColumnOctave column = columns[i];
                double sy = (y * scales[i]) % (prepared.nativeNoise == null ? 0x1.0p48 : 256);
                double basis;
                if (prepared.nativeNoise != null) {
                    basis = prepared.nativeNoise.octaves[i].generateNoise(column.x, sy, column.z);
                } else if (algorithm.equals("simplex_2d_v1")) {
                    basis = simplex(prepared.seeds[i], column.x, column.z);
                } else {
                    basis = column.sample(sy);
                }
                sum += amplitudes[i] * Math.max(-1, Math.min(1, basis));
            }
            return sum / normalization;
        };
    }

    public static double unit(long seed, long x, long y, long z) {
        long value = SeedMixer.derive(SeedMixer.derive(SeedMixer.derive(seed, x), y), z);
        return (value >>> 11) * 0x1.0p-53;
    }

    private static double lattice(long seed, double x, double y, double z, boolean perlin, LatticeCell cell) {
        long ix = (long) Math.floor(x);
        long iy = (long) Math.floor(y);
        long iz = (long) Math.floor(z);
        double fx = x - ix;
        double fy = y - iy;
        double fz = z - iz;
        if (!cell.valid || cell.x != ix || cell.y != iy || cell.z != iz) {
            GenerationMetrics.record(GenerationMetrics.Counter.LATTICE_CORNER_HASHES, 8);
            int index = 0;
            for (int dx = 0; dx < 2; dx++) {
                for (int dy = 0; dy < 2; dy++) {
                    for (int dz = 0; dz < 2; dz++) {
                        double random = unit(seed, ix + dx, iy + dy, iz + dz);
                        cell.corners[index++] = perlin ? (int) (random * 16) : random * 2 - 1;
                    }
                }
            }
            cell.x = ix;
            cell.y = iy;
            cell.z = iz;
            cell.valid = true;
        }
        double fadeX = fade(fx);
        double fadeY = fade(fy);
        double fadeZ = fade(fz);
        return weightedCorners(cell.corners, fx, fy, fz, fadeX, fadeY, fadeZ, perlin);
    }

    // Explicit terms preserve the versioned eight-corner summation order while
    // removing loop/axis dispatch from the dominant exact-noise inner loop.
    private static double weightedCorners(double[] corners, double x, double y, double z, double fadeX, double fadeY,
            double fadeZ, boolean perlin) {
        double oppositeX = 1 - fadeX;
        double oppositeY = 1 - fadeY;
        double oppositeZ = 1 - fadeZ;
        double sum = 0;
        sum += cornerValue(corners[0], x, y, z, perlin) * oppositeX * oppositeY * oppositeZ;
        sum += cornerValue(corners[1], x, y, z - 1, perlin) * oppositeX * oppositeY * fadeZ;
        sum += cornerValue(corners[2], x, y - 1, z, perlin) * oppositeX * fadeY * oppositeZ;
        sum += cornerValue(corners[3], x, y - 1, z - 1, perlin) * oppositeX * fadeY * fadeZ;
        sum += cornerValue(corners[4], x - 1, y, z, perlin) * fadeX * oppositeY * oppositeZ;
        sum += cornerValue(corners[5], x - 1, y, z - 1, perlin) * fadeX * oppositeY * fadeZ;
        sum += cornerValue(corners[6], x - 1, y - 1, z, perlin) * fadeX * fadeY * oppositeZ;
        sum += cornerValue(corners[7], x - 1, y - 1, z - 1, perlin) * fadeX * fadeY * fadeZ;
        return sum;
    }

    private static double cornerValue(double value, double x, double y, double z, boolean perlin) {
        return perlin ? gradient((int) value, x, y, z) : value;
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

    private static final class Sampling {
        private final long worldSeed;
        private final long[] seeds;
        private final LatticeCell[] cells;
        private final NativeNoise nativeNoise;

        private Sampling(long worldSeed, long seed, int count, boolean nativeBasis) {
            this.worldSeed = worldSeed;
            seeds = new long[count];
            cells = new LatticeCell[count];
            for (int index = 0; index < count; index++) {
                seeds[index] = SeedMixer.derive(seed, index);
                cells[index] = new LatticeCell();
            }
            nativeNoise = nativeBasis ? new NativeNoise(seed, count) : null;
        }
    }

    private static final class LatticeCell {
        private final double[] corners = new double[8];
        private long x;
        private long y;
        private long z;
        private boolean valid;
    }

    private static final class ColumnOctave {
        private final long seed;
        private final double x;
        private final double z;
        private final long ix;
        private final long iz;
        private final double fx;
        private final double fz;
        private final double fadeX;
        private final double fadeZ;
        private final boolean perlin;
        private final LatticeCell cell = new LatticeCell();

        private ColumnOctave(long seed, double x, double z, boolean perlin) {
            this.seed = seed;
            this.x = x;
            this.z = z;
            this.perlin = perlin;
            ix = (long) Math.floor(x);
            iz = (long) Math.floor(z);
            fx = x - ix;
            fz = z - iz;
            fadeX = fade(fx);
            fadeZ = fade(fz);
        }

        private double sample(double y) {
            long iy = (long) Math.floor(y);
            if (!cell.valid || cell.y != iy) {
                GenerationMetrics.record(GenerationMetrics.Counter.LATTICE_CORNER_HASHES, 8);
                int index = 0;
                for (int dx = 0; dx < 2; dx++) {
                    for (int dy = 0; dy < 2; dy++) {
                        for (int dz = 0; dz < 2; dz++) {
                            double random = unit(seed, ix + dx, iy + dy, iz + dz);
                            cell.corners[index++] = perlin ? (int) (random * 16) : random * 2 - 1;
                        }
                    }
                }
                cell.y = iy;
                cell.valid = true;
            }
            double fy = y - iy;
            double fadeY = fade(fy);
            return weightedCorners(cell.corners, fx, fy, fz, fadeX, fadeY, fadeZ, perlin);
        }
    }
}
