package betamoon.worldgen.terrain;

import betamoon.assets.model.ModelJson;
import betamoon.worldgen.BlockSet;
import betamoon.worldgen.GenerationMetrics;
import betamoon.worldgen.IntRange;
import betamoon.worldgen.WorldGenKey;
import betamoon.worldgen.WorldGenKind;
import betamoon.worldgen.spatial.FieldContext;
import betamoon.worldgen.spatial.FieldTrace;
import betamoon.worldgen.spatial.NativeClimateSamples;
import betamoon.worldgen.spatial.ScalarField;
import betamoon.worldgen.spatial.ScalarFieldCompiler;
import betamoon.worldgen.spatial.SpatialRegistry;
import betamoon.worldgen.surface.SurfaceRuleSet;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.Map;
import java.util.Random;
import net.minecraft.src.BiomeGenBase;
import net.minecraft.src.Block;
import net.minecraft.src.WorldChunkManager;

/**
 * Compatibility and work-count gates for bulk generation, scoped sampling and
 * prepared columns.
 */
public final class TerrainBackendOptimizationTest {
    public static void main(String[] args) throws Exception {
        require(Block.stone != null, "Initialize native content");
        densityCells();
        preparedColumns();
        materialRequirements();
        profileCorners();
        composedHeights();
        exactClimate();
        columnRuns();
        constantSources();
        surfaceMetadata();
        reentrantBetaScratch();
        System.out.println("Backend optimization passed: exact cell interpolation, prepared field domains, "
                + "material requirements, bounded profile work, composed height reuse and exact climate points.");
    }

    private static void densityCells() throws Exception {
        Method sample = CompiledTerrainSource.class.getDeclaredMethod("densityOccupancy", double[].class);
        sample.setAccessible(true);
        Random random = new Random(81307);
        for (int sx : new int[]{1, 2, 4, 8, 16}) {
            for (int sy : new int[]{1, 2, 4, 8, 16}) {
                for (int sz : new int[]{1, 2, 4, 8, 16}) {
                    CompiledTerrainSource source = source("{\"type\":\"density\",\"density\":1,\"minY\":7,\"maxY\":119,"
                            + "\"cell\":{\"x\":" + sx + ",\"y\":" + sy + ",\"z\":" + sz + "}}");
                    double[] data = new double[(16 / sx + 1) * (128 / sy + 1) * (16 / sz + 1)];
                    for (int index = 0; index < data.length; index++) {
                        data[index] = (random.nextDouble() - 0.5) * (index % 3 == 0 ? 1.0e-20 : 100);
                    }
                    BitSet actual = (BitSet) sample.invoke(source, (Object) data);
                    for (int x = 0; x < 16; x++) {
                        for (int z = 0; z < 16; z++) {
                            for (int y = 0; y < 128; y++) {
                                boolean expected = y >= 7 && y <= 119
                                        && originalInterpolation(data, sx, sy, sz, x, y, z) > 0;
                                require(actual.get((x << 11) | (z << 7) | y) == expected,
                                        "Cell traversal changed an original occupancy threshold");
                            }
                        }
                    }
                }
            }
        }
    }

    // Frozen pre-optimization interpolation: deliberately reconstructs each voxel
    // independently.
    private static double originalInterpolation(double[] data, int cellX, int cellY, int cellZ, int x, int y, int z) {
        int sx = x / cellX;
        int sy = y / cellY;
        int sz = z / cellZ;
        int ny = 128 / cellY + 1;
        int nz = 16 / cellZ + 1;
        double a = data[(sx * nz + sz) * ny + sy];
        double b = data[(sx * nz + sz + 1) * ny + sy];
        double c = data[((sx + 1) * nz + sz) * ny + sy];
        double d = data[((sx + 1) * nz + sz + 1) * ny + sy];
        double da = (data[(sx * nz + sz) * ny + sy + 1] - a) / cellY;
        double db = (data[(sx * nz + sz + 1) * ny + sy + 1] - b) / cellY;
        double dc = (data[((sx + 1) * nz + sz) * ny + sy + 1] - c) / cellY;
        double dd = (data[((sx + 1) * nz + sz + 1) * ny + sy + 1] - d) / cellY;
        for (int step = 0; step < y % cellY; step++) {
            a += da;
            b += db;
            c += dc;
            d += dd;
        }
        double ax = (c - a) / cellX;
        double bx = (d - b) / cellX;
        for (int step = 0; step < x % cellX; step++) {
            a += ax;
            b += bx;
        }
        double az = (b - a) / cellZ;
        for (int step = 0; step < z % cellZ; step++) {
            a += az;
        }
        return a;
    }

