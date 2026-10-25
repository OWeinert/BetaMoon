package betamoon.worldgen.terrain;

import betamoon.instrumentation.agent.AgentRuntime;
import betamoon.worldgen.creation.GenerationCatalog;
import betamoon.worldgen.creation.GenerationDraft;
import betamoon.worldgen.creation.GenerationSelection;
import betamoon.worldgen.creation.GenerationSelectionStore;
import betamoon.worldgen.creation.WorldCreationSelection;
import betamoon.worldgen.WorldGenKind;
import betamoon.worldgen.BiomeGenRegistry;
import betamoon.worldgen.BlockSet;
import betamoon.worldgen.IntRange;
import betamoon.worldgen.surface.SurfaceRuleSet;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import net.minecraft.src.Block;
import net.minecraft.src.Chunk;
import net.minecraft.src.ChunkCoordinates;
import net.minecraft.src.ChunkProviderGenerate;
import net.minecraft.src.EnumSkyBlock;
import net.minecraft.src.IChunkLoader;
import net.minecraft.src.IChunkProvider;
import net.minecraft.src.IProgressUpdate;
import net.minecraft.src.ISaveHandler;
import net.minecraft.src.Material;
import net.minecraft.src.World;
import net.minecraft.src.WorldInfo;
import net.minecraft.src.WorldProvider;

/**
 * Real file corruption/reopen cases, unsigned raw cells and native provider
 * parity.
 */
public final class TerrainFoundationTest {
    private TerrainFoundationTest() {
    }

    public static void main(String[] arguments) throws Exception {
        require(Block.stone != null, "Native block registry must initialize");
        profiles();
        manifests();
        rawBuffers();
        nativeParity();
        worldBinding();
        overrideContracts();
        if (AgentRuntime.isRegistered()) {
            instrumentedBoundaries();
            nativeBiomeOverrides();
            ownedBiomeOverride();
        }
        System.out.println("Terrain foundation passed: atomic manifest/reopen/corruption, signatures, "
                + "raw metadata/heightmaps, native hashes and provider routing.");
    }

    private static WorldGeneratorProfile profile(String key, String owner, boolean active, int priority) {
        return new WorldGeneratorProfile(key, owner, Collections.<String, String>emptyMap());
    }

    private static void profiles() {
        Map<String, String> first = new LinkedHashMap<String, String>();
        first.put("a", "bc");
        first.put("d", "ef");
        Map<String, String> second = new LinkedHashMap<String, String>();
        second.put("d", "ef");
        second.put("a", "bc");
        require(GenerationSignature.of(first).equals(GenerationSignature.of(second)),
                "Signature must not depend on declaration order");
        second.put("a", "changed");
        require(!GenerationSignature.of(first).equals(GenerationSignature.of(second)),
                "Resolved dependency changes must affect compatibility");
        String owner = "terrain_profiles.lua";
        WorldGeneratorProfile low = profile("fixture:generator/low", owner, true, 1);
        WorldGeneratorProfile high = profile("fixture:generator/high", owner, true, 2);
        GeneratorProfileRegistry.replaceOwner(owner, Arrays.asList(low, high));
        GeneratorProfileRegistry.Snapshot snapshot = GeneratorProfileRegistry.snapshot();
        require(snapshot.profiles().size() == 2, "Every registered profile is explicitly selectable");
        expect(IllegalArgumentException.class,
                () -> GeneratorProfileRegistry.replaceOwner(owner, Arrays.asList(low, low)));
        require(GeneratorProfileRegistry.snapshot() == snapshot, "Failed publication retains the active snapshot");
        GeneratorProfileRegistry.replaceOwner(owner, Collections.<WorldGeneratorProfile>emptyList());
        require(snapshot.get(high.key) == high && GeneratorProfileRegistry.snapshot().get(high.key) == null,
                "World snapshots must survive owner removal without becoming mutable catalogs");
    }

