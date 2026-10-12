package betamoon.loot;

import betamoon.assets.AssetKey;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Owner-transactional registry for immutable reusable loot tables. */
public final class LootTableRegistry {
    private static final ThreadLocal<PublicationBatch> CURRENT_BATCH = new ThreadLocal<PublicationBatch>();
    private static volatile Map<AssetKey, Registered> active = Collections.emptyMap();

    private LootTableRegistry() {
    }

    public static PublicationBatch beginPublication(String resourceOwner, String owner) {
        if (CURRENT_BATCH.get() != null) {
            throw new IllegalStateException("A loot-table publication is already active");
        }
        PublicationBatch batch = new PublicationBatch(required(resourceOwner), required(owner));
        CURRENT_BATCH.set(batch);
        return batch;
    }

    public static AssetKey add(String declaredKey, LootTableDefinition definition, String source) {
        PublicationBatch batch = CURRENT_BATCH.get();
        if (batch == null) {
            throw new IllegalStateException("Loot tables may only be registered while a Lua package is loading");
        }
        return batch.add(declaredKey, definition, source);
    }

    public static LootTableDefinition find(AssetKey key) {
        PublicationBatch batch = CURRENT_BATCH.get();
        if (batch != null) {
            Registered pending = batch.tables.get(key);
            if (pending != null) {
                return pending.definition;
            }
        }
        Registered registered = active.get(key);
        return registered == null ? null : registered.definition;
    }

    public static String dependencySignature(AssetKey key) {
        LootTableDefinition definition = find(key);
        if (definition == null) {
            throw new IllegalStateException("Unknown loot table " + key);
        }
        return definition.dependencySignature();
    }

    public static synchronized void clear() {
        active = Collections.emptyMap();
    }

    public static synchronized void retainOwners(Set<String> owners) {
        Map<AssetKey, Registered> retained = new LinkedHashMap<AssetKey, Registered>();
        for (Map.Entry<AssetKey, Registered> entry : active.entrySet()) {
            if (owners.contains(entry.getValue().resourceOwner)) {
                retained.put(entry.getKey(), entry.getValue());
            }
        }
        active = Collections.unmodifiableMap(retained);
    }

    public static List<Description> snapshot() {
        List<Description> result = new ArrayList<Description>();
        for (Registered registered : active.values()) {
            result.add(new Description(registered));
        }
        return Collections.unmodifiableList(result);
    }

    public static final class PublicationBatch implements AutoCloseable {
        private final String resourceOwner;
        private final String owner;
        private final Map<AssetKey, Registered> tables = new LinkedHashMap<AssetKey, Registered>();
        private boolean closed;

        private PublicationBatch(String resourceOwner, String owner) {
            this.resourceOwner = resourceOwner;
            this.owner = owner;
        }

        private AssetKey add(String declaredKey, LootTableDefinition definition, String source) {
            ensureOpen();
            AssetKey key;
            try {
                key = AssetKey.parse(declaredKey);
            } catch (IllegalArgumentException error) {
                throw new IllegalArgumentException("LootTable.key: " + error.getMessage());
            }
            if (tables.containsKey(key)) {
                throw new IllegalArgumentException("Duplicate loot table in one package: " + key);
            }
            Registered existing = active.get(key);
            if (existing != null && !existing.resourceOwner.equals(resourceOwner)) {
                throw new IllegalArgumentException("Loot table is already owned by another package: " + key);
            }
            tables.put(key, new Registered(key, resourceOwner, owner, required(source), definition));
            return key;
        }

        public void validate() throws IOException {
            ensureOpen();
            for (Registered registered : tables.values()) {
                Set<AssetKey> visiting = new LinkedHashSet<AssetKey>();
                visiting.add(registered.key);
                try {
                    registered.definition.validateReferences(visiting, 0);
                    registered.definition.validateEmissionBudget(visiting, 0);
                } catch (IOException error) {
                    throw new IOException("Loot table " + registered.key + ": " + error.getMessage(), error);
                }
            }
        }

        public synchronized void publish() {
            ensureOpen();
            Map<AssetKey, Registered> next = new LinkedHashMap<AssetKey, Registered>();
            for (Map.Entry<AssetKey, Registered> entry : active.entrySet()) {
                if (!entry.getValue().resourceOwner.equals(resourceOwner)) {
                    next.put(entry.getKey(), entry.getValue());
                }
            }
            next.putAll(tables);
            active = Collections.unmodifiableMap(next);
        }

        private void ensureOpen() {
            if (closed) {
                throw new IllegalStateException("Loot-table publication is closed");
            }
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            if (CURRENT_BATCH.get() == this) {
                CURRENT_BATCH.remove();
            }
            closed = true;
        }
    }

    private static final class Registered {
        private final AssetKey key;
        private final String resourceOwner;
        private final String owner;
        private final String source;
        private final LootTableDefinition definition;

        private Registered(AssetKey key, String resourceOwner, String owner, String source,
                LootTableDefinition definition) {
            this.key = key;
            this.resourceOwner = resourceOwner;
            this.owner = owner;
            this.source = source;
            this.definition = definition;
        }
    }

    public static final class Description {
        public final String key;
        public final String owner;
        public final String source;
        public final String semanticHash;

        private Description(Registered registered) {
            key = registered.key.toString();
            owner = registered.owner;
            source = registered.source;
            semanticHash = registered.definition.semanticHash();
        }
    }

    private static String required(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Value must not be empty");
        }
        return value;
    }
}
