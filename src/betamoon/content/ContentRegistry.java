package betamoon.content;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Central, script-owned mapping between canonical keys and runtime content.
 * Reverse lookups use object identity so mutable runtime objects remain safe.
 */
public final class ContentRegistry {
    private final Map<ContentKey, ContentRegistration> registrations = new LinkedHashMap<ContentKey, ContentRegistration>();
    private final IdentityHashMap<Object, ContentRegistration> reverse = new IdentityHashMap<Object, ContentRegistration>();
    private final Map<String, Long> ownerRevisions = new LinkedHashMap<String, Long>();
    private long nextRevision;
    private long epoch;

    /** Begins a complete replacement of one ordinary script's declarations. */
    public synchronized Batch begin(String owner) {
        return begin(owner, NamespaceAccess.USER);
    }

    /** Begins declarations trusted to publish built-in namespace catalogs. */
    public synchronized Batch beginBuiltin(String owner) {
        return begin(owner, NamespaceAccess.BUILTIN);
    }

    /** Begins declarations belonging to BetaMoon's distributed examples. */
    public synchronized Batch beginBundledExample(String owner) {
        return begin(owner, NamespaceAccess.BUNDLED_EXAMPLE);
    }

    private Batch begin(String owner, NamespaceAccess namespaceAccess) {
        requireOwner(owner);
        return new Batch(owner, namespaceAccess, epoch, revisionOf(owner));
    }

    /** Returns an active registration, or {@code null}. */
    public synchronized ContentRegistration find(ContentKey key) {
        if (key == null) {
            throw new NullPointerException("Content key");
        }
        return registrations.get(key);
    }

    /** Returns the content bound to a key, or {@code null}. */
    public synchronized Object resolve(ContentKey key) {
        ContentRegistration registration = find(key);
        return registration == null ? null : registration.getContent();
    }

    /** Returns the key bound to this exact object instance, or {@code null}. */
    public synchronized ContentKey keyOf(Object content) {
        if (content == null) {
            throw new NullPointerException("Content object");
        }
        ContentRegistration registration = reverse.get(content);
        return registration == null ? null : registration.getKey();
    }

    /** Returns an immutable point-in-time snapshot in publication order. */
    public synchronized List<ContentRegistration> snapshot() {
        return Collections.unmodifiableList(new ArrayList<ContentRegistration>(registrations.values()));
    }

    private synchronized Publication publish(Batch batch) {
        batch.requireCurrent(epoch, revisionOf(batch.owner));
        validate(batch);

        Map<ContentKey, ContentRegistration> next = new LinkedHashMap<ContentKey, ContentRegistration>(registrations);
        removeOwner(next, batch.owner);

        long revision = ++nextRevision;
        for (PendingBinding binding : batch.bindings.values()) {
            next.put(binding.key,
                    new ContentRegistration(binding.key, binding.type, binding.content, batch.owner, revision));
        }

        IdentityHashMap<Object, ContentRegistration> nextReverse = buildReverse(next);
        registrations.clear();
        registrations.putAll(next);
        reverse.clear();
        reverse.putAll(nextReverse);
        ownerRevisions.put(batch.owner, Long.valueOf(revision));
        return new Publication(batch.owner, epoch, revision);
    }

    private void validate(Batch batch) {
        for (PendingBinding binding : batch.bindings.values()) {
            ContentRegistration keyed = registrations.get(binding.key);
            if (keyed != null && !batch.owner.equals(keyed.getOwner())) {
                throw conflict(ContentRegistryException.Reason.DUPLICATE_KEY, binding.key, keyed.getKey(), batch.owner,
                        keyed.getOwner(), "Content key '" + binding.key + "' belongs to script '" + keyed.getOwner()
                                + "', not '" + batch.owner + "'");
            }

            ContentRegistration reversed = reverse.get(binding.content);
            if (reversed != null && !binding.key.equals(reversed.getKey())) {
                throw conflict(ContentRegistryException.Reason.DUPLICATE_CONTENT, binding.key, reversed.getKey(),
                        batch.owner, reversed.getOwner(),
                        "Content object is already bound to key '" + reversed.getKey() + "'");
            }
        }
    }

    private synchronized void release(Publication publication) {
        if (publication.epoch != epoch || revisionOf(publication.owner) != publication.revision) {
            return;
        }

        removeOwner(registrations, publication.owner);
        rebuildReverse();
        ownerRevisions.put(publication.owner, Long.valueOf(++nextRevision));
    }

    /** Removes all active state and invalidates batches and publications. */
    public synchronized void clear() {
        registrations.clear();
        reverse.clear();
        ownerRevisions.clear();
        nextRevision = 0L;
        epoch++;
    }

    private void rebuildReverse() {
        reverse.clear();
        reverse.putAll(buildReverse(registrations));
    }

    private static IdentityHashMap<Object, ContentRegistration> buildReverse(
            Map<ContentKey, ContentRegistration> source) {
        IdentityHashMap<Object, ContentRegistration> result = new IdentityHashMap<Object, ContentRegistration>();
        for (ContentRegistration registration : source.values()) {
            ContentRegistration previous = result.put(registration.getContent(), registration);
            if (previous != null) {
                throw new IllegalStateException("Content object is bound to both '" + previous.getKey() + "' and '"
                        + registration.getKey() + "'");
            }
        }
        return result;
    }