    private static void manifests() throws Exception {
        Path directory = Files.createTempDirectory("betamoon-terrain-manifest-");
        Path file = directory.resolve("generator.dat");
        GeneratorManifestStore store = new GeneratorManifestStore(file.toFile());
        require(store.read() == null && !Files.exists(file), "Absent manifests must remain absent");
        WorldGeneratorProfile profile = profile("fixture:generator/native", "fixture.lua", true, 1);
        WorldGeneratorManifest pending = new WorldGeneratorManifest(Long.MIN_VALUE + 1, 0, profile.key,
                profile.signature, null);
        store.write(pending);
        require(store.read().originalSpawn == null, "Pending bootstrap state must round-trip explicitly");
        WorldGeneratorManifest completed = pending.withOriginalSpawn(-120, 64, 301);
        store.write(completed);
        WorldGeneratorManifest loaded = new GeneratorManifestStore(file.toFile()).read();
        require(loaded.worldSeed == pending.worldSeed && loaded.originalSpawn.x == -120
                && loaded.originalSpawn.z == 301, "Manifest must preserve full seed and original spawn after reopen");
        loaded.requireCompatible(pending.worldSeed, 0, profile);
        expect(IllegalStateException.class, () -> loaded.requireCompatible(3, 0, profile));
        expect(IllegalStateException.class, () -> loaded.requireCompatible(pending.worldSeed, 0, null));
        expect(IllegalStateException.class, () -> loaded.withOriginalSpawn(0, 64, 0));
        WorldGeneratorProfile changed = new WorldGeneratorProfile(profile.key, profile.owner,
                Collections.singletonMap("changed_material", "fixture:block/stone@191"));
        expect(IllegalStateException.class, () -> loaded.requireCompatible(pending.worldSeed, 0, changed));
        byte[] valid = Files.readAllBytes(file);
        for (int length : new int[]{0, 8, valid.length - 1}) {
            Files.write(file, Arrays.copyOf(valid, length));
            expect(IOException.class, store::read);
        }
        byte[] corrupt = valid.clone();
        corrupt[16] ^= 1;
        Files.write(file, corrupt);
        expect(IOException.class, store::read);
        Files.write(file, new byte[4097]);
        expect(IOException.class, store::read);
        Files.write(file, valid);
        Files.write(directory.resolve("orphan.pending"), new byte[]{1, 2, 3});
        require(store.read().originalSpawn.x == -120, "Unpublished temporary files must not replace durable identity");
        try (Stream<Path> files = Files.list(directory)) {
            require(files.filter(path -> path.getFileName().toString().endsWith(".pending")).count() == 1,
                    "Successful writes must not leak their temporary records");
        }
    }

    private static void rawBuffers() {
        ChunkBlockBuffer buffer = new ChunkBlockBuffer();
        TerrainHeightmaps maps = new TerrainHeightmaps(buffer);
        TerrainHeightmaps otherView = new TerrainHeightmaps(buffer);
        require(maps.worldSurface(15, 0) == -1, "Empty column height must be explicit");
        buffer.fillColumn(15, 0, 0, 3, Block.dirt.blockID, 0);
        buffer.set(15, 4, 0, Block.grass.blockID, 0);
        buffer.set(15, 5, 0, Block.waterStill.blockID, 0);
        buffer.set(15, 6, 0, Block.plantYellow.blockID, 0);
        require(maps.worldSurface(15, 0) == 6 && maps.oceanFloor(15, 0) == 4 && maps.fluidSurface(15, 0) == 5
                && maps.motionBlocking(15, 0) == 4,
                "Derived semantic heights must distinguish plants, fluid and solid ground");
        require(otherView.worldSurface(15, 0) == 6, "Multiple map views must independently observe revisions");
        buffer.set(15, 6, 0, 0, 0);
        require(otherView.worldSurface(15, 0) == 5 && maps.worldSurface(15, 0) == 5,
                "Both map views must invalidate after removal");
        buffer.set(1, 127, 15, Block.cloth.blockID, 15);
        require(buffer.metadata(1, 127, 15) == 15 && buffer.blockId(1, 127, 15) == Block.cloth.blockID,
                "Raw storage must preserve full metadata and world-height boundary");
        byte[] detached = buffer.copyBlocks();
        detached[(1 << 11) | (15 << 7) | 127] = 0;
        require(buffer.blockId(1, 127, 15) != 0, "Exported arrays must not bypass dirty tracking");
        expect(IllegalArgumentException.class, () -> buffer.set(0, 128, 0, 1, 0));
        expect(IllegalArgumentException.class, () -> buffer.set(-1, 0, 0, 1, 0));
        expect(IllegalArgumentException.class, () -> buffer.set(0, 0, 0, 256, 0));
        expect(IllegalArgumentException.class, () -> buffer.set(0, 0, 0, 1, 16));
        expect(IllegalArgumentException.class, () -> buffer.set(0, 0, 0, 0, 1));
        expect(IllegalArgumentException.class, () -> buffer.set(0, 0, 0, Block.chest.blockID, 0));
        expect(IllegalArgumentException.class, () -> buffer.fillColumn(0, 0, 12, 5, 1, 0));
        Block highId = new Block(240, Material.rock) {
        };
        buffer.set(15, 127, 15, highId.blockID, 13);
        require(buffer.blockId(15, 127, 15) == 240 && buffer.metadata(15, 127, 15) == 13,
                "Unsigned IDs above 127 must survive raw storage with metadata");
    }