    private static void preparedColumns() throws Exception {
        String noise = "{\"type\":\"noise\",\"noise\":{\"algorithm\":\"perlin_v1\",\"amplitudes\":[1,0.4]},"
                + "\"scale\":{\"x\":0.025,\"y\":0.1,\"z\":0.025}}";
        for (String graph : new String[]{noise,
                "{\"type\":\"shift\",\"x\":{\"type\":\"x\"},\"y\":0.1,\"z\":-2,\"input\":" + noise + "}",
                "{\"type\":\"scale_coordinates\",\"x\":0.3,\"y\":0.1,\"z\":-1,\"input\":{"
                        + "\"type\":\"shift\",\"y\":0.2,\"input\":" + noise + "}}",
                "{\"type\":\"rotate_2d\",\"degrees\":37,\"input\":" + noise + "}"}) {
            ScalarField field = new ScalarFieldCompiler("test:field/prepared", SpatialRegistry.snapshot())
                    .compile(json(graph));
            for (long seed : new long[]{42, -815}) {
                for (int x : new int[]{-29999999, -17, 0, 17, 29999999}) {
                    FieldContext fields = new FieldContext(seed, 0);
                    double[] values = new double[128];
                    field.sampleColumn(fields, x, -x, 0, 127, values);
                    for (int y = 0; y < 128; y++) {
                        require(Double.doubleToLongBits(values[y]) == Double
                                .doubleToLongBits(field.sample(fields, x, y, -x)),
                                "Prepared transforms must preserve the exact Y arithmetic");
                    }
                    field.sampleColumn(fields.withTrace(new FieldTrace()), x, -x, 0, 127, values);
                    for (int y = 0; y < 128; y++) {
                        require(Double.doubleToLongBits(values[y]) == Double
                                .doubleToLongBits(field.sample(fields, x, y, -x)),
                                "Traced and prepared field values must agree");
                    }
                }
            }
        }
        ScalarField invalid = new ScalarFieldCompiler("test:field/domain", SpatialRegistry.snapshot())
                .compile(json("{\"type\":\"shift\",\"x\":1000000000000,\"input\":{\"type\":\"scale_coordinates\","
                        + "\"x\":0.00001,\"input\":" + noise + "}}"));
        try {
            invalid.sampleColumn(new FieldContext(42, 0), 100, 0, 0, 127, new double[128]);
            throw new AssertionError("Preparation must reject an invalid intermediate coordinate domain");
        } catch (IllegalStateException expected) {
            require(expected.getMessage().contains("domain"), "Preserve coordinate-domain validation");
        }
    }

