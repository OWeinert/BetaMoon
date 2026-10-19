package betamoon.dimension;

import betamoon.worldgen.spatial.GenerationValues;
import betamoon.worldgen.terrain.TerrainRegistry;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Atomic owner publication; references do not allocate IDs or initialize
 * worlds.
 */
public final class DimensionRegistry {
    private static final ThreadLocal<Batch> CURRENT = new ThreadLocal<Batch>();
    private static volatile Catalog active = new Catalog(Collections.<DimensionKey, DimensionDefinition>emptyMap(),
            Collections.<DimensionKey, DimensionGenerationBinding>emptyMap());

    private DimensionRegistry() {
    }

    public static Batch begin(String owner) {
        if (owner == null || owner.isEmpty() || CURRENT.get() != null) {
            throw new IllegalStateException("Dimension publication requires an owner and cannot nest");
        }
        Batch batch = new Batch(owner);
        CURRENT.set(batch);
        return batch;
    }

    public static DimensionKey add(Map<String, Object> value) {
        Batch batch = CURRENT.get();
        if (batch == null) {
            throw GenerationValues.error("dimensions", "register dimensions during modInit/modReload");
        }
        DimensionDefinition definition = new DimensionDefinition(batch.owner, value);
        if (batch.values.containsKey(definition.key)) {
            throw GenerationValues.error(definition.key.toString(), "duplicate dimension declaration");
        }
        batch.values.put(definition.key, definition);
        batch.resolved = null;
        return definition.key;
    }

    public static DimensionDefinition get(DimensionKey key) {
        Batch pending = CURRENT.get();
        return pending != null && pending.values.containsKey(key)
                ? pending.values.get(key)
                : active.definitions.get(key);
    }

    public static Map<DimensionKey, DimensionDefinition> snapshot() {
        return active.definitions;
    }

    public static DimensionGenerationBinding binding(DimensionKey key) {
        return active.bindings.get(key);
    }

    public static synchronized void retainOwners(Set<String> owners) {
        Map<DimensionKey, DimensionDefinition> retained = new TreeMap<DimensionKey, DimensionDefinition>();
        Map<DimensionKey, DimensionGenerationBinding> bindings = new TreeMap<DimensionKey, DimensionGenerationBinding>();
        Catalog previous = active;
        for (DimensionDefinition definition : previous.definitions.values()) {
            if (owners.contains(definition.owner)) {
                retained.put(definition.key, definition);
                bindings.put(definition.key, previous.bindings.get(definition.key));
            }
        }
        active = new Catalog(retained, bindings);
    }

    public static final class Batch implements AutoCloseable {
        private final String owner;
        private final Map<DimensionKey, DimensionDefinition> values = new TreeMap<DimensionKey, DimensionDefinition>();
        private Catalog resolved;

        private Batch(String owner) {
            this.owner = owner;
        }

        public void validate() {
            Map<DimensionKey, DimensionDefinition> next = new TreeMap<DimensionKey, DimensionDefinition>();
            for (DimensionDefinition definition : active.definitions.values()) {
                if (!owner.equals(definition.owner)) {
                    next.put(definition.key, definition);
                }
            }
            for (DimensionDefinition definition : values.values()) {
                if (next.put(definition.key, definition) != null) {
                    throw GenerationValues.error(definition.key.toString(), "dimension belongs to another owner");
                }
            }
            int limit = Integer.getInteger("betamoon.dimensions.maxDeclarations", 256);
            if (limit < 1 || limit > 4096 || next.size() > limit) {
                throw GenerationValues.error("dimensions", "dimension declaration budget exceeded");
            }
            for (DimensionDefinition definition : next.values()) {
                if (!definition.respawnType.equals("local") && !definition.respawnDimension.isNative()
                        && !next.containsKey(definition.respawnDimension)) {
                    throw GenerationValues.error(definition.key.toString(),
                            "missing respawn dimension: " + definition.respawnDimension);
                }
            }
            Map<DimensionKey, DimensionGenerationBinding> bindings = new TreeMap<DimensionKey, DimensionGenerationBinding>();
            for (DimensionDefinition definition : next.values()) {
                bindings.put(definition.key,
                        new DimensionGenerationBinding(definition, TerrainRegistry.pendingSnapshot()));
            }
            resolved = new Catalog(next, bindings);
        }

        public synchronized void publish() {
            if (resolved == null) {
                validate();
            }
            active = resolved;
        }

        @Override
        public void close() {
            CURRENT.remove();
        }
    }

    private static final class Catalog {
        private final Map<DimensionKey, DimensionDefinition> definitions;
        private final Map<DimensionKey, DimensionGenerationBinding> bindings;

        private Catalog(Map<DimensionKey, DimensionDefinition> definitions,
                Map<DimensionKey, DimensionGenerationBinding> bindings) {
            this.definitions = Collections.unmodifiableMap(new TreeMap<DimensionKey, DimensionDefinition>(definitions));
            this.bindings = Collections
                    .unmodifiableMap(new TreeMap<DimensionKey, DimensionGenerationBinding>(bindings));
        }
    }
}
