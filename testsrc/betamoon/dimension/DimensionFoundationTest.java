package betamoon.dimension;

import betamoon.assets.model.ModelJson;
import betamoon.dimension.storage.CheckedDimensionRecord;
import betamoon.dimension.storage.DimensionIndexStore;
import betamoon.dimension.storage.DimensionMetadataStore;
import betamoon.dimension.storage.DimensionNbt;
import betamoon.dimension.storage.DimensionPaths;
import betamoon.worldgen.BiomeGenRegistry;
import betamoon.worldgen.WorldGenKey;
import betamoon.worldgen.WorldGenKind;
import betamoon.worldgen.biome.LuaBiome;
import betamoon.worldgen.BlockSet;
import betamoon.worldgen.IntRange;
import betamoon.worldgen.surface.SurfaceRuleSet;
import betamoon.worldgen.terrain.ProfileSurfaceRules;
import betamoon.worldgen.terrain.TerrainRegistry;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import net.minecraft.src.NBTTagCompound;
import net.minecraft.src.BiomeGenBase;
import net.minecraft.src.WorldInfo;

/** Storage/identity gates before custom world creation is exposed. */
public final class DimensionFoundationTest {
    public static void main(String[] args) throws Exception {
        declarations();
        biomeSnapshots();
        allocation();
        records();
        metadata();
        nbtBounds();
        System.out
                .println("Dimension foundations passed: strict declarations, exact seeds, stable IDs, checked records, "
                        + "isolated metadata and bounded NBT.");
    }

    private static void declarations() throws Exception {
        DimensionDefinition first = definition("{\"key\":\"test:moon\",\"name\":\"Moon\","
                + "\"generator\":{\"type\":\"builtin\",\"name\":\"nether\"}}");
        require(first.key.toString().equals("test:dimension/moon") && first.environment.skylight
                && !first.environment.waterEvaporates && first.coordinateScale == 1,
                "Nether terrain must not implicitly select Nether environment or scaling");
        require(first.seed(42) == first.seed(42) && first.seed(42) != 42, "Derived seeds are stable and independent");
        require(DimensionDefinition.exactLong("-9223372036854775808", "seed") == Long.MIN_VALUE,
                "String seeds preserve all signed 64-bit bits");
        failure(() -> DimensionDefinition.exactLong(9007199254740992.0, "seed"));
        failure(() -> DimensionKey.parse("test:dimension/../outside"));
        failure(() -> DimensionKey.parse("minecraft:custom"));
        failure(() -> definition("{\"key\":\"minecraft:overworld\",\"generator\":{}}"));
        failure(() -> definition("{\"key\":\"test:bad\",\"generator\":{},\"unknown\":1}"));
        DimensionDefinition cosmetic = definition("{\"key\":\"test:moon\",\"displayName\":\"New Moon\","
                + "\"generator\":{\"type\":\"builtin\",\"name\":\"nether\"}}");
        require(first.signature(null, null).equals(cosmetic.signature(null, null)),
                "Presentation labels must not change generation compatibility");
        DimensionDefinition expanded = definition(
                "{\"key\":\"test:dimension/moon\",\"displayName\":\"Moon\",\"name\":9,"
                        + "\"generator\":{\"type\":\"builtin\",\"name\":\"nether\"},\"coordinateScale\":1,\"initialTime\":\"0\","
                        + "\"seed\":{\"type\":\"derived\",\"salt\":\"\"},\"environment\":{\"preset\":\"surface\"},"
                        + "\"spawn\":{\"type\":\"search\",\"x\":0,\"z\":0,\"radius\":64,\"yaw\":360,\"pitch\":0,"
                        + "\"fallback\":{\"type\":\"fail\"}},\"respawn\":{\"type\":\"dimension\",\"dimension\":\"overworld\"}}");
        require(first.signature(null, null).equals(expanded.signature(null, null)),
                "Canonical aliases and explicit defaults must preserve compatibility; displayName takes precedence");
        DimensionDefinition roundTrip = new DimensionDefinition("other.lua", first.declaration);
        require(roundTrip.signature(null, null).equals(first.signature(null, null)),
                "Effective descriptions must be complete, detached declaration values");
        try (DimensionRegistry.Batch batch = DimensionRegistry.begin("one.lua")) {
            DimensionRegistry.add(json("{\"key\":\"test:moon\",\"generator\":{}}"));
            batch.validate();
            batch.publish();
        }
        failure(() -> {
            try (DimensionRegistry.Batch batch = DimensionRegistry.begin("two.lua")) {
                DimensionRegistry.add(json("{\"key\":\"test:moon\",\"generator\":{}}"));
                batch.validate();
                batch.publish();
            }
        });
        require(DimensionRegistry.get(first.key).owner.equals("one.lua"), "Failed publication preserves the catalog");
        DimensionRegistry.retainOwners(Collections.<String>emptySet());
    }

