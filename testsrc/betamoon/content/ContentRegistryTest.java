package betamoon.content;

import java.util.List;

/** Verifies central forward/reverse identity, ownership, and publication. */
public final class ContentRegistryTest {
    private ContentRegistryTest() {
    }

    public static void main(String[] arguments) {
        verifyMappingsAndOwnership();
        verifyAtomicOwnerReplacement();
        verifyConflicts();
        verifyNamespaceReservations();
        verifyClearInvalidation();
        System.out.println("Central content-registry mapping checks passed.");
    }

    private static void verifyMappingsAndOwnership() {
        ContentRegistry registry = new ContentRegistry();
        ContentKey blockKey = key("alpha:block/copper");
        ContentKey itemKey = key("alpha:item/copper");
        ContentKey otherNamespace = key("beta:block/copper");
        EqualContent block = new EqualContent("copper");
        EqualContent item = new EqualContent("copper");
        Object other = new Object();

        try (ContentRegistry.Batch batch = registry.begin("scripts/copper.lua")) {
            batch.add(blockKey, ContentType.BLOCK, block);
            batch.add(itemKey, ContentType.ITEM, item);
            batch.add(otherNamespace, ContentType.BLOCK, other);
            batch.commit();
        }

        require(registry.resolve(blockKey) == block, "Forward lookup returns the exact content object");
        require(registry.keyOf(block).equals(blockKey), "Reverse lookup returns the canonical key");
        require(registry.keyOf(item).equals(itemKey), "Equal but distinct objects have independent bindings");
        require(registry.find(blockKey).getType().equals(ContentType.BLOCK), "Registrations retain expected type");
        require(registry.find(blockKey).getOwner().equals("scripts/copper.lua"), "Registrations retain script owner");
        require(registry.snapshot().size() == 3, "Types and namespaces have independent key spaces");
        expectFailure(UnsupportedOperationException.class, () -> registry.snapshot().clear());
    }

    private static void verifyAtomicOwnerReplacement() {
        ContentRegistry registry = new ContentRegistry();
        ContentKey firstKey = key("mymod:block/first");
        ContentKey omittedKey = key("mymod:item/omitted");
        Object first = new Object();
        Object omitted = new Object();
        ContentRegistry.Publication original;
        try (ContentRegistry.Batch batch = registry.begin("scripts/content.lua")) {
            batch.add(firstKey, ContentType.BLOCK, first);
            batch.add(omittedKey, ContentType.ITEM, omitted);
            original = batch.commit();
        }

        Object replacement = new Object();
        ContentRegistry.Publication current;
        try (ContentRegistry.Batch batch = registry.begin("scripts/content.lua")) {
            batch.add(firstKey, ContentType.BLOCK, replacement);
            current = batch.commit();
        }
        require(registry.resolve(firstKey) == replacement, "Owner reload replaces content atomically");
        require(registry.find(omittedKey) == null, "Owner reload removes omitted declarations");
        require(registry.keyOf(first) == null && registry.keyOf(omitted) == null,
                "Replacement removes stale reverse mappings");

        original.close();
        require(registry.resolve(firstKey) == replacement, "Old publication cleanup cannot remove a reload");
        current.close();
        require(registry.snapshot().isEmpty(), "Current publication cleanup releases its owner");
        current.close();
        require(registry.snapshot().isEmpty(), "Publication cleanup is idempotent");
    }

    private static void verifyConflicts() {
        ContentRegistry registry = new ContentRegistry();
        ContentKey key = key("mymod:block/shared");
        Object content = new Object();
        publish(registry, "first.lua", key, ContentType.BLOCK, content);

        ContentRegistryException duplicateKey = expectRegistry(ContentRegistryException.Reason.DUPLICATE_KEY, () -> {
            try (ContentRegistry.Batch batch = registry.begin("second.lua")) {
                batch.add(key, ContentType.BLOCK, new Object());
                batch.commit();
            }
        });
        require("first.lua".equals(duplicateKey.getConflictingOwner()), "Key conflicts identify the current owner");
        require(registry.resolve(key) == content, "A failed key conflict preserves active state");

        ContentKey otherKey = key("mymod:block/other");
        expectRegistry(ContentRegistryException.Reason.DUPLICATE_CONTENT, () -> {
            try (ContentRegistry.Batch batch = registry.begin("second.lua")) {
                batch.add(otherKey, ContentType.BLOCK, content);
                batch.commit();
            }
        });
        require(registry.find(otherKey) == null, "A failed reverse conflict publishes nothing");

        expectRegistry(ContentRegistryException.Reason.TYPE_MISMATCH, () -> {
            try (ContentRegistry.Batch batch = registry.begin("second.lua")) {
                batch.add(otherKey, ContentType.ITEM, new Object());
            }
        });

        try (ContentRegistry.Batch batch = registry.begin("second.lua")) {
            batch.add(otherKey, ContentType.BLOCK, new Object());
            expectRegistry(ContentRegistryException.Reason.DUPLICATE_KEY,
                    () -> batch.add(otherKey, ContentType.BLOCK, new Object()));
        }

        Object staged = new Object();
        try (ContentRegistry.Batch batch = registry.begin("second.lua")) {
            batch.add(otherKey, ContentType.BLOCK, staged);
            expectRegistry(ContentRegistryException.Reason.DUPLICATE_CONTENT,
                    () -> batch.add(key("mymod:item/other"), ContentType.ITEM, staged));
        }
    }

