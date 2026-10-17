package betamoon.worldgen;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Opt-in engineering counters. The disabled branch is constant-folded by the
 * runtime JIT.
 */
public final class GenerationMetrics {
    public enum Counter {
        SOURCE_FILLS, COMPOSED_FILLS, BASE_CACHE_HITS, BASE_CACHE_MISSES, BASE_CACHE_EVICTIONS, BLOCK_BYTES_COPIED, METADATA_BYTES_COPIED, MATERIAL_SNAPSHOTS, HEIGHTMAP_COLUMNS, PROFILE_CORNER_SELECTIONS, FIELD_SAMPLES, PREPARED_COLUMN_SAMPLES, NOISE_OCTAVES, LATTICE_CORNER_HASHES, NATIVE_CLIMATE_POINT_REQUESTS, OWNED_BLOCK_LIGHTING_CELLS, FEATURE_ATTEMPTS, REGIONAL_PIECES_SERIALIZED, BASE_NANOS, CARVER_NANOS, SURFACE_NANOS, FINALIZATION_NANOS
    }

    private static final boolean ENABLED = Boolean.getBoolean("betamoon.worldgen.metrics");
    private static final ThreadLocal<long[]> VALUES = ThreadLocal.withInitial(() -> new long[Counter.values().length]);

    private GenerationMetrics() {
    }

    public static void record(Counter counter, long amount) {
        if (ENABLED) {
            VALUES.get()[counter.ordinal()] += amount;
        }
    }

    public static void reset() {
        VALUES.remove();
    }

    public static long start() {
        return ENABLED ? System.nanoTime() : 0;
    }

    public static void finish(Counter stage, long started) {
        if (ENABLED) {
            record(stage, System.nanoTime() - started);
        }
    }

    public static Map<String, Long> snapshot() {
        Map<String, Long> result = new LinkedHashMap<String, Long>();
        if (ENABLED) {
            long[] values = VALUES.get();
            for (Counter counter : Counter.values()) {
                result.put(counter.name(), values[counter.ordinal()]);
            }
        }
        return result;
    }
}
