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
        verifyReservationsAndRollback();
        verifyRetainedIdentityReload();
        verifyOwnerRetention();
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

    private static void verifyRetainedIdentityReload() {
        ContentRegistry registry = new ContentRegistry();
        ContentKey key = key("mymod:block/press");
        NativeContentIdentity block250 = NativeContentIdentity.of(ContentType.BLOCK, "numeric_id", "250");
        NativeContentIdentity block251 = NativeContentIdentity.of(ContentType.BLOCK, "numeric_id", "251");
        ContentRegistry.Batch abandoned = registry.begin("abandoned.lua");
        abandoned.addRetained(key("mymod:block/abandoned"), ContentType.BLOCK, new Object(), block250);
        expectRegistry(ContentRegistryException.Reason.RETAINED_KEY_CHANGED, () -> {
            try (ContentRegistry.Batch competing = registry.begin("competing.lua")) {
                competing.addRetained(key("mymod:block/competing"), ContentType.BLOCK, new Object(), block250);
            }
        });
        abandoned.close();
        require(registry.retainedKeyOf(block250) == null,
                "Abandoning initialization releases the reservation without creating a retained claim");
        Object original = new Object();
        ContentRegistry.Publication originalPublication;
        try (ContentRegistry.Batch batch = registry.begin("press.lua")) {
            batch.addRetained(key, ContentType.BLOCK, original, block250);
            originalPublication = batch.commit();
        }
        require(registry.find(key).isRetained(), "Retained registrations expose their compatibility identity");
        require(block250.equals(registry.find(key).getNativeIdentity()), "Registration retains its native identity");
        require(key.equals(registry.retainedKeyOf(block250)),
                "Native identity resolves to its permanent canonical key");
        require(block250.equals(registry.nativeIdentityOf(key)), "Canonical key resolves to its native identity");

        Object replacement = new Object();
        ContentRegistry.Publication replacementPublication;
        try (ContentRegistry.Batch batch = registry.begin("press.lua")) {
            batch.addRetained(key, ContentType.BLOCK, replacement, block250);
            replacementPublication = batch.commit();
        }
        require(registry.resolve(key) == replacement, "Compatible retained reload may replace its runtime facade");
        originalPublication.close();
        require(registry.resolve(key) == replacement, "Old retained cleanup cannot remove the reload");

        expectRegistry(ContentRegistryException.Reason.RETAINED_IDENTITY_CHANGED, () -> {
            try (ContentRegistry.Batch batch = registry.begin("press.lua")) {
                batch.addRetained(key, ContentType.BLOCK, new Object(), block251);
            }
        });
        expectRegistry(ContentRegistryException.Reason.RETAINED_IDENTITY_CHANGED, () -> {
            try (ContentRegistry.Batch batch = registry.begin("press.lua")) {
                batch.add(key, ContentType.BLOCK, new Object());
            }
        });
        expectRegistry(ContentRegistryException.Reason.RETAINED_KEY_CHANGED, () -> {
            try (ContentRegistry.Batch batch = registry.begin("press.lua")) {
                batch.addRetained(key("mymod:block/renamed_press"), ContentType.BLOCK, new Object(), block250);
            }
        });
        expectRegistry(ContentRegistryException.Reason.TYPE_MISMATCH, () -> {
            try (ContentRegistry.Batch batch = registry.begin("press.lua")) {
                batch.addRetained(key, ContentType.BLOCK, new Object(),
                        NativeContentIdentity.of(ContentType.ITEM, "numeric_id", "250"));
            }
        });
        require(registry.resolve(key) == replacement, "Rejected retained reloads preserve active state");

        replacementPublication.close();
        require(registry.find(key) == null, "Unload removes the active retained binding");
        require(key.equals(registry.retainedKeyOf(block250)), "Unload keeps the retained compatibility claim");
        expectRegistry(ContentRegistryException.Reason.DUPLICATE_KEY, () -> {
            try (ContentRegistry.Batch batch = registry.begin("intruder.lua")) {
                batch.addRetained(key, ContentType.BLOCK, new Object(), block250);
            }
        });
        try (ContentRegistry.Batch batch = registry.begin("press.lua")) {
            batch.addRetained(key, ContentType.BLOCK, new Object(), block250);
            batch.commit();
        }

        registry.clear();
        ContentKey renamed = key("mymod:block/renamed_press");
        try (ContentRegistry.Batch batch = registry.begin("press.lua")) {
            batch.addRetained(renamed, ContentType.BLOCK, new Object(), block250);
            batch.commit();
        }
        require(renamed.equals(registry.retainedKeyOf(block250)),
                "Registry reset releases retained compatibility claims");

        expectFailure(IllegalArgumentException.class,
                () -> NativeContentIdentity.of(ContentType.BLOCK, "Numeric-ID", "250"));
        expectFailure(IllegalArgumentException.class,
                () -> NativeContentIdentity.of(ContentType.BLOCK, "numeric_id", "bad\nvalue"));
    }

    private static void verifyOwnerRetention() {
        ContentRegistry registry = new ContentRegistry();
        ContentKey keptKey = key("mymod:block/kept");
        ContentKey removedKey = key("mymod:item/removed");
        NativeContentIdentity keptIdentity = NativeContentIdentity.of(ContentType.BLOCK, "numeric_id", "252");
        Object kept = new Object();
        Object removed = new Object();
        try (ContentRegistry.Batch batch = registry.begin("kept.lua")) {
            batch.addRetained(keptKey, ContentType.BLOCK, kept, keptIdentity);
            batch.commit();
        }
        publish(registry, "removed.lua", removedKey, ContentType.ITEM, removed);

        ContentRegistry.Batch stale = registry.begin("removed.lua");
        ContentKey stagedKey = key("mymod:item/staged_before_retention");
        stale.add(stagedKey, ContentType.ITEM, new Object());
        registry.retainOwners(java.util.Collections.singleton("kept.lua"));

        require(registry.resolve(keptKey) == kept, "Retained owners remain active");
        require(registry.find(removedKey) == null && registry.keyOf(removed) == null,
                "Missing owners lose forward and reverse active mappings");
        expectFailure(IllegalStateException.class, stale::commit);
        try (ContentRegistry.Batch releasedReservation = registry.begin("new.lua")) {
            releasedReservation.add(stagedKey, ContentType.ITEM, new Object());
        }

        registry.retainOwners(java.util.Collections.<String>emptySet());
        require(registry.find(keptKey) == null, "Owner pruning removes retained content from the active view");
        require(keptKey.equals(registry.retainedKeyOf(keptIdentity)),
                "Owner pruning preserves restart-only identity claims");
    }

    private static void verifyReservationsAndRollback() {
        ContentRegistry registry = new ContentRegistry();
        ContentKey activeKey = key("mymod:block/active");
        Object active = new Object();
        publish(registry, "owner.lua", activeKey, ContentType.BLOCK, active);

        ContentKey stagedKey = key("mymod:item/staged");
        Object staged = new Object();
        ContentRegistry.Batch initializing = registry.begin("initializing.lua");
        initializing.add(stagedKey, ContentType.ITEM, staged);
        require(registry.find(stagedKey) == null && registry.keyOf(staged) == null,
                "Reservations stay invisible until publication");

        ContentRegistryException keyReservation = expectRegistry(ContentRegistryException.Reason.DUPLICATE_KEY, () -> {
            try (ContentRegistry.Batch competing = registry.begin("competing.lua")) {
                competing.add(stagedKey, ContentType.ITEM, new Object());
            }
        });
        require("initializing.lua".equals(keyReservation.getConflictingOwner()),
                "Reservation conflicts identify the initializing owner");
        expectRegistry(ContentRegistryException.Reason.DUPLICATE_CONTENT, () -> {
            try (ContentRegistry.Batch competing = registry.begin("competing.lua")) {
                competing.add(key("mymod:item/other"), ContentType.ITEM, staged);
            }
        });

        initializing.close();
        publish(registry, "competing.lua", stagedKey, ContentType.ITEM, staged);
        require(registry.resolve(stagedKey) == staged, "Closing an uncommitted batch releases every reservation");
        require(registry.resolve(activeKey) == active, "Abandoned initialization does not alter active content");

        ContentRegistry.Batch stale = registry.begin("owner.lua");
        stale.add(key("mymod:block/stale"), ContentType.BLOCK, new Object());
        publish(registry, "owner.lua", key("mymod:block/current"), ContentType.BLOCK, new Object());
        expectFailure(IllegalStateException.class, stale::commit);
        try (ContentRegistry.Batch afterFailure = registry.begin("after-failure.lua")) {
            afterFailure.add(key("mymod:block/stale"), ContentType.BLOCK, new Object());
        }
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