    private static void biomeSnapshots() throws Exception {
        LuaBiome first = new LuaBiome("Old meadow");
        first.applyDefaultSurface();
        try (BiomeGenRegistry.PublicationBatch batch = BiomeGenRegistry.beginPublication("biomes.lua", "test")) {
            WorldGenKey surface = BiomeGenRegistry.registerSurface("test:soil",
                    (key, resourceOwner, owner, source) -> new SurfaceRuleSet(key, resourceOwner, owner, source,
                            new BlockSet(new int[]{1}),
                            Collections.singletonList(new SurfaceRuleSet.Layer(41, new IntRange(1, 1))), null, 0));
            BiomeGenRegistry.registerBiomeGenerator("test:meadow", first, Collections.singleton("old"), surface, 0, 0.4,
                    0, 1, true, 0);
            batch.validate();
            batch.publish();
        }
        DimensionDefinition definition = definition("{\"key\":\"test:biomes\",\"generator\":{}}");
        DimensionGenerationBinding original = new DimensionGenerationBinding(definition, TerrainRegistry.snapshot());
        Map<String, String> pinned = original.biomes.dependencies(null, "overworld");
        ProfileSurfaceRules surfaces = new ProfileSurfaceRules().withCatalog(original.biomes);
        BiomeGenBase[] samples = new BiomeGenBase[256];
        Arrays.fill(samples, original.biomes.biome(WorldGenKey.parse("test:meadow", WorldGenKind.BIOME)));
        byte[] terrain = new byte[32768];
        terrain[63] = 1;
        surfaces.applyDeclared(42, 0, 0, terrain, samples);
        require(terrain[63] == 41, "A cloned biome must retain its registered surface rules");
        try (BiomeGenRegistry.PublicationBatch batch = BiomeGenRegistry.beginPublication("unrelated.lua", "other")) {
            LuaBiome unused = new LuaBiome("Unused");
            unused.applyDefaultSurface();
            BiomeGenRegistry.registerBiomeGenerator("other:unused", unused, Collections.<String>emptySet(), null, 0, 1,
                    0, 1, false, 0);
            batch.validate();
            batch.publish();
        }
        DimensionGenerationBinding unrelated = new DimensionGenerationBinding(definition, TerrainRegistry.snapshot());
        require(original.signature.equals(unrelated.signature),
                "Unused biome declarations cannot invalidate a dimension");
        LuaBiome replacement = new LuaBiome("New meadow");
        replacement.applyDefaultSurface();
        try (BiomeGenRegistry.PublicationBatch batch = BiomeGenRegistry.beginPublication("biomes.lua", "test")) {
            BiomeGenRegistry.registerBiomeGenerator("test:meadow", replacement, Collections.singleton("new"), null, 0.5,
                    1, 0, 1, true, 0);
            batch.validate();
            batch.publish();
        }
        DimensionGenerationBinding changed = new DimensionGenerationBinding(definition, TerrainRegistry.snapshot());
        require(!original.signature.equals(changed.signature),
                "Climate distribution changes must invalidate saved generation");
        first.applyTopBlock(1);
        first.applyBigTreeChance(25);
        require(original.biomes.biome(WorldGenKey.parse("test:meadow", WorldGenKind.BIOME)) != first
                && original.biomes.biome(WorldGenKey.parse("test:meadow", WorldGenKind.BIOME)).topBlock != 1
                && original.biomes.dependencies(null, "overworld").equals(pinned),
                "Pinned biome identity, distribution and dependency descriptions cannot follow later publications");
        BiomeGenRegistry.clear();
    }

