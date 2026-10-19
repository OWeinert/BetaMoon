package betamoon.worldgen.terrain;

import betamoon.assets.model.ModelJson;
import betamoon.worldgen.BiomeGenRegistry;
import betamoon.worldgen.spatial.FieldContext;
import betamoon.worldgen.spatial.FieldTrace;
import betamoon.worldgen.spatial.SpatialRegistry;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.src.BiomeGenBase;

/**
 * Output and work-count regressions for early masks and immutable fluid-safety
 * snapshots.
 */
public final class TerrainGenerationOptimizationTest {
    public static void verify() throws Exception {
        filteredFieldWork();
        composedAndShapedMasks();
        fluidBoundaries();
        neighborhoodReuse();
        cacheLifetime();
        completeBaseReuse();
    }

    private static void filteredFieldWork() throws Exception {
        FieldTrace trace = new FieldTrace();
        FieldContext fields = new FieldContext(42, 0).withTrace(trace);
        BiomeGenBase[] biomes = new BiomeGenBase[256];
        Arrays.fill(biomes, BiomeGenBase.desert);
        CompiledCarver vertical = carver(
                "{\"type\":\"field\",\"field\":{\"type\":\"y\"}," + "\"min\":13,\"max\":17,\"minY\":10,\"maxY\":20,"
                        + "\"biomes\":{\"include\":[\"Plains\"]},\"avoidFluids\":false}");
        vertical.apply(new GenerationContext(fields, 0, 0, biomes), solid());
        require(trace.samples().isEmpty(), "Rejected columns must not evaluate the cave field");

        biomes[5 * 16 + 7] = BiomeGenBase.plains;
        ChunkBlockBuffer carved = solid();
        vertical.apply(new GenerationContext(fields, 0, 0, biomes), carved);
        require(trace.samples().size() == 11 && !trace.truncated(),
                "A Y-dependent field must sample only the eligible column and inclusive Y range");
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = 0; y < 128; y++) {
                    boolean selected = x == 5 && z == 7 && y >= 13 && y <= 17;
                    require(carved.blockId(x, y, z) == (selected ? 0 : 1), "Early filters must preserve exact masks");
                }
            }
        }

        trace = new FieldTrace();
        fields = new FieldContext(42, 0).withTrace(trace);
        carved = solid();
        carver("{\"type\":\"field\",\"field\":{\"type\":\"x\"},\"min\":5,\"max\":5,"
                + "\"minY\":10,\"maxY\":20,\"biomes\":{\"include\":[\"Plains\"]},\"avoidFluids\":false}")
                .apply(new GenerationContext(fields, 0, 0, biomes), carved);
        require(trace.samples().size() == 1, "A Y-independent field must evaluate once per eligible column");
        require(carved.blockId(5, 9, 7) == 1 && carved.blockId(5, 10, 7) == 0 && carved.blockId(5, 20, 7) == 0
                && carved.blockId(5, 21, 7) == 1, "Column-wide masks must preserve both inclusive vertical endpoints");
    }

    private static void composedAndShapedMasks() throws Exception {
        BiomeGenBase[] biomes = new BiomeGenBase[256];
        for (int column = 0; column < 256; column++) {
            biomes[column] = column % 3 == 0 ? BiomeGenBase.plains : BiomeGenBase.desert;
        }
        GenerationContext context = new GenerationContext(new FieldContext(-817, 0), -1, -2, biomes);
        for (String operation : new String[]{"union", "intersection"}) {
            CompiledCarver composed = carver("{\"type\":\"compose\",\"operation\":\"" + operation
                    + "\",\"biomes\":{\"exclude\":[\"Desert\"]},\"avoidFluids\":false,\"children\":["
                    + "{\"type\":\"field\",\"field\":{\"type\":\"y\"},\"min\":20,\"max\":30},"
                    + "{\"type\":\"compose\",\"children\":[{\"type\":\"field\",\"field\":{\"type\":\"y\"},"
                    + "\"min\":25,\"max\":35,\"biomes\":{\"include\":[\"Plains\"]}}]}]}");
            ChunkBlockBuffer result = solid();
            composed.apply(context, result);
            for (int column = 0; column < 256; column++) {
                for (int y = 0; y < 128; y++) {
                    boolean selected = column % 3 == 0 && y >= (operation.equals("union") ? 20 : 25)
                            && y <= (operation.equals("union") ? 35 : 30);
                    require(result.blockId(column >> 4, y, column & 15) == (selected ? 0 : 1),
                            "Parent filtering must distribute through nested unions and intersections");
                }
            }
        }
        for (String type : new String[]{"tunnel", "room", "ravine"}) {
            String shape = "\"type\":\"" + type + "\",\"probability\":1,\"radius\":3,"
                    + "\"minY\":20,\"maxY\":40,\"avoidFluids\":false";
            ChunkBlockBuffer unfiltered = solid();
            ChunkBlockBuffer filtered = solid();
            carver("{" + shape + "}").apply(context, unfiltered);
            carver("{" + shape + ",\"biomes\":{\"include\":[\"Plains\"]}}").apply(context, filtered);
            int carved = 0;
            for (int column = 0; column < 256; column++) {
                for (int y = 0; y < 128; y++) {
                    int expected = column % 3 == 0 ? unfiltered.blockId(column >> 4, y, column & 15) : 1;
                    require(filtered.blockId(column >> 4, y, column & 15) == expected,
                            "Early shape clipping must preserve origin RNG and geometry: " + type);
                    carved += expected == 0 ? 1 : 0;
                }
            }
            require(carved > 0, "Shape parity must exercise a nonempty mask: " + type);
        }
    }

    private static void fluidBoundaries() throws Exception {
        CountingSource source = new CountingSource();
        RawTerrainCache cache = new RawTerrainCache();
        CompiledCarver carver = carver("{\"type\":\"field\",\"field\":4,\"minY\":4,\"maxY\":4,"
                + "\"replaceable\":[35],\"avoidFluids\":true}");
        FieldContext fields = new FieldContext(42, 0);
        int[][] coordinates = {{-1, -1}, {0, -1}, {0, 0}, {-1, 0}, {1, 0}, {0, 0}, {-1, -1}};
        for (int[] coordinate : coordinates) {
            GenerationContext context = GenerationContext.preview(fields, coordinate[0], coordinate[1]);
            ChunkBlockBuffer buffer = new ChunkBlockBuffer();
            source.fill(context, buffer);
            context.rawNeighborhood = new RawGenerationNeighborhood(source, context, buffer, cache);
            carver.apply(context, buffer);
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    int wx = context.worldX(x);
                    int wz = context.worldZ(z);
                    boolean fluid = source.fluid(wx, wz);
                    boolean touches = fluid || source.fluid(wx - 1, wz) || source.fluid(wx + 1, wz)
                            || source.fluid(wx, wz - 1) || source.fluid(wx, wz + 1);
                    require(buffer.blockId(x, 4, z) == (fluid ? 9 : touches ? 35 : 0),
                            "Fluid safety must preserve initial occupancy across chunk edges and negative coordinates");
                    require(buffer.metadata(x, 4, z) == (!fluid && touches ? 7 : 0),
                            "Cached fluid checks must preserve solid metadata and clear carved metadata");
                }
            }
        }
        GenerationContext edge = GenerationContext.preview(fields, 1874999, -1875000);
        ChunkBlockBuffer buffer = new ChunkBlockBuffer();
        source.fill(edge, buffer);
        RawGenerationNeighborhood neighborhood = new RawGenerationNeighborhood(source, edge, buffer, cache);
        require(neighborhood.block(30000000, 4, -30000000) == 0 && neighborhood.block(29999999, 4, -30000001) == 0
                && neighborhood.block(29999999, -1, -30000000) == 0
                && neighborhood.block(29999999, 128, -30000000) == 0,
                "World and vertical limits must remain air without generating outside the world");
        try {
            neighborhood.block(29999983, 4, -29999984);
            throw new AssertionError("Diagonal neighbor access must remain rejected");
        } catch (IllegalArgumentException expected) {
            require(expected.getMessage().contains("boundary contract"), "Retain bounded neighbor validation");
        }
    }

    private static void neighborhoodReuse() {
        CountingSource source = new CountingSource();
        FieldContext fields = new FieldContext(42, 0);
        RawTerrainCache cache = new RawTerrainCache();
        GenerationContext context = GenerationContext.preview(fields, 0, 0);
        ChunkBlockBuffer buffer = new ChunkBlockBuffer();
        source.fill(context, buffer);
        RawGenerationNeighborhood first = new RawGenerationNeighborhood(source, context, buffer, cache);
        buffer.set(0, 4, 0, 0, 0);
        require(first.block(0, 4, 0) == 9, "Carving must not mutate the published base snapshot");
        first.block(16, 4, 0);
        first.block(16, 5, 1);
        require(source.fills == 2, "A neighboring base chunk is synthesized at most once per neighborhood");
        context = GenerationContext.preview(fields, 2, 0);
        source.fill(context, buffer);
        RawGenerationNeighborhood second = new RawGenerationNeighborhood(source, context, buffer, cache);
        second.block(31, 4, 0);
        require(source.fills == 3, "Consecutive chunks must reuse their shared immutable neighbor");
        RawGenerationNeighborhood independent = new RawGenerationNeighborhood(source, context, buffer,
                new RawTerrainCache());
        independent.block(31, 4, 0);
        require(source.fills == 4, "Provider caches must not share snapshots across worlds");

        context = GenerationContext.preview(fields.withTrace(new FieldTrace()), 2, 0);
        new RawGenerationNeighborhood(source, context, buffer, cache).block(31, 4, 0);
        require(source.fills == 5, "Traced generation must bypass shared neighbor reuse");
    }

    private static void cacheLifetime() {
        RawTerrainCache cache = new RawTerrainCache();
        CountingSource source = new CountingSource();
        FieldContext fields = new FieldContext(42, 0, true, 0, 0);
        byte[] snapshot = new byte[ChunkBlockBuffer.CELL_COUNT];
        for (int x = 0; x < 32; x++) {
            cache.put(source, fields, x, 0, snapshot);
        }
        require(cache.get(source, fields, 0, 0) == snapshot, "Recently used snapshots remain available");
        cache.put(source, fields, 32, 0, snapshot);
        require(cache.get(source, fields, 0, 0) == snapshot && cache.get(source, fields, 1, 0) == null
                && cache.get(source, fields, 2, 0) == snapshot,
                "Fluid snapshots must use bounded least-recently-used eviction");
        for (FieldContext different : new FieldContext[]{new FieldContext(43, 0, true, 0, 0),
                new FieldContext(42, -1, true, 0, 0), new FieldContext(42, 0, false, 0, 0),
                new FieldContext(42, 0, true, 1, 0), new FieldContext(42, 0, true, 0, 1),
                new FieldContext(42, 0, true, 0, 0, new Environment()),
                new FieldContext(42, 0, true, 0, 0, null, SpatialRegistry.snapshot())}) {
            cache.put(source, fields, 0, 0, snapshot);
            require(cache.get(source, different, 0, 0) == null, "Changed sampling inputs must invalidate snapshots");
        }
        cache.put(source, fields, 0, 0, snapshot);
        require(cache.get(new CountingSource(), fields, 0, 0) == null, "A different terrain source must invalidate");
        cache.put(source, fields, 0, 0, snapshot);
        BiomeGenRegistry.invalidateTerrainSnapshots();
        require(cache.get(source, fields, 0, 0) == null, "Live biome mutations must invalidate base snapshots");
        cache.put(source, fields, 0, 0, snapshot);
        FieldContext traced = fields.withTrace(new FieldTrace());
        cache.put(source, traced, 0, 0, new byte[ChunkBlockBuffer.CELL_COUNT]);
        require(cache.get(source, traced, 0, 0) == null && cache.get(source, fields, 0, 0) == snapshot,
                "Tracing must neither read nor publish shared snapshots");
        require(cache.get(source, new FieldContext(42, 0, true, 0, 0), 0, 0) == snapshot,
                "Equivalent sampling inputs must reuse snapshots even with a fresh FieldContext");
    }

    private static void completeBaseReuse() {
        CountingSource source = new CountingSource();
        RawTerrainCache cache = new RawTerrainCache();
        FieldContext fields = new FieldContext(42, 0);
        GenerationContext context = GenerationContext.preview(fields, -1, 0);
        ChunkBlockBuffer first = new ChunkBlockBuffer();
        BaseTerrainSnapshot base = cache.base(source, context, first);
        byte[] expectedBlocks = first.copyBlocks();
        byte[] expectedMetadata = first.copyMetadata();
        first.set(0, 4, 0, 0, 0);
        ChunkBlockBuffer second = new ChunkBlockBuffer();
        cache.base(source, GenerationContext.preview(fields, -1, 0), second);
        require(source.fills == 1 && Arrays.equals(expectedBlocks, second.copyBlocks())
                && Arrays.equals(expectedMetadata, second.copyMetadata()),
                "Complete base results must restore blocks and metadata without repeating source generation");
        second.set(0, 4, 0, 0, 0);
        require(base.blocks[4] == expectedBlocks[4], "Restored mutable buffers must not alias cached base data");
        cache.base(source, GenerationContext.preview(fields.withTrace(new FieldTrace()), -1, 0),
                new ChunkBlockBuffer());
        require(source.fills == 2, "Tracing must regenerate complete base inputs");
        RawTerrainCache isolated = new RawTerrainCache();
        BiomeGenBase[] custom = new BiomeGenBase[256];
        Arrays.fill(custom, BiomeGenBase.desert);
        isolated.capture(source, new GenerationContext(fields, 3, 0, custom), second);
        require(isolated.get(source, fields, 3, 0) == null,
                "Manually supplied biomes without an environment must not poison detached neighbor previews");
    }

    public static void verifyProfileOutput(WorldGeneratorProfile profile) throws Exception {
        List<String> rows = new ArrayList<String>();
        for (String line : Files.readAllLines(Paths.get("testsrc/betamoon/worldgen/fixtures/terrain_outputs.sha256"),
                StandardCharsets.UTF_8)) {
            if (line.startsWith(profile.key + " ")) {
                rows.add(line);
            }
        }
        require(rows.size() == 20, "Both seeds and every fixed coordinate must have a baseline hash");
        RawTerrainCache cache = new RawTerrainCache();
        for (int order = 0; order < 2; order++) {
            if (order == 1) {
                Collections.reverse(rows);
            }
            long previousSeed = Long.MIN_VALUE;
            FieldContext fields = null;
            for (String row : rows) {
                String[] parts = row.split(" ");
                long seed = Long.parseLong(parts[1]);
                if (fields == null || seed != previousSeed) {
                    TerrainFoundationTest.Fixture world = new TerrainFoundationTest.Fixture(null, seed);
                    ProfileWorldChunkManager manager = new ProfileWorldChunkManager(world.getWorldChunkManager(),
                            profile.biomeSource);
                    fields = new FieldContext(seed, 0, true, 0, 0, manager, profile.spatial);
                    manager.setFieldContext(fields);
                    previousSeed = seed;
                }
                int x = Integer.parseInt(parts[2]);
                int z = Integer.parseInt(parts[3]);
                ChunkBlockBuffer actual = BetaMoonChunkProvider.generateRaw(profile,
                        GenerationContext.preview(fields, x, z), cache);
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                digest.update(actual.copyBlocks());
                digest.update(actual.copyMetadata());
                StringBuilder hash = new StringBuilder();
                for (byte value : digest.digest()) {
                    hash.append(String.format(Locale.ROOT, "%02x", value & 255));
                }
                require(hash.toString().equals(parts[4]), "Generated blocks and metadata changed: " + row);
            }
        }
    }

    private static CompiledCarver carver(String json) throws Exception {
        Map<String, Object> value = ModelJson.read(json.getBytes(StandardCharsets.UTF_8));
        CompiledCarver.Resolver resolver = new CompiledCarver.Resolver() {
            public CompiledCarver carver(String key) {
                throw new AssertionError("Test uses inline children");
            }

            public CompiledCarver inlineCarver(String path, Map<String, Object> child) {
                return new CompiledCarver(path, child, SpatialRegistry.snapshot(), this,
                        Collections.<String, String>emptyMap());
            }
        };
        return new CompiledCarver("test:carver/optimization", value, SpatialRegistry.snapshot(), resolver,
                Collections.<String, String>emptyMap());
    }

    private static ChunkBlockBuffer solid() {
        ChunkBlockBuffer buffer = new ChunkBlockBuffer();
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                buffer.fillColumn(x, z, 0, 127, 1, 0);
            }
        }
        return buffer;
    }

    private static final class CountingSource implements TerrainSource {
        private int fills;

        public boolean isNative() {
            return false;
        }

        public void fill(GenerationContext context, ChunkBlockBuffer buffer) {
            fills++;
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    buffer.fillColumn(x, z, 0, 127, 35, 7);
                    if (fluid(context.worldX(x), context.worldZ(z))) {
                        buffer.set(x, 4, z, 9, 0);
                    }
                }
            }
        }

        boolean fluid(int x, int z) {
            return Math.floorMod(x, 17) == 0 && Math.floorMod(z, 19) == 0;
        }

        public double height(FieldContext fields, int x, int z) {
            return 127;
        }

        public String signature() {
            return "counted-test-source";
        }
    }

    private static final class Environment implements FieldContext.Environment {
        public double biomeValue(String property, int x, int z) {
            return 0.5;
        }

        public boolean biomeMatches(String selector, int x, int z) {
            return false;
        }
    }

    private static void require(boolean value, String message) {
        if (!value) {
            throw new AssertionError(message);
        }
    }
}