    private static void nativeParity() throws Exception {
        Path directory = Files.createTempDirectory("betamoon-native-parity-");
        Fixture first = new Fixture(new Save(directory.resolve("a")), 1390123101L);
        Fixture second = new Fixture(new Save(directory.resolve("b")), 1390123101L);
        IChunkProvider original = new ChunkProviderGenerate(first, first.getRandomSeed());
        IChunkProvider delegated = new BetaMoonChunkProvider(new ChunkProviderGenerate(second, second.getRandomSeed()),
                profile("fixture:generator/native", "native.lua", true, 1));
        int[][] coordinates = {{0, 0}, {-1, 3}, {3, -1}, {-2, -2}};
        String[] expected = {"e80d814bae4f2b11435cac68a8d93d56bde5c1f456b1660e4b8ca9b76f06e3fb",
                "b14fb964902757a596742402376b161726764da1e0b46b5a0c1e0de1af65197c",
                "0b5d70723fd5326eb2231c3f7bd5ef634eb1e650484163041a1e2f3abb480e21",
                "3587e9cd9e0c1ba8f7cf71832cef8bafc5feac0ab69433294e4d1e3f4c92c7ca"};
        Map<String, String> hashes = new HashMap<String, String>();
        for (int i = 0; i < coordinates.length; i++) {
            int[] position = coordinates[i];
            Chunk chunk = original.provideChunk(position[0], position[1]);
            hashes.put(position[0] + "," + position[1], chunkHash(chunk));
            require(expected[i].equals(chunkHash(chunk)),
                    "Native golden chunk changed at " + position[0] + "," + position[1]);
        }
        for (int i = coordinates.length - 1; i >= 0; i--) {
            int[] position = coordinates[i];
            Chunk chunk = delegated.provideChunk(position[0], position[1]);
            require(hashes.get(position[0] + "," + position[1]).equals(chunkHash(chunk)),
                    "Delegation must preserve native blocks, metadata, light and heights under reversed order");
        }
        require(original.makeString().equals(delegated.makeString()), "ModLoader must retain native dispatch name");
        CountingProvider counter = new CountingProvider();
        BetaMoonChunkProvider wrapper = new BetaMoonChunkProvider(counter,
                profile("fixture:generator/count", "count.lua", false, 0));
        wrapper.populate(counter, -1, 2);
        wrapper.provideChunk(-1, 2);
        wrapper.prepareChunk(-1, 2);
        wrapper.saveChunks(true, null);
        require(counter.population == 1 && counter.provide == 1 && counter.prepare == 1 && counter.saves == 1,
                "Each native provider operation must forward exactly once");
    }

    private static String chunkHash(Chunk chunk) {
        Map<String, String> values = new HashMap<String, String>();
        values.put("blocks", Arrays.toString(chunk.blocks));
        values.put("metadata", Arrays.toString(chunk.data.data));
        values.put("sky", Arrays.toString(chunk.skylightMap.data));
        values.put("light", Arrays.toString(chunk.blocklightMap.data));
        values.put("height", Arrays.toString(chunk.heightMap));
        return GenerationSignature.of(values);
    }

