package betamoon.assets;

import java.util.List;

/**
 * Runs with only project class directories on the classpath, without Lua or
 * Minecraft jars.
 */
public final class AssetFoundationTest {
    private AssetFoundationTest() {
    }

    public static void main(String[] arguments) {
        verifyIdentitiesAndPaths();
        verifyAtomicPublicationAndOwnership();
        verifyGenerationLifetime();
        System.out.println("Asset foundations passed: identity, paths, ownership, atomic publication and lifetime.");
    }

    private static void verifyIdentitiesAndPaths() {
        AssetKey key = AssetKey.parse("mymod:entities/guard");
        require(key.equals(AssetKey.parse(key.toString())), "Keys must round-trip exactly");
        require(key.hashCode() == AssetKey.parse(key.toString()).hashCode(), "Equal keys must share a hash");
        require(!key.equals(AssetKey.parse("other:entities/guard")), "Namespaces must remain significant");
        AssetId texture = new AssetId(AssetKind.TEXTURE, key);
        AssetId model = new AssetId(AssetKind.MODEL, key);
        require(!texture.equals(model), "Different categories have independent key spaces");

        String[] invalidKeys = {"guard", ":guard", "mymod:", "MyMod:guard", "mymod:Guard", "mymod:a:b",
                "mymod:../guard", "mymod:/guard", "mymod:a//b", "mymod:a/./b", "mymod:a/", "mymod:a./b"};
        for (String value : invalidKeys) {
            expectFailure(IllegalArgumentException.class, () -> AssetKey.parse(value));
        }
        String[] invalidPaths = {"", "../guard.png", "/guard.png", "C:/guard.png", "a/../guard.png", "a//b.png",
                "a/./b.png", "a/", "a/ b.png", "a/b.png ", "a/b?.png", "a/b\u0000.png"};
        for (String value : invalidPaths) {
            expectFailure(IllegalArgumentException.class, () -> AssetPath.parse(value));
        }
        require(AssetPath.parse("mymod\\Textures\\Guard.png").getDirectOverridePath().toString()
                .equals("bm_assets/mymod/Textures/Guard.png"), "Direct paths preserve case and normalize separators");
        require(AssetPath.parse("dummy.png").getDirectOverridePath().toString().equals("bm_assets/dummy.png"),
                "Bare texture paths must resolve immediately under bm_assets");
        AssetDefinition uppercaseTexture = new AssetDefinition(texture, AssetPath.parse("Guard.PNG"), "png");
        require(uppercaseTexture.getFallbackPath().toString().equals("Guard.PNG"),
                "Registered fallback paths retain their exact case, including the extension");
        AssetDefinition original = new AssetDefinition(model, AssetPath.parse("author/guard.json"), "json");
        AssetDefinition relocated = new AssetDefinition(model, AssetPath.parse("renamed/guard.json"), "json");
        require(original.getOverridePath().toString().equals("bm_assets/mymod/models/entities/guard.json"),
                "Registered models must use the JSON suffix");
        require(original.getOverridePath().equals(relocated.getOverridePath()),
                "Moving a fallback must not change a registered override path");
        AssetDefinition animation = new AssetDefinition(new AssetId(AssetKind.ANIMATION, key),
                AssetPath.parse("author/guard.animation.json"), "animation.json");
        require(animation.getOverridePath().toString()
                .equals("bm_assets/mymod/animations/entities/guard.animation.json"),
                "Registered animations must retain their compound suffix");
        expectFailure(IllegalArgumentException.class,
                () -> new AssetDefinition(model, AssetPath.parse("guard.json"), "../json"));
        expectFailure(IllegalArgumentException.class,
                () -> new AssetDefinition(model, AssetPath.parse("guard.png"), "json"));
    }