    private static void allocation() throws Exception {
        Path root = Files.createTempDirectory("betamoon-dimension-ids-");
        DimensionPaths paths = new DimensionPaths(root);
        DimensionIndexStore index = new DimensionIndexStore(paths);
        DimensionKey first = DimensionKey.parse("test:one");
        DimensionKey second = DimensionKey.parse("test:two");
        require(index.allocate(first) == 2 && index.allocate(second) == 3, "Reserve native IDs -1, 0 and 1");
        DimensionIndexStore reopened = new DimensionIndexStore(paths);
        require(reopened.allocate(second) == 3 && reopened.allocate(first) == 2
                && reopened.allocate(DimensionKey.parse("other:one")) == 4,
                "Changing registration order or namespace must not reassign saved IDs");
        require(reopened.resolve(2).equals(first) && reopened.resolve(1) == null,
                "Unknown native Sky IDs must not be guessed as custom dimensions");
        failure(() -> paths.resolve("../escape"));
        Path link = root.resolve("linked");
        try {
            Files.createSymbolicLink(link, Files.createTempDirectory("betamoon-dimension-outside-"));
            failure(() -> paths.resolve("linked/stolen.dat"));
        } catch (UnsupportedOperationException | FileSystemException unavailable) {
            // Creating symlinks requires an OS capability; path/bounds checks still run
            // above.
        }
    }

    private static void records() throws Exception {
        Path root = Files.createTempDirectory("betamoon-dimension-records-");
        CheckedDimensionRecord record = new CheckedDimensionRecord(new DimensionPaths(root), "betamoon/check.dat", 91,
                1024);
        require(record.read() == null, "Absent records are distinct from corrupt records");
        record.write(new byte[]{1, 2, 3});
        require(record.read()[2] == 3, "Checked records round trip");
        Path file = root.resolve("betamoon/check.dat");
        byte[] valid = Files.readAllBytes(file);
        for (int length = 0; length < valid.length; length++) {
            Files.write(file, Arrays.copyOf(valid, length));
            failure(() -> record.read());
        }
        valid[12] ^= 1;
        Files.write(file, valid);
        failure(() -> record.read());
        failure(() -> record.write(new byte[1025]));
    }

    private static void metadata() throws Exception {
        DimensionPaths paths = new DimensionPaths(Files.createTempDirectory("betamoon-dimension-meta-"));
        DimensionGenerationBinding first = new DimensionGenerationBinding(
                definition("{\"key\":\"test:one\",\"generator\":{},\"initialTime\":200}"), TerrainRegistry.snapshot());
        DimensionGenerationBinding second = new DimensionGenerationBinding(
                definition("{\"key\":\"test:two\",\"generator\":{}}"), TerrainRegistry.snapshot());
        DimensionMetadataStore one = new DimensionMetadataStore(paths, first, 2, 42);
        DimensionMetadataStore two = new DimensionMetadataStore(paths, second, 3, 42);
        one.initialize(-1.5, 65, 2.25, 37, -12);
        two.initialize(17, 70, -18, 0, 0);
        WorldInfo info = one.worldInfo();
        info.setWorldTime(1234);
        info.setSpawn(99, 80, 99);
        info.setRaining(true);
        info.setPlayerNBTTagCompound(new NBTTagCompound());
        one.save(info);
        DimensionMetadataStore reopened = new DimensionMetadataStore(paths, first, 2, 42);
        require(reopened.anchorX() == -2 && reopened.anchorZ() == 2 && reopened.entryPosition()[0] == -1.5,
                "Original integer anchors must use floor and preserve precise entry positions");
        require(reopened.worldInfo().getWorldTime() == 1234 && reopened.worldInfo().getSpawnX() == 99
                && reopened.worldInfo().getPlayerNBTTagCompound() == null && two.worldInfo().getWorldTime() == 0,
                "Mutable world state is local and must not own root player data");
        failure(() -> new DimensionMetadataStore(paths, first, 3, 42));
        failure(() -> new DimensionMetadataStore(paths, first, 2, 43));
    }

    private static void nbtBounds() throws Exception {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("hello", "world");
        require(DimensionNbt.copy(tag, 1024).getString("hello").equals("world"), "Bounded native NBT round trips");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            output.writeByte(10);
            output.writeUTF("");
            output.writeByte(7);
            output.writeUTF("bomb");
            output.writeInt(Integer.MAX_VALUE);
        }
        failure(() -> DimensionNbt.decode(bytes.toByteArray(), 1024));
    }

    private static Map<String, Object> json(String source) throws Exception {
        return ModelJson.read(source.getBytes(StandardCharsets.UTF_8));
    }

    private static DimensionDefinition definition(String source) throws Exception {
        return new DimensionDefinition("test.lua", json(source));
    }

    private static void failure(Checked action) throws Exception {
        try {
            action.run();
        } catch (IllegalArgumentException | IllegalStateException | IOException expected) {
            return;
        }
        throw new AssertionError("Expected validation/storage failure");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private interface Checked {
        void run() throws Exception;
    }
}