    private static void worldBinding() throws Exception {
        Path directory = Files.createTempDirectory("betamoon-world-binding-");
        Save save = new Save(directory);
        Fixture oldWorld = new Fixture(save, 7);
        WorldGeneratorProfile profile = profile("fixture:generator/bound", "binding.lua", true, 2);
        GeneratorProfileRegistry.replaceOwner(profile.owner, Collections.singletonList(profile));
        WorldGenerationRuntime.prepare(oldWorld, save);
        require(WorldGenerationRuntime.manifest(oldWorld) == null,
                "Existing native saves must not adopt a newly registered default");
        Fixture world = new Fixture(save, 7);
        world.isNewWorld = true;
        try (WorldCreationSelection pending = choose(profile, 7)) {
            WorldGenerationRuntime.prepare(world, save);
        }
        require(WorldGenerationRuntime.manifest(world) != null && save.locks >= 1,
                "Selected profile must be durable under the native session lock before generation");
        expect(IllegalStateException.class, () -> WorldGenerationRuntime.prepare(new Fixture(save, 7), save));
        expect(IllegalStateException.class, () -> WorldGenerationRuntime.requireSelectedProvider(world));
        WorldGenerationRuntime.recordNativeSpawn(world);
        world.setSpawnPoint(new ChunkCoordinates(100, 90, 100));
        WorldGenerationRuntime.recordNativeSpawn(world);
        require(WorldGenerationRuntime.manifest(world).originalSpawn.x == 0,
                "Later respawn changes must not replace the initial anchor");
        Fixture reopen = new Fixture(save, 7);
        WorldGenerationRuntime.prepare(reopen, save);
        require(WorldGenerationRuntime.manifest(reopen).originalSpawn.x == 0,
                "Saved profile and anchor must resolve on another world instance");
        expect(IllegalStateException.class, () -> WorldGenerationRuntime.prepare(new Fixture(save, 8), save));
        GeneratorProfileRegistry.replaceOwner(profile.owner, Collections.<WorldGeneratorProfile>emptyList());
        require(WorldGenerationRuntime.select(world.worldProvider,
                new CountingProvider()) instanceof BetaMoonChunkProvider,
                "Already open worlds retain their selected immutable profile snapshot");
        expect(IllegalStateException.class, () -> WorldGenerationRuntime.prepare(new Fixture(save, 7), save));
        Save absent = new Save(directory.resolve("absent"));
        Fixture nativeWorld = new Fixture(absent, 9);
        IChunkProvider nativeProvider = new CountingProvider();
        WorldGenerationRuntime.prepare(nativeWorld, absent);
        require(WorldGenerationRuntime.select(nativeWorld.worldProvider, nativeProvider) == nativeProvider,
                "No-profile path must preserve literal native provider identity");
        require(!Files.exists(directory.resolve("absent/betamoon_generator_overworld.dat")),
                "No-profile worlds must not create a manifest");
        WorldGenerationRuntime.prepare(new Fixture(null, 9), null);
        Save newNativeSave = new Save(directory.resolve("new-native"));
        Fixture newNative = new Fixture(newNativeSave, 17);
        newNative.isNewWorld = true;
        WorldGenerationRuntime.prepare(newNative, newNativeSave);
        require(WorldGenerationRuntime.select(newNative.worldProvider, nativeProvider) == nativeProvider,
                "Recording an anchor must retain the literal native provider");
        newNative.setSpawnPoint(new ChunkCoordinates(-25, 71, 19));
        WorldGenerationRuntime.recordNativeSpawn(newNative);
        newNative.setSpawnPoint(new ChunkCoordinates(100, 65, 100));
        WorldGenerationRuntime.recordNativeSpawn(newNative);
        require(WorldGenerationRuntime.fieldContext(newNative).spawnX == -25,
                "Native-world original anchors must ignore subsequent respawn changes");
        Fixture nativeReopen = new Fixture(newNativeSave, 17);
        WorldGenerationRuntime.prepare(nativeReopen, newNativeSave);
        require(WorldGenerationRuntime.profile(nativeReopen) == null
                && WorldGenerationRuntime.manifest(nativeReopen).originalSpawn.z == 19,
                "Native anchors must survive reopening without selecting a custom generator");

    }

    private static void instrumentedBoundaries() throws Exception {
        Save save = new Save(Files.createTempDirectory("betamoon-factory-hook-"));
        Fixture world = new Fixture(save, 17);
        world.isNewWorld = true;
        WorldGeneratorProfile profile = profile("fixture:generator/hooked", "hooked.lua", true, 1);
        GeneratorProfileRegistry.replaceOwner(profile.owner, Collections.singletonList(profile));
        try (WorldCreationSelection pending = choose(profile, 17)) {
            world.createOuterProvider();
        }
        require(WorldGenerationRuntime.manifest(world) != null, "World factory hook must capture its save handler");
        require(world.worldProvider.getChunkProvider() instanceof BetaMoonChunkProvider,
                "Native factory return hook must select the pinned provider");
        world.selectInitialSpawn();
        require(WorldGenerationRuntime.manifest(world).originalSpawn != null,
                "Actual native initial-spawn boundary must persist the original anchor");
        GeneratorProfileRegistry.replaceOwner(profile.owner, Collections.<WorldGeneratorProfile>emptyList());
    }