    private static void removeOwner(Map<ContentKey, ContentRegistration> source, String owner) {
        Iterator<ContentRegistration> entries = source.values().iterator();
        while (entries.hasNext()) {
            if (owner.equals(entries.next().getOwner())) {
                entries.remove();
            }
        }
    }

    private long revisionOf(String owner) {
        Long revision = ownerRevisions.get(owner);
        return revision == null ? 0L : revision.longValue();
    }

    private static void requireOwner(String owner) {
        if (owner == null || owner.trim().length() == 0) {
            throw new IllegalArgumentException("Content owner must not be empty");
        }
    }

    private static ContentRegistryException conflict(ContentRegistryException.Reason reason, ContentKey key,
            ContentKey conflictingKey, String owner, String conflictingOwner, String message) {
        return new ContentRegistryException(reason, key, conflictingKey, owner, conflictingOwner, message);
    }

    /** Thread-confined staging area for one script generation. */
    public final class Batch implements AutoCloseable {
        private final String owner;
        private final NamespaceAccess namespaceAccess;
        private final long expectedEpoch;
        private final long expectedRevision;
        private final Map<ContentKey, PendingBinding> bindings = new LinkedHashMap<ContentKey, PendingBinding>();
        private final IdentityHashMap<Object, PendingBinding> reverseBindings = new IdentityHashMap<Object, PendingBinding>();
        private boolean closed;

        private Batch(String owner, NamespaceAccess namespaceAccess, long expectedEpoch, long expectedRevision) {
            this.owner = owner;
            this.namespaceAccess = namespaceAccess;
            this.expectedEpoch = expectedEpoch;
            this.expectedRevision = expectedRevision;
        }

        public void add(ContentKey key, ContentType expectedType, Object content) {
            requireOpen();
            if (key == null) {
                throw new NullPointerException("Content key");
            }
            if (expectedType == null) {
                throw new NullPointerException("Expected content type");
            }
            if (content == null) {
                throw new NullPointerException("Content object");
            }
            if (!expectedType.equals(key.type())) {
                throw conflict(ContentRegistryException.Reason.TYPE_MISMATCH, key, null, owner, null,
                        "Expected content type '" + expectedType + "', found '" + key.type() + "'");
            }
            namespaceAccess.validate(key, owner);

            PendingBinding duplicateKey = bindings.get(key);
            if (duplicateKey != null) {
                throw conflict(ContentRegistryException.Reason.DUPLICATE_KEY, key, duplicateKey.key, owner, owner,
                        "Duplicate content key '" + key + "' in script '" + owner + "'");
            }
            PendingBinding duplicateContent = reverseBindings.get(content);
            if (duplicateContent != null) {
                throw conflict(ContentRegistryException.Reason.DUPLICATE_CONTENT, key, duplicateContent.key, owner,
                        owner, "Content object is already staged for key '" + duplicateContent.key + "'");
            }

            PendingBinding binding = new PendingBinding(key, expectedType, content);
            bindings.put(key, binding);
            reverseBindings.put(content, binding);
        }

        public Publication commit() {
            requireOpen();
            try {
                return publish(this);
            } finally {
                close();
            }
        }

        private void requireCurrent(long currentEpoch, long currentRevision) {
            if (expectedEpoch != currentEpoch || expectedRevision != currentRevision) {
                throw new IllegalStateException("Content declarations changed while staging script '" + owner + "'");
            }
        }

        private void requireOpen() {
            if (closed) {
                throw new IllegalStateException("Content declaration batch for '" + owner + "' is closed");
            }
        }

        @Override
        public void close() {
            bindings.clear();
            reverseBindings.clear();
            closed = true;
        }
    }

    /** Generation-specific release token; stale cleanup cannot remove a reload. */
    public final class Publication implements AutoCloseable {
        private final String owner;
        private final long epoch;
        private final long revision;

        private Publication(String owner, long epoch, long revision) {
            this.owner = owner;
            this.epoch = epoch;
            this.revision = revision;
        }

        @Override
        public void close() {
            release(this);
        }
    }

    private static final class PendingBinding {
        private final ContentKey key;
        private final ContentType type;
        private final Object content;

        private PendingBinding(ContentKey key, ContentType type, Object content) {
            this.key = key;
            this.type = type;
            this.content = content;
        }
    }

    private enum NamespaceAccess {
        USER {
            @Override
            void validate(ContentKey key, String owner) {
                if (ContentNamespaces.isReserved(key.namespace())) {
                    throw reserved(key, owner);
                }
            }
        },
        BUILTIN {
            @Override
            void validate(ContentKey key, String owner) {
            }
        },
        BUNDLED_EXAMPLE {
            @Override
            void validate(ContentKey key, String owner) {
                if (ContentNamespaces.isReserved(key.namespace())
                        && !ContentNamespaces.EXAMPLE.equals(key.namespace())) {
                    throw reserved(key, owner);
                }
            }
        };

        abstract void validate(ContentKey key, String owner);

        static ContentRegistryException reserved(ContentKey key, String owner) {
            return conflict(ContentRegistryException.Reason.RESERVED_NAMESPACE, key, null, owner, null,
                    "Content namespace '" + key.namespace() + "' is reserved");
        }
    }
}
