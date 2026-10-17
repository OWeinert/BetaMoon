package betamoon.worldgen.spatial;

import betamoon.worldgen.GenerationMetrics;
import net.minecraft.src.WorldChunkManager;

/**
 * Bounded exact point samples of native climate; replacement managers keep
 * their own behavior.
 */
public final class NativeClimateSamples {
    private final WorldChunkManager manager;
    private Samples samples;

    public NativeClimateSamples(WorldChunkManager manager) {
        this.manager = manager;
    }

    public double value(String property, int x, int z) {
        if (manager.getClass() != WorldChunkManager.class) {
            GenerationMetrics.record(GenerationMetrics.Counter.NATIVE_CLIMATE_POINT_REQUESTS, 1);
            manager.loadBlockGeneratorData(null, x, z, 1, 1);
            return property.equals("temperature") ? manager.temperature[0] : manager.humidity[0];
        }
        if (samples == null) {
            samples = new Samples();
        }
        int slot = (x * 73428767 + z * 912931) & 511;
        long coordinate = ((long) x << 32) ^ (z & 0xffffffffL);
        if (!samples.valid[slot] || samples.coordinates[slot] != coordinate) {
            GenerationMetrics.record(GenerationMetrics.Counter.NATIVE_CLIMATE_POINT_REQUESTS, 1);
            manager.loadBlockGeneratorData(null, x, z, 1, 1);
            samples.temperatures[slot] = manager.temperature[0];
            samples.humidities[slot] = manager.humidity[0];
            samples.coordinates[slot] = coordinate;
            samples.valid[slot] = true;
        }
        return property.equals("temperature") ? samples.temperatures[slot] : samples.humidities[slot];
    }

    private static final class Samples {
        private final long[] coordinates = new long[512];
        private final boolean[] valid = new boolean[512];
        private final double[] temperatures = new double[512];
        private final double[] humidities = new double[512];
    }
}
