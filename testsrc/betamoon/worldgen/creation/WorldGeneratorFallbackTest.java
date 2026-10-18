package betamoon.worldgen.creation;

import betamoon.worldgen.GenerationStage;
import betamoon.worldgen.WorldGenRegistry;
import betamoon.worldgen.WorldGenKey;
import betamoon.worldgen.FeatureResult;
import betamoon.worldgen.HeightProvider;
import betamoon.worldgen.IntRange;
import betamoon.worldgen.PlacementConditions;
import betamoon.worldgen.terrain.GeneratorManifestStore;
import betamoon.worldgen.terrain.GeneratorProfileRegistry;
import betamoon.worldgen.terrain.TerrainFoundationTest;
import betamoon.worldgen.terrain.WorldGenerationRuntime;
import betamoon.worldgen.terrain.WorldGeneratorManifest;
import betamoon.worldgen.terrain.WorldGeneratorProfile;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import net.minecraft.src.ChunkProviderGenerate;

/**
 * Consent boundaries and byte-for-byte persistence across fallback and
 * restoration.
 */
public final class WorldGeneratorFallbackTest {
    public static void main(String[] args) throws Exception {
        savedSelection();
        legacyManifest();
        incompatibleDependencies();
        System.out.println("Generator fallback passed: read-only preflight, explicit scoped consent, native routing, "
                + "dimension return, restart isolation, corruption rejection and generator restoration.");
    }

    private static void incompatibleDependencies() throws Exception {
        Path directory = Files.createTempDirectory("betamoon-preflight-dependencies-");
        File settings = directory.resolve("settings.dat").toFile();
        File manifest = directory.resolve("manifest.dat").toFile();
        WorldGeneratorProfile profile = new WorldGeneratorProfile("test:generator/present", "present.lua",
                Collections.<String, String>emptyMap());
        GeneratorProfileRegistry.replaceOwner(profile.owner, Collections.singletonList(profile));
        try (WorldGenRegistry.PublicationBatch batch = WorldGenRegistry.beginPublication("dependency.lua", "Test")) {
            WorldGenKey feature = WorldGenRegistry.addFeature("test:feature/required", "test",
                    (context, origin, plan) -> FeatureResult.rejected("test"), Collections.emptyList(), 1, 1);
            WorldGenRegistry.addPlacement("test:placement/required", feature, GenerationStage.SURFACE_FEATURES,
                    Collections.singleton("minecraft:overworld"), new IntRange(1, 1), 0, 1, HeightProvider.fixed(64),
                    PlacementConditions.any(), Collections.emptyList(), Collections.emptyList(), 0, 0, 1, "chunk", 1);
            batch.publish();
        }
        GenerationSelection selection = GenerationSelection.create(new GenerationDraft(new GenerationCatalog(profile)), 42);
        new GenerationSelectionStore(settings).write(selection);
        new GeneratorManifestStore(manifest).write(new WorldGeneratorManifest(42, 0, profile.key,
                selection.profile().signature, new WorldGeneratorManifest.OriginalSpawn(0, 64, 0)));
        byte[] before = Files.readAllBytes(settings.toPath());
        require(MissingWorldGenerator.inspect(settings, manifest, 42) == null, "Complete selection passes preflight");
        WorldGenRegistry.clear();
        try {
            MissingWorldGenerator.inspect(settings, manifest, 42);
            throw new AssertionError("Missing dependency must fail before world construction");
        } catch (IOException expected) {
            require(expected.getMessage().contains("test:placement/required"), "Refusal identifies the dependency");
        }
        require(Arrays.equals(before, Files.readAllBytes(settings.toPath())), "Refusal cannot rewrite saved choices");
        GeneratorProfileRegistry.replaceOwner(profile.owner, Collections.emptyList());
    }