    private static void nativeBiomeOverrides() throws Exception {
        long seed = 177;
        try (BiomeGenRegistry.PublicationBatch surfaces = BiomeGenRegistry.beginPublication(
                "override_surface_fixture.lua", "override_surface_fixture.lua")) {
            BiomeGenRegistry.registerSurface("fixture:surface/gold_caps", (key, resourceOwner, owner, sourcePath) ->
                    new SurfaceRuleSet(key, resourceOwner, owner, sourcePath,
                            new BlockSet(Block.stone.blockID),
                            Collections.singletonList(new SurfaceRuleSet.Layer(Block.blockGold.blockID,
                                    new IntRange(1, 1))), null, 63));
            surfaces.publish();
        }
        Map<String, Object> source = new LinkedHashMap<String, Object>();
        source.put("key", "fixture:terrain_source/high_plains");
        source.put("type", "heightmap");
        source.put("height", 100.0);
        source.put("solid", 1.0);
        Map<String, Object> region = new LinkedHashMap<String, Object>();
        region.put("minX", 0.0);
        region.put("maxX", 8.0);
        region.put("minZ", 0.0);
        region.put("maxZ", 15.0);
        Map<String, Object> terrain = new LinkedHashMap<String, Object>();
        terrain.put("mode", "replace");
        terrain.put("source", "fixture:terrain_source/high_plains");
        Map<String, Object> caves = new LinkedHashMap<String, Object>();
        caves.put("mode", "none");
        Map<String, Object> materials = new LinkedHashMap<String, Object>();
        materials.put("mode", "replace");
        Map<String, Object> materialRule = new LinkedHashMap<String, Object>();
        materialRule.put("type", "when");
        Map<String, Object> condition = new LinkedHashMap<String, Object>();
        condition.put("type", "below_y");
        condition.put("y", 90.0);
        materialRule.put("condition", condition);
        materialRule.put("thenRule", 4.0);
        materials.put("rule", materialRule);
        Map<String, Object> surface = new LinkedHashMap<String, Object>();
        surface.put("mode", "replace");
        surface.put("surface", "fixture:surface/gold_caps");
        Map<String, Object> population = new LinkedHashMap<String, Object>();
        population.put("disableNative", Collections.singletonList("ores"));
        Map<String, Object> override = new LinkedHashMap<String, Object>();
        override.put("key", "fixture:terrain_override/high_plains");
        override.put("bases", Collections.singletonList("minecraft"));
        override.put("region", region);
        override.put("terrain", terrain);
        override.put("carvers", caves);
        override.put("materials", materials);
        override.put("surfaces", surface);
        override.put("population", population);
        try (TerrainRegistry.Batch batch = TerrainRegistry.begin("override_fixture.lua")) {
            TerrainRegistry.add(WorldGenKind.TERRAIN_SOURCE, source);
            TerrainRegistry.add(WorldGenKind.TERRAIN_OVERRIDE, override);
            batch.validate();
            batch.publish();
        }
        Save baselineSave = new Save(Files.createTempDirectory("betamoon-native-override-base-"));
        Fixture baseline = new Fixture(baselineSave, seed);
        baseline.isNewWorld = true;
        try (WorldCreationSelection pending = WorldCreationSelection.begin(
                GenerationSelection.create(new GenerationDraft(new GenerationCatalog(null)), seed))) {
            baseline.createOuterProvider();
        }
        Chunk expected = baseline.worldProvider.getChunkProvider().provideChunk(0, 0);
        Save save = new Save(Files.createTempDirectory("betamoon-native-override-"));
        Fixture world = new Fixture(save, seed);
        world.isNewWorld = true;
        GenerationDraft draft = new GenerationDraft(new GenerationCatalog(null));
        require(!draft.selected("override/fixture:terrain_override/high_plains"),
                "Override packages must begin disabled");
        draft.toggle("override/fixture:terrain_override/high_plains");
        try (WorldCreationSelection pending = WorldCreationSelection.begin(GenerationSelection.create(draft, seed))) {
            world.createOuterProvider();
        }
        require(WorldGenerationRuntime.overrides(world).size() == 1, "Selected override was not pinned to the world");
        Chunk actual = world.worldProvider.getChunkProvider().provideChunk(0, 0);
        require((actual.blocks[(1 << 11) | (1 << 7) | 80] & 255) == Block.cobblestone.blockID
                        && (actual.blocks[(1 << 11) | (1 << 7) | 100] & 255) == Block.blockGold.blockID
                        && expected.blocks[(1 << 11) | (1 << 7) | 100] == 0,
                "Native Plains override did not apply terrain, underground material and custom surface");
        require(!WorldGenerationRuntime.overridePopulationEnabled(world, "ores", 1, 1, true)
                        && WorldGenerationRuntime.overridePopulationEnabled(world, "ores", 14, 1, true),
                "Scoped native population suppression ignored the region boundary");
        Map<String, Object> point = WorldGenerationRuntime.inspectOverrides(world, 1, 1);
        require(((List<?>) point.get("packages")).size() == 1
                        && point.get("biome") != null,
                "Point diagnostics omitted the pinned override or saved biome");
        for (int z = 0; z < 16; z++) {
            for (int y = 0; y < 128; y++) {
                int index = (14 << 11) | (z << 7) | y;
                require(actual.blocks[index] == expected.blocks[index],
                        "Native raw output changed outside the override region");
            }
        }
        world.selectInitialSpawn();
        Fixture reopened = new Fixture(save, seed);
        reopened.createOuterProvider();
        require(WorldGenerationRuntime.overrides(reopened).size() == 1
                        && chunkHash(actual).equals(chunkHash(reopened.worldProvider.getChunkProvider()
                                .provideChunk(0, 0))),
                "Saved override selection did not reproduce the native chunk after reopening");
        nativeCaveOnlyOverride(seed, baseline);
        TerrainRegistry.retainOwners(Collections.<String>emptySet());
        BiomeGenRegistry.retainOwners(Collections.<String>emptySet());
    }

