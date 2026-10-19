package betamoon.worldgen.terrain;

import betamoon.worldgen.BiomeGenRegistry;
import betamoon.worldgen.GenerationMetrics;
import betamoon.worldgen.spatial.FieldContext;
import betamoon.worldgen.spatial.SpatialRegistry;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Provider-owned, bounded snapshots of base block IDs before any carving or
 * surface pass.
 */
final class RawTerrainCache {
    private static final int MAX_CHUNKS = 32;
    private static final int MAX_BYTES = 2 * 1024 * 1024;
    private final Map<Long, BaseTerrainSnapshot> chunks = new LinkedHashMap<Long, BaseTerrainSnapshot>(MAX_CHUNKS,
            0.75f, true);
    private int retainedBytes;
    private TerrainSource source;
    private long seed;
    private int dimension;
    private boolean hasOriginalSpawn;
    private int spawnX;
    private int spawnZ;
    private FieldContext.Environment environment;
    private SpatialRegistry.Snapshot spatial;
    private long biomeRevision;

    byte[] get(TerrainSource source, FieldContext fields, int x, int z) {
        if (fields.trace != null) {
            return null;
        }
        selectInputs(source, fields);
        BaseTerrainSnapshot snapshot = chunks.get(key(x, z));
        return snapshot == null ? null : snapshot.blocks;
    }

    /** Takes ownership of an immutable snapshot; callers must never modify it. */
    void put(TerrainSource source, FieldContext fields, int x, int z, byte[] blocks) {
        if (fields.trace != null) {
            return;
        }
        selectInputs(source, fields);
        store(x, z, new BaseTerrainSnapshot(blocks));
    }

    BaseTerrainSnapshot base(TerrainSource source, GenerationContext context, ChunkBlockBuffer buffer) {
        BaseTerrainSnapshot snapshot = null;
        if (context.fields.trace == null) {
            selectInputs(source, context.fields);
            snapshot = chunks.get(key(context.chunkX, context.chunkZ));
        }
        if (snapshot != null && snapshot.matches(context)) {
            GenerationMetrics.record(GenerationMetrics.Counter.BASE_CACHE_HITS, 1);
            snapshot.restore(context, buffer);
            return snapshot;
        }
        GenerationMetrics.record(GenerationMetrics.Counter.BASE_CACHE_MISSES, 1);
        source.fill(context, buffer);
        return capture(source, context, buffer);
    }

    BaseTerrainSnapshot capture(TerrainSource source, GenerationContext context, ChunkBlockBuffer buffer) {
        BaseTerrainSnapshot snapshot = new BaseTerrainSnapshot(context, buffer);
        if (context.fields.trace == null && context.hasReproducibleBiomes()) {
            selectInputs(source, context.fields);
            store(context.chunkX, context.chunkZ, snapshot);
        }
        return snapshot;
    }

    private void store(int x, int z, BaseTerrainSnapshot snapshot) {
        BaseTerrainSnapshot previous = chunks.put(key(x, z), snapshot);
        retainedBytes += snapshot.retainedBytes() - (previous == null ? 0 : previous.retainedBytes());
        Iterator<Map.Entry<Long, BaseTerrainSnapshot>> oldest = chunks.entrySet().iterator();
        while (chunks.size() > MAX_CHUNKS || retainedBytes > MAX_BYTES) {
            retainedBytes -= oldest.next().getValue().retainedBytes();
            oldest.remove();
            GenerationMetrics.record(GenerationMetrics.Counter.BASE_CACHE_EVICTIONS, 1);
        }
    }

    private void selectInputs(TerrainSource next, FieldContext fields) {
        long revision = BiomeGenRegistry.terrainRevision();
        if (source != next || seed != fields.seed || dimension != fields.dimension
                || hasOriginalSpawn != fields.hasOriginalSpawn || spawnX != fields.spawnX || spawnZ != fields.spawnZ
                || environment != fields.environment || spatial != fields.spatial || biomeRevision != revision) {
            chunks.clear();
            retainedBytes = 0;
            source = next;
            seed = fields.seed;
            dimension = fields.dimension;
            hasOriginalSpawn = fields.hasOriginalSpawn;
            spawnX = fields.spawnX;
            spawnZ = fields.spawnZ;
            environment = fields.environment;
            spatial = fields.spatial;
            biomeRevision = revision;
        }
    }

    private static long key(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }
}