    private static void savedSelection() throws Exception {
        Path directory = Files.createTempDirectory("betamoon-fallback-");
        TerrainFoundationTest.Save save = new TerrainFoundationTest.Save(directory);
        WorldGeneratorProfile definition = new WorldGeneratorProfile("test:generator/missing", "missing.lua",
                Collections.<String, String>emptyMap());
        GeneratorProfileRegistry.replaceOwner(definition.owner, Collections.singletonList(definition));
        GenerationSelection selection = GenerationSelection
                .create(new GenerationDraft(new GenerationCatalog(definition)), 42);
        File settings = save.func_28113_a("betamoon_generation_settings");
        File manifest = save.func_28113_a("betamoon_generator_overworld");
        GenerationSelectionStore selections = new GenerationSelectionStore(settings);
        selections.write(selection);
        WorldGeneratorProfile resolved = selection.profile();
        new GeneratorManifestStore(manifest).write(new WorldGeneratorManifest(42, 0, resolved.key, resolved.signature,
                new WorldGeneratorManifest.OriginalSpawn(0, 65, 0)));
        byte[] originalSettings = Files.readAllBytes(settings.toPath());
        byte[] originalManifest = Files.readAllBytes(manifest.toPath());
        require(MissingWorldGenerator.inspect(settings, manifest, 42) == null,
                "An installed generator needs no warning");
        GeneratorProfileRegistry.replaceOwner(definition.owner, Collections.<WorldGeneratorProfile>emptyList());
        MissingWorldGenerator missing = MissingWorldGenerator.inspect(settings, manifest, 42);
        require(missing != null && missing.missing.contains(definition.key), "Preflight lists the missing generator");
        require(Arrays.equals(originalSettings, Files.readAllBytes(settings.toPath())),
                "Preflight cannot rewrite choices");
        expect(IllegalStateException.class,
                () -> WorldGenerationRuntime.prepare(new TerrainFoundationTest.Fixture(save, 42), save));
        try (WorldGeneratorFallback permission = WorldGeneratorFallback.begin(missing)) {
            WorldGeneratorFallback.startingWorld();
            TerrainFoundationTest.Fixture world = new TerrainFoundationTest.Fixture(save, 42);
            WorldGenerationRuntime.prepare(world, save);
            require(WorldGenerationRuntime.isFallback(world) && WorldGenerationRuntime.profile(world) == null,
                    "Confirmed missing-profile load uses a temporary native binding");
            ChunkProviderGenerate nativeProvider = new ChunkProviderGenerate(world, 42);
            require(WorldGenerationRuntime.select(world.worldProvider, nativeProvider) == nativeProvider,
                    "Fallback retains the literal native provider");
            for (GenerationStage stage : GenerationStage.values()) {
                require(!WorldGenerationRuntime.stageEnabled(world, stage), "Owned generation is disabled in fallback");
            }
            require(WorldGenerationRuntime.nativeCategoryEnabled(world, "ores"), "Native ores remain available");
            require(!WorldGenerationRuntime.contentEnabled(world, false, "test:placement/anything",
                    "test:feature/anything", Collections.<String>emptySet()),
                    "Fallback does not run registered placements");
            require(GenerationBiomes.key(world.getWorldChunkManager().getBiomeGenAt(64, -85)).startsWith("minecraft:"),
                    "Fallback resolves a native biome");
            TerrainFoundationTest.Save unrelated = new TerrainFoundationTest.Save(directory.resolve("other"));
            new GenerationSelectionStore(unrelated.func_28113_a("betamoon_generation_settings")).write(selection);
            expect(IllegalStateException.class,
                    () -> WorldGenerationRuntime.prepare(new TerrainFoundationTest.Fixture(unrelated, 42), unrelated));
        }
        TerrainFoundationTest.Fixture returning = new TerrainFoundationTest.Fixture(save, 42);
        WorldGenerationRuntime.prepare(returning, save);
        require(WorldGenerationRuntime.isFallback(returning), "A dimension return retains the session's consent");
        require(Arrays.equals(originalSettings, Files.readAllBytes(settings.toPath()))
                && Arrays.equals(originalManifest, Files.readAllBytes(manifest.toPath())),
                "Fallback leaves both generation records byte-for-byte unchanged");
        WorldGeneratorFallback.startingWorld();
        expect(IllegalStateException.class,
                () -> WorldGenerationRuntime.prepare(new TerrainFoundationTest.Fixture(save, 42), save));
        GeneratorProfileRegistry.replaceOwner(definition.owner, Collections.singletonList(definition));
        TerrainFoundationTest.Fixture restored = new TerrainFoundationTest.Fixture(save, 42);
        WorldGenerationRuntime.prepare(restored, save);
        require(!WorldGenerationRuntime.isFallback(restored) && WorldGenerationRuntime.profile(restored) != null,
                "Restoring the generator resumes the saved profile automatically");
        require(Arrays.equals(originalSettings, Files.readAllBytes(settings.toPath())),
                "Restoration preserves choices");
        GeneratorProfileRegistry.replaceOwner(definition.owner, Collections.<WorldGeneratorProfile>emptyList());
        Files.write(settings.toPath(), GenerationSelectionStore.encode(selection, 1));
        byte[] oldSchema = Files.readAllBytes(settings.toPath());
        MissingWorldGenerator older = MissingWorldGenerator.inspect(settings, manifest, 42);
        require(Arrays.equals(oldSchema, Files.readAllBytes(settings.toPath())),
                "Preflight cannot migrate an older schema");
        try (WorldGeneratorFallback permission = WorldGeneratorFallback.begin(older)) {
            WorldGenerationRuntime.prepare(new TerrainFoundationTest.Fixture(save, 42), save);
        }
        require(Arrays.equals(oldSchema, Files.readAllBytes(settings.toPath())),
                "Fallback cannot migrate saved choices");
        byte[] corrupt = oldSchema.clone();
        corrupt[20] ^= 1;
        Files.write(settings.toPath(), corrupt);
        expect(IOException.class, () -> MissingWorldGenerator.inspect(settings, manifest, 42));
        try (WorldGeneratorFallback permission = WorldGeneratorFallback.begin(older)) {
            expect(IllegalStateException.class,
                    () -> WorldGenerationRuntime.prepare(new TerrainFoundationTest.Fixture(save, 42), save));
        }
        Files.write(settings.toPath(), oldSchema);
        Files.write(manifest.toPath(), new byte[]{1, 2, 3});
        expect(IOException.class, () -> MissingWorldGenerator.inspect(settings, manifest, 42));
        try (WorldGeneratorFallback permission = WorldGeneratorFallback.begin(older)) {
            expect(IllegalStateException.class,
                    () -> WorldGenerationRuntime.prepare(new TerrainFoundationTest.Fixture(save, 42), save));
        }
        WorldGeneratorFallback.startingWorld();
    }