    private static void nativeCaveOnlyOverride(long seed, Fixture baseline) throws Exception {
        Map<String, Object> cave = new LinkedHashMap<String, Object>();
        cave.put("key", "fixture:carver/negative_cavern");
        cave.put("type", "field");
        cave.put("field", 0.0);
        cave.put("min", 0.0);
        cave.put("max", 0.0);
        cave.put("minY", 40.0);
        cave.put("maxY", 60.0);
        cave.put("avoidFluids", false);
        Map<String, Object> region = new LinkedHashMap<String, Object>();
        region.put("minX", -16.0);
        region.put("maxX", -9.0);
        region.put("minZ", 0.0);
        region.put("maxZ", 15.0);
        Map<String, Object> channel = new LinkedHashMap<String, Object>();
        channel.put("mode", "add");
        channel.put("entries", Collections.singletonList("fixture:carver/negative_cavern"));
        Map<String, Object> override = new LinkedHashMap<String, Object>();
        override.put("key", "fixture:terrain_override/negative_cavern");
        override.put("bases", Collections.singletonList("minecraft"));
        override.put("region", region);
        override.put("carvers", channel);
        try (TerrainRegistry.Batch batch = TerrainRegistry.begin("override_cave_fixture.lua")) {
            TerrainRegistry.add(WorldGenKind.CARVER, cave);
            TerrainRegistry.add(WorldGenKind.TERRAIN_OVERRIDE, override);
            batch.publish();
        }
        GenerationDraft draft = new GenerationDraft(new GenerationCatalog(null));
        draft.toggle("override/fixture:terrain_override/negative_cavern");
        Save save = new Save(Files.createTempDirectory("betamoon-negative-cavern-"));
        Fixture world = new Fixture(save, seed);
        world.isNewWorld = true;
        try (WorldCreationSelection pending = WorldCreationSelection.begin(GenerationSelection.create(draft, seed))) {
            world.createOuterProvider();
        }
        Chunk original = baseline.worldProvider.getChunkProvider().provideChunk(-1, 0);
        Chunk carved = world.worldProvider.getChunkProvider().provideChunk(-1, 0);
        int removed = 0;
        for (int z = 0; z < 16; z++) {
            for (int y = 40; y <= 60; y++) {
                int inside = (1 << 11) | (z << 7) | y;
                if (original.blocks[inside] != 0 && carved.blocks[inside] == 0) {
                    removed++;
                }
            }
            for (int y = 0; y < 128; y++) {
                int outside = (14 << 11) | (z << 7) | y;
                require(original.blocks[outside] == carved.blocks[outside],
                        "Cave-only override changed a native column outside its negative-coordinate target");
            }
        }
        require(removed > 0, "Cave-only override did not carve selected native columns");
    }