    private static void materialRequirements() throws Exception {
        FieldContext fields = new FieldContext(42, 0);
        GenerationMetrics.reset();
        ChunkBlockBuffer buffer = new ChunkBlockBuffer();
        source("{\"type\":\"heightmap\",\"height\":40,\"material\":{\"block\":35,\"metadata\":7}}")
                .fill(GenerationContext.preview(fields, 0, 0), buffer);
        require(buffer.blockId(3, 40, 7) == 35 && buffer.metadata(3, 40, 7) == 7,
                "Constant material metadata must remain correct");
        require(metric(GenerationMetrics.Counter.MATERIAL_SNAPSHOTS) == 0
                && metric(GenerationMetrics.Counter.HEIGHTMAP_COLUMNS) == 0,
                "Constant materials must not copy occupancy or calculate unused maps");
        GenerationMetrics.reset();
        source("{\"type\":\"heightmap\",\"height\":40,\"material\":{\"type\":\"when\",\"condition\":{"
                + "\"type\":\"derived_surface_depth\",\"min\":0,\"max\":0},\"thenRule\":35,\"elseRule\":1}}")
                .fill(GenerationContext.preview(fields, 0, 0), buffer);
        require(buffer.blockId(3, 40, 7) == 35 && buffer.blockId(3, 39, 7) == 1,
                "Derived surface must read frozen occupancy before material changes");
        require(metric(GenerationMetrics.Counter.MATERIAL_SNAPSHOTS) == 0,
                "Derived surface alone must not require a block snapshot");
    }

    private static void profileCorners() throws Exception {
        ProfileMapTerrainSource.Resolver profiles = new ProfileMapTerrainSource.Resolver() {
            public HeightEnvelope profile(String key) {
                throw new AssertionError("Test uses inline profiles");
            }

            public HeightEnvelope inlineProfile(String key, Map<String, Object> value) {
                return new HeightEnvelope(key, value, SpatialRegistry.snapshot());
            }
        };
        ProfileMapTerrainSource source = new ProfileMapTerrainSource("test:terrain_source/corners",
                json("{\"type\":\"profile_map\",\"blendWidth\":48,\"fallback\":{\"baseHeight\":40},"
                        + "\"entries\":[{\"profile\":{\"baseHeight\":80}}]}"),
                SpatialRegistry.snapshot(), TerrainRegistry.snapshot(), profiles,
                Collections.<String, String>emptyMap());
        GenerationMetrics.reset();
        FieldContext fields = new FieldContext(42, 0);
        for (int x = 1; x <= 16; x++) {
            for (int z = 1; z <= 16; z++) {
                require(Math.abs(source.height(fields, x, z) - 80) < 1.0e-12,
                        "Blended profiles must retain their envelope");
                source.usesHeightProfile(fields, x, z, "test:absent");
            }
        }
        require(metric(GenerationMetrics.Counter.PROFILE_CORNER_SELECTIONS) == 4,
                "A blend cell must reuse its four selections across height and membership queries");
    }

    private static void composedHeights() throws Exception {
        final int[] fills = {0};
        TerrainSource child = new TerrainSource() {
            public boolean isNative() {
                return false;
            }

            public void fill(GenerationContext context, ChunkBlockBuffer buffer) {
                fills[0]++;
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        buffer.fillColumn(x, z, 32, 47, 35, 7);
                    }
                }
            }

            public double height(FieldContext fields, int x, int z) {
                return 47;
            }