    private static void legacyManifest() throws Exception {
        TerrainFoundationTest.Save save = new TerrainFoundationTest.Save(
                Files.createTempDirectory("betamoon-legacy-fallback-"));
        File settings = save.func_28113_a("betamoon_generation_settings");
        File file = save.func_28113_a("betamoon_generator_overworld");
        WorldGeneratorProfile profile = new WorldGeneratorProfile("test:generator/legacy", "legacy.lua",
                Collections.<String, String>emptyMap());
        new GeneratorManifestStore(file).write(new WorldGeneratorManifest(7, 0, profile.key, profile.signature,
                new WorldGeneratorManifest.OriginalSpawn(0, 65, 0)));
        byte[] original = Files.readAllBytes(file.toPath());
        MissingWorldGenerator missing = MissingWorldGenerator.inspect(settings, file, 7);
        require(missing != null, "Legacy profile manifests also receive a warning");
        try (WorldGeneratorFallback permission = WorldGeneratorFallback.begin(missing)) {
            TerrainFoundationTest.Fixture world = new TerrainFoundationTest.Fixture(save, 7);
            WorldGenerationRuntime.prepare(world, save);
            require(WorldGenerationRuntime.isFallback(world), "Legacy saves support explicit fallback");
        }
        require(!settings.exists() && Arrays.equals(original, Files.readAllBytes(file.toPath())),
                "Legacy fallback cannot add or replace generator records");
        WorldGeneratorFallback.startingWorld();
    }

    private static void expect(Class<? extends Exception> type, Checked action) throws Exception {
        try {
            action.run();
        } catch (Exception exception) {
            if (type.isInstance(exception)) {
                return;
            }
            throw exception;
        }
        throw new AssertionError("Expected " + type.getSimpleName());
    }

    private interface Checked {
        void run() throws Exception;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
