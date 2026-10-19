package betamoon.luamodloader;

import betamoon.worldgen.GenerationMetrics;
import betamoon.worldgen.creation.GenerationCatalog;
import betamoon.worldgen.creation.GenerationDraft;
import betamoon.worldgen.creation.GenerationSelection;
import betamoon.worldgen.creation.GenerationSelectionStore;
import betamoon.worldgen.terrain.GeneratorManifestStore;
import betamoon.worldgen.terrain.TerrainFoundationTest;
import betamoon.worldgen.terrain.TerrainRegistry;
import betamoon.worldgen.terrain.WorldGeneratorManifest;
import betamoon.worldgen.terrain.WorldGeneratorProfile;
import com.sun.management.ThreadMXBean;
import java.io.DataOutputStream;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import net.minecraft.src.Chunk;
import net.minecraft.src.ChunkProvider;
import net.minecraft.src.EnumSkyBlock;
import net.minecraft.src.IChunkProvider;
import net.minecraft.src.IProgressUpdate;
import net.minecraft.src.ModLoader;
import net.minecraft.src.World;
import net.minecraft.src.WorldProvider;

/**
 * Opt-in benchmark; correctness gates and timing runs are intentionally
 * separate.
 */
public final class WorldgenPerformanceBenchmark {
    public static void main(String[] args) throws Exception {
        WorldgenExamplesTest.loadExamples(false);
        Field initialized = ModLoader.class.getDeclaredField("hasInit");
        initialized.setAccessible(true);
        initialized.setBoolean(null, true);
        int repetitions = Integer.getInteger("worldgen.benchmark.repetitions", 5);
        int width = Integer.getInteger("worldgen.benchmark.width", 8);
        long seed = Long.getLong("worldgen.benchmark.seed", -5852049645459666272L);
        String[] orders = System.getProperty("worldgen.benchmark.orders", "row").split(",");
        if (width < 2 || width > 32 || repetitions < 1 || repetitions > 20) {
            throw new IllegalArgumentException("Benchmark width must be 2..32 and repetitions 1..20");
        }
        for (int iteration = -1; iteration < repetitions; iteration++) {
            for (String order : orders) {
                for (String profile : new String[]{"minecraft", "example:generator/rolling_meadows",
                        "example:generator/hollow_marches"}) {
                    for (int origin : new int[]{0, 64}) {
                        run(profile, seed, origin, width, order, iteration);
                    }
                }
            }
        }
    }