    private static void verifyNamespaceReservations() {
        ContentRegistry registry = new ContentRegistry();
        expectRegistry(ContentRegistryException.Reason.RESERVED_NAMESPACE, () -> {
            try (ContentRegistry.Batch batch = registry.begin("user.lua")) {
                batch.add(key("minecraft:block/stone"), ContentType.BLOCK, new Object());
            }
        });
        expectRegistry(ContentRegistryException.Reason.RESERVED_NAMESPACE, () -> {
            try (ContentRegistry.Batch batch = registry.begin("user.lua")) {
                batch.add(key("example:block/demo"), ContentType.BLOCK, new Object());
            }
        });

        try (ContentRegistry.Batch batch = registry.beginBuiltin("internal:catalog")) {
            batch.add(key("minecraft:block/stone"), ContentType.BLOCK, new Object());
            batch.add(key("betamoon:shared/missing"), ContentType.SHARED, new Object());
            batch.commit();
        }
        try (ContentRegistry.Batch batch = registry.beginBundledExample("examples/demo/main.lua")) {
            batch.add(key("example:block/demo"), ContentType.BLOCK, new Object());
            batch.commit();
        }
        require(registry.snapshot().size() == 3, "Trusted catalogs can publish their reserved namespaces");
    }

    private static void verifyClearInvalidation() {
        ContentRegistry registry = new ContentRegistry();
        ContentRegistry.Publication beforeClear;
        try (ContentRegistry.Batch batch = registry.begin("same-owner.lua")) {
            batch.add(key("mymod:block/before_clear"), ContentType.BLOCK, new Object());
            beforeClear = batch.commit();
        }
        ContentRegistry.Batch stale = registry.begin("stale.lua");
        stale.add(key("mymod:block/stale"), ContentType.BLOCK, new Object());
        registry.clear();
        expectFailure(IllegalStateException.class, stale::commit);
        Object current = new Object();
        publish(registry, "same-owner.lua", key("mymod:block/after_clear"), ContentType.BLOCK, current);
        beforeClear.close();
        require(registry.resolve(key("mymod:block/after_clear")) == current,
                "A pre-clear publication cannot remove a later publication with the same revision");
        require(registry.snapshot().size() == 1, "Clear prevents an old batch from resurrecting declarations");
    }

    private static void publish(ContentRegistry registry, String owner, ContentKey key, ContentType type,
            Object content) {
        try (ContentRegistry.Batch batch = registry.begin(owner)) {
            batch.add(key, type, content);
            batch.commit();
        }
    }

    private static ContentKey key(String value) {
        return ContentKey.parseCanonical(value);
    }

    private static ContentRegistryException expectRegistry(ContentRegistryException.Reason reason, Runnable action) {
        try {
            action.run();
        } catch (ContentRegistryException error) {
            require(reason == error.getReason(), "Expected " + reason + ", got " + error.getReason());
            return error;
        }
        throw new AssertionError("Expected registry conflict " + reason);
    }

    private static void expectFailure(Class<? extends RuntimeException> type, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException error) {
            if (type.isInstance(error)) {
                return;
            }
            throw new AssertionError("Unexpected exception", error);
        }
        throw new AssertionError("Expected " + type.getSimpleName());
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static final class EqualContent {
        private final String value;

        private EqualContent(String value) {
            this.value = value;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof EqualContent && value.equals(((EqualContent) other).value);
        }

        @Override
        public int hashCode() {
            return value.hashCode();
        }
    }
}
