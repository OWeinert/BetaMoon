package betamoon.assets;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Owns atomic, script-scoped sets of asset declarations without any client
 * dependencies.
 */
public final class AssetRegistry {
    private final Map<AssetId, AssetRegistration> registrations = new LinkedHashMap<>();
    private final Map<String, Long> ownerRevisions = new LinkedHashMap<>();
    private long nextRevision;

    /**
     * Stages a complete replacement of this owner's declarations, invisible until
     * commit.
     */
    public synchronized Batch begin(String owner) {
        Objects.requireNonNull(owner, "Asset owner");
        if (owner.trim().isEmpty()) {
            throw new IllegalArgumentException("Asset owner must not be empty");
        }
        return new Batch(owner, revisionOf(owner));
    }

    /** Returns the current registration, or null if its owner is not published. */
    public synchronized AssetRegistration find(AssetId id) {
        return registrations.get(Objects.requireNonNull(id, "Asset identity"));
    }

    /**
     * Returns an immutable point-in-time snapshot, in publication/declaration
     * order.
     */
    public synchronized List<AssetRegistration> snapshot() {
        return Collections.unmodifiableList(new ArrayList<>(registrations.values()));
    }

    private long revisionOf(String owner) {
        Long revision = ownerRevisions.get(owner);
        return revision == null ? 0L : revision;
    }

    private synchronized Publication publish(Batch batch) {
        if (revisionOf(batch.owner) != batch.expectedRevision) {
            throw new IllegalStateException("Asset declarations changed while staging script '" + batch.owner + "'");
        }
        for (AssetId id : batch.definitions.keySet()) {
            AssetRegistration current = registrations.get(id);
            if (current != null && !current.getOwner().equals(batch.owner)) {
                throw new IllegalArgumentException(
                        "Asset '" + id + "' belongs to script '" + current.getOwner() + "', not '" + batch.owner + "'");
            }
        }

        long revision = ++nextRevision;
        removeOwner(batch.owner);
        for (AssetDefinition definition : batch.definitions.values()) {
            registrations.put(definition.getId(), new AssetRegistration(definition, batch.owner, revision));
        }
        ownerRevisions.put(batch.owner, revision);
        return new Publication(batch.owner, revision);
    }

    private synchronized void release(Publication publication) {
        if (revisionOf(publication.owner) != publication.revision) {
            return;
        }
        removeOwner(publication.owner);
        // Keep the tombstone so a batch opened before unload cannot resurrect stale
        // declarations.
        ownerRevisions.put(publication.owner, ++nextRevision);
    }

    private void removeOwner(String owner) {
        Iterator<AssetRegistration> entries = registrations.values().iterator();
        while (entries.hasNext()) {
            if (entries.next().getOwner().equals(owner)) {
                entries.remove();
            }
        }
    }

    /**
     * Thread-confined staging area. Closing an uncommitted batch discards its
     * declarations.
     */
    public final class Batch implements AutoCloseable {
        private final String owner;
        private final long expectedRevision;
        private final Map<AssetId, AssetDefinition> definitions = new LinkedHashMap<>();
        private boolean closed;

        private Batch(String owner, long expectedRevision) {
            this.owner = owner;
            this.expectedRevision = expectedRevision;
        }

        public AssetDefinition find(AssetId id) {
            requireOpen();
            return definitions.get(id);
        }

        public void discard(AssetDefinition definition) {
            requireOpen();
            definitions.remove(definition.getId(), definition);
        }

        public void add(AssetDefinition definition) {
            requireOpen();
            Objects.requireNonNull(definition, "Asset definition");
            if (definitions.containsKey(definition.getId())) {
                throw new IllegalArgumentException(
                        "Duplicate asset '" + definition.getId() + "' in script '" + owner + "'");
            }
            definitions.put(definition.getId(), definition);
        }

        /**
         * Publishes the entire batch or leaves the registry unchanged on validation
         * failure.
         */
        public Publication commit() {
            requireOpen();
            try {
                return publish(this);
            } finally {
                close();
            }
        }

        private void requireOpen() {
            if (closed) {
                throw new IllegalStateException("Asset declaration batch for '" + owner + "' is closed");
            }
        }

        @Override
        public void close() {
            definitions.clear();
            closed = true;
        }
    }

    /**
     * Generation-specific release token; old cleanup never removes a newer
     * publication.
     */
    public final class Publication implements AutoCloseable {
        private final String owner;
        private final long revision;

        private Publication(String owner, long revision) {
            this.owner = owner;
            this.revision = revision;
        }

        @Override
        public void close() {
            release(this);
        }
    }
}