    private static void run(String key, long seed, int origin, int width, String order, int iteration)
            throws Exception {
        TerrainFoundationTest.Save save = new TerrainFoundationTest.Save(
                Files.createTempDirectory("worldgen-benchmark-"));
        WorldGeneratorProfile original = key.equals("minecraft") ? null : TerrainRegistry.snapshot().generator(key);
        GenerationSelection selection = GenerationSelection.create(new GenerationDraft(new GenerationCatalog(original)),
                seed);
        new GenerationSelectionStore(save.func_28113_a("betamoon_generation_settings")).write(selection);
        if (original != null) {
            WorldGeneratorProfile profile = selection.profile();
            new GeneratorManifestStore(save.func_28113_a("betamoon_generator_overworld"))
                    .write(new WorldGeneratorManifest(seed, 0, profile.key, profile.signature,
                            new WorldGeneratorManifest.OriginalSpawn(0, 64, 0)));
        }
        BenchmarkWorld world = new BenchmarkWorld(save, seed);
        world.rand.setSeed(seed);
        world.counters.clear();
        GenerationMetrics.reset();
        List<int[]> coordinates = new ArrayList<int[]>();
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < width; z++) {
                coordinates.add(new int[]{origin + x, z});
            }
        }
        List<int[]> canonicalOrder = new ArrayList<int[]>(coordinates);
        if (order.equals("reverse")) {
            Collections.reverse(coordinates);
        } else if (order.equals("random")) {
            Collections.shuffle(coordinates, new Random(161));
        } else if (order.equals("column")) {
            Collections.sort(coordinates,
                    (a, b) -> a[1] == b[1] ? Integer.compare(a[0], b[0]) : Integer.compare(a[1], b[1]));
        } else if (!order.equals("row")) {
            throw new IllegalArgumentException("Unknown benchmark order: " + order);
        }
        long[] totals = new long[coordinates.size()];
        long loadNs = 0;
        long lightNs = 0;
        ThreadMXBean memory = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        memory.setThreadAllocatedMemoryEnabled(true);
        long thread = Thread.currentThread().getId();
        long allocated = memory.getThreadAllocatedBytes(thread);
        for (int index = 0; index < coordinates.size(); index++) {
            int[] coordinate = coordinates.get(index);
            long start = System.nanoTime();
            world.getChunkFromChunkCoords(coordinate[0], coordinate[1]);
            long loaded = System.nanoTime();
            int batches = 0;
            while (world.updatingLighting()) {
                if (++batches > 10000) {
                    throw new AssertionError("Lighting work did not terminate");
                }
            }
            long finished = System.nanoTime();
            loadNs += loaded - start;
            lightNs += finished - loaded;
            totals[index] = finished - start;
        }
        allocated = memory.getThreadAllocatedBytes(thread) - allocated;
        boolean verifyLighting = Boolean.getBoolean("worldgen.benchmark.verifyLighting") && original != null;
        byte[] publishedLight = verifyLighting ? lightingDigest(world, canonicalOrder) : null;
        if (verifyLighting || Boolean.getBoolean("worldgen.benchmark.canonicalLight")) {
            for (int[] coordinate : canonicalOrder) {
                int x = coordinate[0] * 16;
                int z = coordinate[1] * 16;
                world.scheduleLightingUpdate(EnumSkyBlock.Block, x, 0, z, x + 15, 127, z + 15);
            }
            int batches = 0;
            while (world.updatingLighting()) {
                if (++batches > 10000) {
                    throw new AssertionError("Canonical lighting work did not terminate");
                }
            }
            if (verifyLighting && !Arrays.equals(publishedLight, lightingDigest(world, canonicalOrder))) {
                throw new AssertionError("Published lighting differed from complete native reconciliation: " + key
                        + " / " + origin + " / " + order);
            }
        }
        MessageDigest terrain = MessageDigest.getInstance("SHA-256");
        MessageDigest light = MessageDigest.getInstance("SHA-256");
        for (int[] coordinate : canonicalOrder) {
            Chunk chunk = world.getChunkFromChunkCoords(coordinate[0], coordinate[1]);
            terrain.update(chunk.blocks);
            terrain.update(chunk.data.data);
            light.update(chunk.blocklightMap.data);
            light.update(chunk.skylightMap.data);
        }
        String export = System.getProperty("worldgen.benchmark.export");
        if (export != null && iteration == 0) {
            Path directory = Paths.get(export);
            Files.createDirectories(directory);
            String name = key.replace(':', '_').replace('/', '_') + "_" + order + "_" + origin + ".bin";
            try (DataOutputStream stream = new DataOutputStream(Files.newOutputStream(directory.resolve(name)))) {
                for (int[] coordinate : canonicalOrder) {
                    Chunk chunk = world.getChunkFromChunkCoords(coordinate[0], coordinate[1]);
                    stream.writeInt(coordinate[0]);
                    stream.writeInt(coordinate[1]);
                    stream.write(chunk.blocks);
                    stream.write(chunk.data.data);
                    stream.write(chunk.blocklightMap.data);
                    stream.write(chunk.skylightMap.data);
                }
            }
        }
        Arrays.sort(totals);
        if (iteration >= 0) {
            System.out.printf(Locale.ROOT,
                    "RESULT,%s,seed=%d,iteration=%d,order=%s,origin=%d,chunks=%d,rawCalls=%d,popCalls=%d,"
                            + "rawMs=%.3f,popMs=%.3f,requestMs=%.3f,lightMs=%.3f,totalMs=%.3f,p50Ms=%.3f,"
                            + "p95Ms=%.3f,p99Ms=%.3f,maxMs=%.3f,allocatedBytes=%d,terrain=%s,light=%s%n",
                    key, seed, iteration, order, origin, coordinates.size(), world.counters.rawCalls,
                    world.counters.popCalls, world.counters.rawNs / 1e6, world.counters.popNs / 1e6, loadNs / 1e6,
                    lightNs / 1e6, (loadNs + lightNs) / 1e6, percentile(totals, 0.5) / 1e6,
                    percentile(totals, 0.95) / 1e6, percentile(totals, 0.99) / 1e6, totals[totals.length - 1] / 1e6,
                    allocated, hex(terrain.digest()), hex(light.digest()));
            if (!GenerationMetrics.snapshot().isEmpty()) {
                System.out.println("METRICS," + key + ",origin=" + origin + "," + GenerationMetrics.snapshot());
            }
        }
    }

    private static long percentile(long[] sorted, double quantile) {
        return sorted[Math.max(0, (int) Math.ceil(sorted.length * quantile) - 1)];
    }

    private static byte[] lightingDigest(World world, List<int[]> coordinates) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        for (int[] coordinate : coordinates) {
            Chunk chunk = world.getChunkFromChunkCoords(coordinate[0], coordinate[1]);
            digest.update(chunk.blocklightMap.data);
            digest.update(chunk.skylightMap.data);
        }
        return digest.digest();
    }

    private static String hex(byte[] bytes) {
        StringBuilder hex = new StringBuilder();
        for (byte value : bytes) {
            hex.append(String.format(Locale.ROOT, "%02x", value & 255));
        }
        return hex.toString();
    }

    private static final class Counters {
        private long rawNs;
        private long popNs;
        private int rawCalls;
        private int popCalls;

        private void clear() {
            rawNs = 0;
            popNs = 0;
            rawCalls = 0;
            popCalls = 0;
        }
    }

    private static final class BenchmarkWorld extends World {
        private Counters counters;

        private BenchmarkWorld(TerrainFoundationTest.Save save, long seed) {
            super(save, "worldgen-benchmark", new WorldProvider() {
            }, seed);
        }

        protected IChunkProvider getChunkProvider() {
            IChunkProvider outer = super.getChunkProvider();
            counters = new Counters();
            try {
                Field delegate = ChunkProvider.class.getDeclaredField("chunkProvider");
                delegate.setAccessible(true);
                delegate.set(outer, new TimedProvider((IChunkProvider) delegate.get(outer), counters));
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Benchmark requires the native caching provider", exception);
            }
            return outer;
        }
    }

    private static final class TimedProvider implements IChunkProvider {
        private final IChunkProvider delegate;
        private final Counters counters;

        private TimedProvider(IChunkProvider delegate, Counters counters) {
            this.delegate = delegate;
            this.counters = counters;
        }

        public boolean chunkExists(int x, int z) {
            return delegate.chunkExists(x, z);
        }

        public Chunk provideChunk(int x, int z) {
            long start = System.nanoTime();
            try {
                return delegate.provideChunk(x, z);
            } finally {
                counters.rawCalls++;
                counters.rawNs += System.nanoTime() - start;
            }
        }

        public Chunk prepareChunk(int x, int z) {
            return delegate.prepareChunk(x, z);
        }

        public void populate(IChunkProvider provider, int x, int z) {
            long start = System.nanoTime();
            try {
                delegate.populate(provider, x, z);
            } finally {
                counters.popCalls++;
                counters.popNs += System.nanoTime() - start;
            }
        }

        public boolean saveChunks(boolean all, IProgressUpdate progress) {
            return delegate.saveChunks(all, progress);
        }

        public boolean unload100OldestChunks() {
            return delegate.unload100OldestChunks();
        }

        public boolean canSave() {
            return delegate.canSave();
        }

        public String makeString() {
            return delegate.makeString();
        }
    }
}
