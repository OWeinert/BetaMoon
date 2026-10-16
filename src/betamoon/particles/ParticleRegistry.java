package betamoon.particles;

import betamoon.content.ContentKey;
import betamoon.content.ContentRegistry;
import betamoon.luamodloader.LuaScriptRegistry;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Atomic owner generations survive failed initialization and are removed when
 * their package disappears.
 */
public final class ParticleRegistry {
    private static final ContentRegistry CONTENT = new ContentRegistry();
    private static final ThreadLocal<Batch> CURRENT = new ThreadLocal<Batch>();
    private static final Map<String, Generation> GENERATIONS = new LinkedHashMap<String, Generation>();
    private static Consumer<String> cleanup = owner -> {
    };

    private ParticleRegistry() {
    }

    public static void setCleanup(Consumer<String> listener) {
        cleanup = listener;
    }

    public static Batch begin(String owner) {
        if (CURRENT.get() != null) {
            throw new IllegalStateException("A particle declaration scope is already active");
        }
        Batch batch = new Batch(owner);
        CURRENT.set(batch);
        return batch;
    }

    public static Object find(ContentKey key) {
        Batch batch = CURRENT.get();
        if (batch != null && batch.owner.equals(LuaScriptRegistry.getCurrentScriptFile())) {
            Object pending = batch.pending.get(key);
            if (pending != null) {
                return pending;
            }
        }
        return CONTENT.resolve(key);
    }

    public static ParticleDefinition particle(ContentKey key) {
        Object found = find(key);
        return found instanceof ParticleDefinition ? (ParticleDefinition) found : null;
    }

    public static void stage(ContentKey key, Object definition, AutoCloseable resource) {
        Batch batch = CURRENT.get();
        if (batch == null || batch.published || !batch.owner.equals(LuaScriptRegistry.getCurrentScriptFile())) {
            closeResource(resource);
            throw new IllegalStateException("Particle declarations require modInit or modReload");
        }
        try {
            batch.content.add(key, key.type(), definition);
            batch.pending.put(key, definition);
            if (resource != null) {
                batch.resources.add(resource);
            }
        } catch (RuntimeException error) {
            closeResource(resource);
            throw error;
        }
    }

    public static void retainOwners(Set<String> owners) {
        for (String owner : new ArrayList<String>(GENERATIONS.keySet())) {
            if (!owners.contains(owner)) {
                cleanup.accept(owner);
                ParticleDiagnostics.clearOwner(owner);
                GENERATIONS.remove(owner).close();
            }
        }
    }

    public static List<Object> snapshot() {
        List<Object> result = new ArrayList<Object>();
        for (Generation generation : GENERATIONS.values()) {
            result.addAll(generation.definitions.values());
        }
        return result;
    }

    private static void closeResource(AutoCloseable resource) {
        if (resource != null) {
            try {
                resource.close();
            } catch (Exception error) {
                throw new IllegalStateException("Particle resource cleanup failed", error);
            }
        }
    }

    public static final class Batch implements AutoCloseable {
        private final String owner;
        private final ContentRegistry.Batch content;
        private final Map<ContentKey, Object> pending = new LinkedHashMap<ContentKey, Object>();
        private final List<AutoCloseable> resources = new ArrayList<AutoCloseable>();
        private boolean published;

        private Batch(String owner) {
            this.owner = owner;
            content = CONTENT.beginBundledExample(owner);
        }

        public void publish() {
            ContentRegistry.Publication publication = content.commit();
            Generation previous = GENERATIONS.put(owner, new Generation(publication, pending, resources));
            published = true;
            cleanup.accept(owner);
            ParticleDiagnostics.clearOwner(owner);
            if (previous != null) {
                previous.close();
            }
        }

        @Override
        public void close() {
            content.close();
            CURRENT.remove();
            if (!published) {
                for (AutoCloseable resource : resources) {
                    closeResource(resource);
                }
            }
        }
    }

    private static final class Generation implements AutoCloseable {
        private final ContentRegistry.Publication publication;
        private final Map<ContentKey, Object> definitions;
        private final List<AutoCloseable> resources;

        private Generation(ContentRegistry.Publication publication, Map<ContentKey, Object> definitions,
                List<AutoCloseable> resources) {
            this.publication = publication;
            this.definitions = new LinkedHashMap<ContentKey, Object>(definitions);
            this.resources = new ArrayList<AutoCloseable>(resources);
        }

        @Override
        public void close() {
            publication.close();
            for (AutoCloseable resource : resources) {
                closeResource(resource);
            }
        }
    }
}
