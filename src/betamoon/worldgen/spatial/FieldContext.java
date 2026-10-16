package betamoon.worldgen.spatial;

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

    public interface Environment {
        double biomeValue(String property, int x, int z);

        boolean biomeMatches(String selector, int x, int z);

        default BiomeGenBase biome(int x, int z) {
            return BiomeGenBase.plains;
        }
    }
}