    private static void ownedBiomeOverride() throws Exception {
        Map<String, Object> base = new LinkedHashMap<String, Object>();
        base.put("key", "fixture:terrain_source/base_plain");
        base.put("type", "heightmap");
        base.put("height", 64.0);
        base.put("solid", 1.0);
        Map<String, Object> raised = new LinkedHashMap<String, Object>();
        raised.put("key", "fixture:terrain_source/raised_wool");
        raised.put("type", "heightmap");
        raised.put("height", 80.0);
        Map<String, Object> wool = new LinkedHashMap<String, Object>();
        wool.put("block", (double) Block.cloth.blockID);
        wool.put("metadata", 4.0);
        raised.put("solid", wool);
        Map<String, Object> dimension = new LinkedHashMap<String, Object>();
        dimension.put("terrain", "fixture:terrain_source/base_plain");
        dimension.put("surfaces", "none");
        Map<String, Object> generator = new LinkedHashMap<String, Object>();
        generator.put("key", "fixture:generator/plain_base");
        generator.put("dimensions", Collections.singletonMap("overworld", dimension));
        Map<String, Object> region = new LinkedHashMap<String, Object>();
        region.put("minX", 0.0);
        region.put("maxX", 7.0);
        region.put("minZ", 0.0);
        region.put("maxZ", 15.0);
        Map<String, Object> override = new LinkedHashMap<String, Object>();
        override.put("key", "fixture:terrain_override/owned_wool");
        override.put("bases", Collections.singletonList("fixture:generator/plain_base"));
        override.put("region", region);
        Map<String, Object> channel = new LinkedHashMap<String, Object>();
        channel.put("mode", "replace");
        channel.put("source", "fixture:terrain_source/raised_wool");
        override.put("terrain", channel);
        try (TerrainRegistry.Batch batch = TerrainRegistry.begin("owned_override_fixture.lua")) {
            TerrainRegistry.add(WorldGenKind.TERRAIN_SOURCE, base);
            TerrainRegistry.add(WorldGenKind.TERRAIN_SOURCE, raised);
            TerrainRegistry.add(WorldGenKind.GENERATOR, generator);
            TerrainRegistry.add(WorldGenKind.TERRAIN_OVERRIDE, override);
            batch.publish();
        }
        WorldGeneratorProfile profile = GeneratorProfileRegistry.snapshot().get("fixture:generator/plain_base");
        GenerationDraft draft = new GenerationDraft(new GenerationCatalog(profile));
        draft.toggle("override/fixture:terrain_override/owned_wool");
        Save save = new Save(Files.createTempDirectory("betamoon-owned-override-"));
        Fixture world = new Fixture(save, 287);
        world.isNewWorld = true;
        try (WorldCreationSelection pending = WorldCreationSelection.begin(GenerationSelection.create(draft, 287))) {
            world.createOuterProvider();
        }
        Chunk chunk = world.worldProvider.getChunkProvider().provideChunk(0, 0);
        require((chunk.blocks[(1 << 11) | (1 << 7) | 75] & 255) == Block.cloth.blockID
                        && chunk.data.getNibble(1, 75, 1) == 4
                        && chunk.blocks[(14 << 11) | (1 << 7) | 75] == 0,
                "Owned terrain override lost selected occupancy, metadata or outside columns");
        TerrainRegistry.retainOwners(Collections.<String>emptySet());
    }

    private static void overrideContracts() throws Exception {
        Map<String, Object> surface = new LinkedHashMap<String, Object>();
        surface.put("mode", "none");
        Map<String, Object> first = new LinkedHashMap<String, Object>();
        first.put("key", "fixture:terrain_override/first");
        first.put("bases", Collections.singletonList("minecraft"));
        first.put("surfaces", surface);
        Map<String, Object> region = new LinkedHashMap<String, Object>();
        region.put("minX", 0.0);
        region.put("maxX", 15.0);
        region.put("minZ", 0.0);
        region.put("maxZ", 15.0);
        first.put("region", region);
        Map<String, Object> blend = new LinkedHashMap<String, Object>();
        blend.put("width", 4.0);
        first.put("blend", blend);
        Map<String, Object> second = new LinkedHashMap<String, Object>();
        second.put("key", "fixture:terrain_override/second");
        second.put("bases", Collections.singletonList("minecraft"));
        second.put("surfaces", surface);
        publishOverrides(first, second);
        Fixture sample = new Fixture(null, 41);
        BiomeRegionOverride boundary = TerrainRegistry.snapshot().override("fixture:terrain_override/first");
        require(boundary.influence(WorldGenerationRuntime.fieldContext(sample), -1, 5) == 0
                        && boundary.influence(WorldGenerationRuntime.fieldContext(sample), 0, 5) > 0
                        && boundary.influence(WorldGenerationRuntime.fieldContext(sample), 3, 5) == 1,
                "Inside blending did not preserve the outside or reach full influence");
        GenerationDraft conflicting = new GenerationDraft(new GenerationCatalog(null));
        conflicting.toggle("override/fixture:terrain_override/first");
        conflicting.toggle("override/fixture:terrain_override/second");
        expect(IllegalArgumentException.class, () -> GenerationSelection.create(conflicting, 41));

        first.put("before", Collections.singletonList("fixture:terrain_override/second"));
        publishOverrides(first, second);
        GenerationDraft ordered = new GenerationDraft(new GenerationCatalog(null));
        ordered.toggle("override/fixture:terrain_override/first");
        ordered.toggle("override/fixture:terrain_override/second");
        GenerationSelection selection = GenerationSelection.create(ordered, 41);
        require(selection.overrideOrder.equals(Arrays.asList("fixture:terrain_override/first",
                        "fixture:terrain_override/second")), "Explicit override ordering was not saved");
        Path path = Files.createTempFile("betamoon-override-order-", ".dat");
        GenerationSelectionStore store = new GenerationSelectionStore(path.toFile());
        store.write(selection);
        require(store.read().overrideOrder.equals(selection.overrideOrder),
                "Override ordering did not survive schema 3 reload");
        Map<String, Object> later = new LinkedHashMap<String, Object>();
        later.put("key", "fixture:terrain_override/installed_later");
        later.put("bases", Collections.singletonList("minecraft"));
        later.put("surfaces", surface);
        try (TerrainRegistry.Batch batch = TerrainRegistry.begin("later_override_contracts.lua")) {
            TerrainRegistry.add(WorldGenKind.TERRAIN_OVERRIDE, later);
            batch.publish();
        }
        selection.validate(41);
        require(!selection.enabled("override/fixture:terrain_override/installed_later"),
                "A newly installed override activated in a saved world");
        first.put("displayName", "Renamed for players");
        publishOverrides(first, second);
        selection.validate(41);
        first.put("priority", 2.0);
        publishOverrides(first, second);
        expect(IllegalStateException.class, () -> store.read().validate(41));
        TerrainRegistry.retainOwners(Collections.singleton("later_override_contracts.lua"));
        expect(IllegalStateException.class, () -> store.read().validate(41));
        TerrainRegistry.retainOwners(Collections.<String>emptySet());
    }

