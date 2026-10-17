package betamoon.worldgen.terrain;

import betamoon.worldgen.spatial.FieldContext;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One bounded request cache shared by composed and sampled-density height
 * consumers.
 */
final class TerrainHeightPreview {
    private static final Object CACHE_KEY = new Object();

    private TerrainHeightPreview() {
    }

    static TerrainHeightmaps heights(TerrainSource source, FieldContext fields, int chunkX, int chunkZ) {
        Cache cache = fields.samplingData(CACHE_KEY, Cache::new);
        Key key = new Key(source, chunkX, chunkZ);
        TerrainHeightmaps heights = cache.values.get(key);
        if (heights == null) {
            ChunkBlockBuffer buffer = new ChunkBlockBuffer();
            source.fill(GenerationContext.preview(fields, chunkX, chunkZ), buffer);
            heights = new TerrainHeightmaps(buffer);
            cache.values.put(key, heights);
        }
        return heights;
    }

    private static final class Cache {
        private final Map<Key, TerrainHeightmaps> values = new LinkedHashMap<Key, TerrainHeightmaps>(16, 0.75f, true) {
            protected boolean removeEldestEntry(Map.Entry<Key, TerrainHeightmaps> eldest) {
                return size() > 16;
            }
        };
    }

    private static final class Key {
        private final TerrainSource source;
        private final int x;
        private final int z;

        private Key(TerrainSource source, int x, int z) {
            this.source = source;
            this.x = x;
            this.z = z;
        }

        public int hashCode() {
            return (System.identityHashCode(source) * 31 + x) * 31 + z;
        }

        public boolean equals(Object other) {
            if (!(other instanceof Key)) {
                return false;
            }
            Key key = (Key) other;
            return source == key.source && x == key.x && z == key.z;
        }
    }
}
