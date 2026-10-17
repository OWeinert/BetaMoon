package betamoon.worldgen.spatial;

import betamoon.worldgen.BiomeGenRegistry;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.src.BiomeGenBase;

/** Immutable sampling inputs without chunk or Lua access. */
public final class FieldContext {
    public final long seed;
    public final int dimension;
    public final boolean hasOriginalSpawn;
    public final int spawnX;
    public final int spawnZ;
    public final Environment environment;
    public final SpatialRegistry.Snapshot spatial;
    public final FieldTrace trace;
    private Map<Object, Object> samplingData;
    private long samplingRevision = Long.MIN_VALUE;

    public FieldContext(long seed, int dimension) {
        this(seed, dimension, false, 0, 0);
    }

    public FieldContext(long seed, int dimension, boolean hasOriginalSpawn, int spawnX, int spawnZ) {
        this(seed, dimension, hasOriginalSpawn, spawnX, spawnZ, null);
    }

    public FieldContext(long seed, int dimension, boolean hasOriginalSpawn, int spawnX, int spawnZ,
            Environment environment) {
        this(seed, dimension, hasOriginalSpawn, spawnX, spawnZ, environment, null);
    }

    public FieldContext(long seed, int dimension, boolean hasOriginalSpawn, int spawnX, int spawnZ,
            Environment environment, SpatialRegistry.Snapshot spatial) {
        this(seed, dimension, hasOriginalSpawn, spawnX, spawnZ, environment, spatial, null);
    }

    private FieldContext(long seed, int dimension, boolean hasOriginalSpawn, int spawnX, int spawnZ,
            Environment environment, SpatialRegistry.Snapshot spatial, FieldTrace trace) {
        this.trace = trace;
        this.spatial = spatial;
        this.seed = seed;
        this.dimension = dimension;
        this.hasOriginalSpawn = hasOriginalSpawn;
        this.spawnX = spawnX;
        this.spawnZ = spawnZ;
        this.environment = environment;
    }

    public FieldContext withTrace(FieldTrace trace) {
        return new FieldContext(seed, dimension, hasOriginalSpawn, spawnX, spawnZ, environment, spatial, trace);
    }

    /**
     * Request-owned, bounded state for pure samplers; never retained by compiled
     * definitions.
     */
    @SuppressWarnings("unchecked")
    public <T> T samplingData(Object sampler, Supplier<T> factory) {
        if (trace != null) {
            return factory.get();
        }
        long revision = BiomeGenRegistry.terrainRevision();
        if (samplingData == null || samplingRevision != revision) {
            samplingData = new IdentityHashMap<Object, Object>();
            samplingRevision = revision;
        }
        T result = (T) samplingData.get(sampler);
        if (result == null) {
            if (samplingData.size() >= 256) {
                samplingData.clear();
            }
            result = factory.get();
            samplingData.put(sampler, result);
        }
        return result;
    }

    public interface Environment {
        double biomeValue(String property, int x, int z);

        boolean biomeMatches(String selector, int x, int z);

        default BiomeGenBase biome(int x, int z) {
            return BiomeGenBase.plains;
        }
    }
}
