package betamoon.worldgen.terrain;

import betamoon.instrumentation.agent.AgentRuntime;
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
        if (AgentRuntime.isRegistered()) {
            instrumentedBoundaries();
        }
        System.out.println("Terrain foundation passed: atomic manifest/reopen/corruption, signatures, "
                + "raw metadata/heightmaps, native hashes and provider routing.");
    }

    private static WorldGeneratorProfile profile(String key, String owner, boolean active, int priority) {
        return new WorldGeneratorProfile(key, owner, active, priority, Collections.<String, String>emptyMap());
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
        require(snapshot.defaultForNewWorld() == high, "Highest priority chooses new-world default");
        expect(IllegalArgumentException.class, () -> GeneratorProfileRegistry.replaceOwner(owner,
                Arrays.asList(low, profile("fixture:generator/tie", owner, true, 1))));
        require(GeneratorProfileRegistry.snapshot() == snapshot, "Failed publication must retain active snapshot");
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
        WorldGeneratorProfile changed = new WorldGeneratorProfile(profile.key, profile.owner, true, 1,
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
        WorldGenerationRuntime.prepare(world, save);
        require(WorldGenerationRuntime.manifest(world) != null && save.locks == 1,
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
        world.createOuterProvider();
        require(WorldGenerationRuntime.manifest(world) != null, "World factory hook must capture its save handler");
        require(world.worldProvider.getChunkProvider() instanceof BetaMoonChunkProvider,
                "Native factory return hook must select the pinned provider");
        world.selectInitialSpawn();
        require(WorldGenerationRuntime.manifest(world).originalSpawn != null,
                "Actual native initial-spawn boundary must persist the original anchor");
        GeneratorProfileRegistry.replaceOwner(profile.owner, Collections.<WorldGeneratorProfile>emptyList());
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

    static final class Fixture extends World {
        Fixture(Save save, long seed) {
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

    static final class Save implements ISaveHandler {
        private final Path directory;
        private int locks;

        Save(Path directory) {
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