            public String signature() {
                return "counting-child";
            }
        };
        ComposedTerrainSource composed = new ComposedTerrainSource("test:terrain_source/composed",
                json("{\"type\":\"compose\",\"children\":[{\"source\":\"test:child\"}]}"), SpatialRegistry.snapshot(),
                key -> child, Collections.<String, String>emptyMap());
        FieldContext fields = new FieldContext(42, 0);
        for (int x = -16; x < 0; x++) {
            for (int z = 0; z < 16; z++) {
                require(composed.height(fields, x, z) == 47, "Composed heights must describe actual occupancy");
            }
        }
        require(fills[0] == 1, "A chunk of point heights must not generate the composed source 256 times");
    }

    private static void exactClimate() {
        for (long seed : new long[]{42, -5852049645459666272L}) {
            WorldChunkManager manager = new TerrainFoundationTest.Fixture(null, seed).getWorldChunkManager();
            NativeClimateSamples samples = new NativeClimateSamples(manager);
            GenerationMetrics.reset();
            for (int x = -16; x < 16; x++) {
                int z = x * 3;
                manager.loadBlockGeneratorData(null, x, z, 1, 1);
                double temperature = manager.temperature[0];
                double humidity = manager.humidity[0];
                require(Double.doubleToLongBits(samples.value("temperature", x, z)) == Double
                        .doubleToLongBits(temperature), "Cached point temperature changed");
                require(Double.doubleToLongBits(samples.value("humidity", x, z)) == Double.doubleToLongBits(humidity),
                        "Cached point humidity changed");
            }
            require(metric(GenerationMetrics.Counter.NATIVE_CLIMATE_POINT_REQUESTS) == 32,
                    "A temperature/humidity pair must share its native point request");
        }
        WorldChunkManager changing = new WorldChunkManager() {
            private int calls;

            @Override
            public BiomeGenBase[] loadBlockGeneratorData(BiomeGenBase[] output, int x, int z, int width, int depth) {
                temperature = new double[]{++calls};
                humidity = new double[]{-calls};
                return new BiomeGenBase[]{BiomeGenBase.plains};
            }
        };
        NativeClimateSamples samples = new NativeClimateSamples(changing);
        require(samples.value("temperature", 0, 0) == 1 && samples.value("temperature", 0, 0) == 2
                && samples.value("humidity", 0, 0) == -3,
                "Replacement climate managers must retain their live point behavior without native caching");
    }

    private static long metric(GenerationMetrics.Counter counter) {
        Long value = GenerationMetrics.snapshot().get(counter.name());
        require(value != null, "This work-count suite requires -Dbetamoon.worldgen.metrics=true");
        return value.longValue();
    }

    private static void columnRuns() {
        ChunkBlockBuffer bulk = new ChunkBlockBuffer();
        ChunkBlockBuffer point = new ChunkBlockBuffer();
        TerrainHeightmaps heights = new TerrainHeightmaps(bulk);
        Random random = new Random(2951);
        for (int iteration = 0; iteration < 512; iteration++) {
            int x = random.nextInt(16);
            int z = random.nextInt(16);
            int first = random.nextInt(128);
            int last = first + random.nextInt(128 - first);
            int id = iteration % 7 == 0 ? 0 : 35;
            int metadata = id == 0 ? 0 : random.nextInt(16);
            bulk.fillColumn(x, z, first, last, id, metadata);
            for (int y = first; y <= last; y++) {
                point.set(x, y, z, id, metadata);
            }
            require(heights.worldSurface(x, z) == new TerrainHeightmaps(point).worldSurface(x, z),
                    "Bulk runs must invalidate existing height views");
        }
        require(Arrays.equals(bulk.copyBlocks(), point.copyBlocks())
                && Arrays.equals(bulk.copyMetadata(), point.copyMetadata()),
                "Bulk runs must preserve adjacent nibbles, including single cells and odd endpoints");
    }

    private static void constantSources() throws Exception {
        for (int floor : new int[]{-1, 0, 17, 90, 127}) {
            ChunkBlockBuffer buffer = new ChunkBlockBuffer();
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    buffer.fillColumn(x, z, 0, 127, 35, 15);
                }
            }
            source("{\"type\":\"heightmap\",\"minY\":7,\"maxY\":119,\"height\":40.7,"
                    + "\"material\":{\"block\":35,\"metadata\":7},\"seaLevel\":63,"
                    + "\"air\":{\"block\":20,\"metadata\":3},\"floorHeight\":" + floor + "}")
                    .fill(GenerationContext.preview(new FieldContext(42, 0), -3, 7), buffer);
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    for (int y = 0; y < 128; y++) {
                        boolean outside = y < 7 || y > 119;
                        int id = outside ? 35 : y <= floor ? 7 : y <= 40 ? 35 : y <= 63 ? 9 : 20;
                        int metadata = outside ? 15 : y <= floor ? 0 : y <= 40 ? 7 : y <= 63 ? 0 : 3;
                        require(buffer.blockId(x, y, z) == id && buffer.metadata(x, y, z) == metadata,
                                "Constant column ranges must preserve floors, fluids, metadata and untouched Y");
                    }
                }
            }
        }
        ChunkBlockBuffer buffer = new ChunkBlockBuffer();
        source("{\"type\":\"void\",\"seaLevel\":60,\"spawnPlatform\":{\"block\":35,\"size\":3,\"y\":63}}")
                .fill(GenerationContext.preview(new FieldContext(42, 0, true, -1, 0), -1, 0), buffer);
        require(buffer.blockId(15, 63, 0) == 35 && buffer.blockId(12, 63, 0) == 0 && buffer.blockId(15, 60, 0) == 9,
                "Void runs must retain spawn platform and fluid precedence");
    }

    private static void surfaceMetadata() {
        ChunkBlockBuffer buffer = new ChunkBlockBuffer();
        buffer.fillColumn(3, 7, 0, 40, 35, 7);
        buffer.set(3, 41, 7, 20, 3);
        TerrainHeightmaps heights = new TerrainHeightmaps(buffer);
        SurfaceRuleSet rule = new SurfaceRuleSet(WorldGenKey.parse("test:surface/meta", WorldGenKind.SURFACE), "test",
                "test.lua", "test", new BlockSet(35), Arrays.asList(new SurfaceRuleSet.Layer(35, new IntRange(1, 1)),
                        new SurfaceRuleSet.Layer(1, new IntRange(2, 2))),
                null, 63);
        buffer.applySurface(rule, GenerationContext.preview(new FieldContext(42, 0), 0, 0), 3, 7);
        require(buffer.metadata(3, 40, 7) == 7 && buffer.blockId(3, 39, 7) == 1 && buffer.metadata(3, 39, 7) == 0
                && buffer.metadata(3, 38, 7) == 0 && buffer.metadata(3, 37, 7) == 7 && buffer.metadata(3, 41, 7) == 3
                && heights.worldSurface(3, 7) == 41,
                "Tracked surfaces must reset only changed IDs and preserve neighboring metadata");
    }

    private static void reentrantBetaScratch() {
        BetaDensitySampler sampler = new BetaDensitySampler(42);
        FieldContext.Environment constant = new FieldContext.Environment() {
            public double biomeValue(String property, int x, int z) {
                return 0.5;
            }

            public boolean biomeMatches(String selector, int x, int z) {
                return false;
            }
        };
        FieldContext ordinary = new FieldContext(42, 0, true, 0, 0, constant);
        double[] expected = sampler.sample(GenerationContext.preview(ordinary, -3, 4), 68, 1, 1);
        FieldContext.Environment nested = new FieldContext.Environment() {
            private boolean entered;

            public double biomeValue(String property, int x, int z) {
                if (!entered) {
                    entered = true;
                    sampler.sample(GenerationContext.preview(ordinary, 9, -7), 68, 1, 1);
                }
                return 0.5;
            }

            public boolean biomeMatches(String selector, int x, int z) {
                return false;
            }
        };
        double[] actual = sampler.sample(GenerationContext.preview(new FieldContext(42, 0, true, 0, 0, nested), -3, 4),
                68, 1, 1);
        sampler.sample(GenerationContext.preview(ordinary, 5, 6), 68, 1, 1);
        require(Arrays.equals(expected, actual), "Nested Beta sampling must lease independent scratch arrays");
        double[] repeated = sampler.sample(GenerationContext.preview(ordinary, -3, 4), 68, 1, 1);
        require(Arrays.equals(expected, repeated) && repeated != expected,
                "Returned density arrays must remain independently owned after scratch reuse");
    }

    private static Map<String, Object> json(String value) throws Exception {
        return ModelJson.read(value.getBytes(StandardCharsets.UTF_8));
    }

    private static CompiledTerrainSource source(String value) throws Exception {
        return new CompiledTerrainSource("test:terrain_source/optimization", json(value), SpatialRegistry.snapshot(),
                TerrainRegistry.snapshot(), Collections.<String, String>emptyMap());
    }

    private static void require(boolean value, String message) {
        if (!value) {
            throw new AssertionError(message);
        }
    }
}
