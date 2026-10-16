package betamoon.multiblock;

import betamoon.content.ContentKey;
import betamoon.content.ContentRegistry;
import betamoon.luamodloader.LuaScriptRegistry;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Owner-scoped publication keeps the last working definitions after a failed
 * reload.
 */
public final class MultiblockRegistry {
    private static final ContentRegistry CONTENT = new ContentRegistry();
    private static final ThreadLocal<Batch> CURRENT = new ThreadLocal<Batch>();
    private static final Map<String, Generation> OWNERS = new LinkedHashMap<String, Generation>();

    private MultiblockRegistry() {
    }

    public static Batch begin(String owner) {
        if (CURRENT.get() != null) {
            throw new IllegalStateException("A multiblock declaration scope is already active");
        }
        Batch batch = new Batch(owner);
        CURRENT.set(batch);
        return batch;
    }

    public static void stage(MultiblockDefinition definition) {
        Batch batch = CURRENT.get();
        if (batch == null || batch.published || !batch.owner.equals(LuaScriptRegistry.getCurrentScriptFile())) {
            throw new IllegalStateException("Multiblock declarations require modInit or modReload");
        }
        if (batch.pending.size() >= 512) {
            throw new IllegalArgumentException("At most 512 multiblock definitions per owner");
        }
        batch.content.add(definition.key, definition.key.type(), definition);
        batch.pending.put(definition.key, definition);
    }

    public static MultiblockDefinition find(ContentKey key) {
        Batch batch = CURRENT.get();
        if (batch != null && batch.owner.equals(LuaScriptRegistry.getCurrentScriptFile())
                && batch.pending.containsKey(key)) {
            return batch.pending.get(key);
        }
        return (MultiblockDefinition) CONTENT.resolve(key);
    }

    public static List<MultiblockDefinition> all() {
        List<MultiblockDefinition> result = new ArrayList<MultiblockDefinition>();
        for (Generation generation : OWNERS.values()) {
            result.addAll(generation.definitions.values());
        }
        return result;
    }

    public static void retainOwners(Set<String> owners) {
        boolean changed = false;
        for (String owner : new ArrayList<String>(OWNERS.keySet())) {
            if (!owners.contains(owner)) {
                OWNERS.remove(owner).publication.close();
                changed = true;
            }
        }
        if (changed) {
            MultiblockRuntime.definitionsChanged();
        }
    }

    public static final class Batch implements AutoCloseable {
        private final String owner;
        private final ContentRegistry.Batch content;
        private final Map<ContentKey, MultiblockDefinition> pending = new LinkedHashMap<>();
        private boolean published;

        private Batch(String owner) {
            this.owner = owner;
            content = CONTENT.beginBundledExample(owner);
        }

        public void validate() {
            Map<Integer, ContentKey> controllers = new LinkedHashMap<>();
            List<MultiblockDefinition> proposed = new ArrayList<>();
            for (MultiblockDefinition definition : all()) {
                if (!owner.equals(definition.owner)) {
                    proposed.add(definition);
                }
            }
            proposed.addAll(pending.values());
            if (proposed.size() > 4096) {
                throw new IllegalArgumentException("At most 4096 multiblock definitions globally");
            }
            for (MultiblockDefinition definition : proposed) {
                if (definition.controller >= 0) {
                    ContentKey previous = controllers.put(definition.controller, definition.key);
                    if (previous != null) {
                        throw new IllegalArgumentException("Controller " + definition.controller + " is bound to both "
                                + previous + " and " + definition.key);
                    }
                }
            }
        }

        public void publish() {
            validate();
            ContentRegistry.Publication publication = content.commit();
            Generation previous = OWNERS.put(owner, new Generation(publication, pending));
            published = true;
            if (previous != null) {
                previous.publication.close();
            }
            MultiblockRuntime.definitionsChanged();
        }

        public void close() {
            content.close();
            CURRENT.remove();
        }
    }

    private static final class Generation {
        private final ContentRegistry.Publication publication;
        private final Map<ContentKey, MultiblockDefinition> definitions;

        private Generation(ContentRegistry.Publication publication, Map<ContentKey, MultiblockDefinition> definitions) {
            this.publication = publication;
            this.definitions = new LinkedHashMap<>(definitions);
        }
    }
}