    private static void verifyAtomicPublicationAndOwnership() {
        AssetRegistry registry = new AssetRegistry();
        AssetDefinition texture = texture("mymod:guard", "guard.png");
        AssetRegistry.Publication original;
        try (AssetRegistry.Batch batch = registry.begin("owner.lua")) {
            batch.add(texture);
            require(registry.find(texture.getId()) == null, "Staged definitions must not be visible");
            original = batch.commit();
            expectFailure(IllegalStateException.class, () -> batch.add(texture));
            expectFailure(IllegalStateException.class, batch::commit);
        }
        List<AssetRegistration> snapshot = registry.snapshot();
        require(snapshot.size() == 1, "Published definitions must be visible together");
        expectFailure(UnsupportedOperationException.class, snapshot::clear);

        AssetDefinition unrelated = texture("other:free", "free.png");
        try (AssetRegistry.Batch batch = registry.begin("intruder.lua")) {
            batch.add(unrelated);
            batch.add(texture);
            expectFailure(IllegalArgumentException.class, batch::commit);
            require(registry.find(unrelated.getId()) == null, "A conflict must publish none of the batch");
            require(registry.find(texture.getId()).getOwner().equals("owner.lua"),
                    "A conflict must preserve ownership");
        }
        try (AssetRegistry.Batch batch = registry.begin("owner.lua")) {
            batch.add(texture);
            expectFailure(IllegalArgumentException.class, () -> batch.add(texture));
        }
        require(registry.find(texture.getId()).getDefinition() == texture,
                "Abandoned batches must leave the old publication intact");

        try (AssetRegistry.Batch batch = registry.begin("owner.lua")) {
            AssetDefinition model = new AssetDefinition(new AssetId(AssetKind.MODEL, texture.getId().getKey()),
                    AssetPath.parse("guard.json"), "json");
            batch.add(model);
            batch.commit();
            require(registry.find(texture.getId()) == null, "Replacement removes omitted declarations");
            require(registry.find(model.getId()) != null, "Replacement publishes new declarations");
        }
        original.close();
        require(registry.snapshot().size() == 1, "Old cleanup must not remove the replacement");
        require(snapshot.get(0).getDefinition() == texture, "Snapshots must not change after replacement");
    }

    private static void verifyGenerationLifetime() {
        AssetRegistry registry = new AssetRegistry();
        AssetDefinition texture = texture("mymod:guard", "guard.png");
        AssetRegistry.Batch stale = registry.begin("owner.lua");
        stale.add(texture);
        AssetRegistry.Publication current;
        try (AssetRegistry.Batch batch = registry.begin("owner.lua")) {
            batch.add(texture);
            current = batch.commit();
        }
        expectFailure(IllegalStateException.class, stale::commit);
        AssetRegistry.Batch beforeUnload = registry.begin("owner.lua");
        beforeUnload.add(texture);
        current.close();
        current.close();
        require(registry.snapshot().isEmpty(), "Release is idempotent");
        expectFailure(IllegalStateException.class, beforeUnload::commit);

        try (AssetRegistry.Batch batch = registry.begin("new-owner.lua")) {
            batch.add(texture);
            batch.commit();
        }
        current.close();
        require(registry.find(texture.getId()).getOwner().equals("new-owner.lua"),
                "Cleanup cannot remove a different owner that later reuses a released key");
        try (AssetRegistry.Batch empty = registry.begin("new-owner.lua")) {
            empty.commit().close();
        }
        require(registry.snapshot().isEmpty(), "An empty replacement releases every previous declaration");
    }

    private static AssetDefinition texture(String key, String path) {
        return new AssetDefinition(new AssetId(AssetKind.TEXTURE, AssetKey.parse(key)), AssetPath.parse(path), "png");
    }

    private static void expectFailure(Class<? extends RuntimeException> expected, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException error) {
            if (expected.isInstance(error)) {
                return;
            }
            throw new AssertionError("Unexpected exception", error);
        }
        throw new AssertionError("Expected " + expected.getSimpleName());
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