    private static void publishOverrides(Map<String, Object> first, Map<String, Object> second) {
        try (TerrainRegistry.Batch batch = TerrainRegistry.begin("override_contracts.lua")) {
            TerrainRegistry.add(WorldGenKind.TERRAIN_OVERRIDE, first);
            TerrainRegistry.add(WorldGenKind.TERRAIN_OVERRIDE, second);
            batch.publish();
        }
    }

    private static WorldCreationSelection choose(WorldGeneratorProfile profile, long seed) {
        return WorldCreationSelection
                .begin(GenerationSelection.create(new GenerationDraft(new GenerationCatalog(profile)), seed));
    }

    private static void expect(Class<? extends Exception> type, CheckedAction action) {
        try {
            action.run();
        } catch (Exception exception) {
            if (type.isInstance(exception)) {
                return;
            }
            throw new AssertionError("Unexpected failure", exception);
        }
        throw new AssertionError("Expected " + type.getSimpleName());
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private interface CheckedAction {
        void run() throws Exception;
    }

    public static final class Fixture extends World {
        public Fixture(Save save, long seed) {
            super(save, "terrain-fixture", new WorldProvider() {
                @Override
                public boolean canCoordinateBeSpawn(int x, int z) {
                    return x == 0 && z == 0;
                }
            }, seed);
        }

        @Override
        protected IChunkProvider getChunkProvider() {
            return null;
        }

        void createOuterProvider() {
            super.getChunkProvider();
        }

        void selectInitialSpawn() {
            super.getInitialSpawnLocation();
        }

        @Override
        public int getHeightValue(int x, int z) {
            return 0;
        }

        @Override
        public void scheduleLightingUpdate(EnumSkyBlock kind, int minX, int minY, int minZ, int maxX, int maxY,
                int maxZ) {
            // The native raw-chunk fixture intentionally has no loaded neighboring world.
        }
    }

    public static final class Save implements ISaveHandler {
        private final Path directory;
        private int locks;

        public Save(Path directory) {
            this.directory = directory;
        }

        public WorldInfo loadWorldInfo() {
            return null;
        }

        public void func_22150_b() {
            locks++;
        }

        public IChunkLoader getChunkLoader(WorldProvider provider) {
            return null;
        }

        public void saveWorldInfoAndPlayer(WorldInfo info, List players) {
        }

        public void saveWorldInfo(WorldInfo info) {
        }

        public File func_28113_a(String name) {
            return directory.resolve(name + ".dat").toFile();
        }
    }

    private static final class CountingProvider implements IChunkProvider {
        private int population;
        private int provide;
        private int prepare;
        private int saves;

        public boolean chunkExists(int x, int z) {
            return false;
        }

        public Chunk provideChunk(int x, int z) {
            provide++;
            return null;
        }

        public Chunk prepareChunk(int x, int z) {
            prepare++;
            return null;
        }

        public void populate(IChunkProvider provider, int x, int z) {
            population++;
        }

        public boolean saveChunks(boolean all, IProgressUpdate progress) {
            saves++;
            return true;
        }

        public boolean unload100OldestChunks() {
            return false;
        }

        public boolean canSave() {
            return true;
        }

        public String makeString() {
            return "RandomLevelSource";
        }
    }
}
