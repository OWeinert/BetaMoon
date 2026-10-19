package betamoon.worldgen.terrain;

import betamoon.assets.model.ModelJson;
import betamoon.worldgen.spatial.FieldContext;
import betamoon.worldgen.spatial.SpatialRegistry;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.Locale;
import net.minecraft.src.BiomeGenBase;
import net.minecraft.src.Block;
import net.minecraft.src.ChunkProviderGenerate;

/**
 * Same-process raw-generation timing/allocation observations; output never
 * depends on elapsed time.
 */
public final class WorldgenPerformanceTest {
    private static volatile long checksum;

    public static void main(String[] args) throws Exception {
        if (Block.stone == null) {
            throw new AssertionError("Native blocks unavailable");
        }
        TerrainFoundationTest.Fixture world = new TerrainFoundationTest.Fixture(
                new TerrainFoundationTest.Save(Files.createTempDirectory("betamoon-worldgen-cost-")), 1987);
        ProfileWorldChunkManager climate = new ProfileWorldChunkManager(world.getWorldChunkManager(), null);
        FieldContext fields = new FieldContext(1987, 0, true, 0, 0, climate);
        ChunkProviderGenerate nativeProvider = new ChunkProviderGenerate(world, 1987);
        measure("native_beta_raw", () -> {
            byte[] blocks = new byte[32768];
            BiomeGenBase[] biomes = world.getWorldChunkManager().loadBlockGeneratorData(null, -32, 48, 16, 16);
            nativeProvider.generateTerrain(-2, 3, blocks, biomes, world.getWorldChunkManager().temperature);
            checksum += blocks[1000];
        });
        String[] declarations = {
                "{\"type\":\"flat\",\"layers\":[{\"block\":7,\"depth\":1},{\"block\":3,\"depth\":3},{\"block\":2,\"depth\":1}]}",
                "{\"type\":\"heightmap\",\"height\":64}",
                "{\"type\":\"density\",\"density\":{\"type\":\"y_gradient\",\"fromY\":0,\"toY\":128,\"fromValue\":64,\"toValue\":-64}}",
                "{\"type\":\"beta_compatible\"}"};
        for (String declaration : declarations) {
            CompiledTerrainSource source = new CompiledTerrainSource("test:terrain_source/cost",
                    ModelJson.read(declaration.getBytes(StandardCharsets.UTF_8)), SpatialRegistry.snapshot(),
                    TerrainRegistry.snapshot(), Collections.<String, String>emptyMap());
            measure(declaration.substring(9, declaration.indexOf('"', 9)), () -> {
                ChunkBlockBuffer buffer = new ChunkBlockBuffer();
                source.fill(GenerationContext.preview(new FieldContext(1987, 0, true, 0, 0, climate), -2, 3), buffer);
                checksum += buffer.blockId(7, 32, 9);
            });
        }
        HeightEnvelope unused = new HeightEnvelope("test:terrain/unused",
                Collections.<String, Object>singletonMap("baseHeight", 50.0), SpatialRegistry.snapshot());
        ProfileMapTerrainSource slopedFallback = new ProfileMapTerrainSource("test:terrain_source/slope",
                ModelJson.read(("{\"type\":\"profile_map\",\"entries\":[{\"profile\":\"test:terrain/unused\","
                        + "\"biomes\":{\"include\":[\"test:biome/absent\"]}}],\"material\":{\"type\":\"when\","
                        + "\"condition\":{\"type\":\"slope\"},\"thenRule\":1,\"elseRule\":3}}")
                        .getBytes(StandardCharsets.UTF_8)),
                SpatialRegistry.snapshot(), TerrainRegistry.snapshot(), key -> unused,
                Collections.<String, String>emptyMap());
        measure("profile_map_native_fallback_with_slope", () -> {
            ChunkBlockBuffer buffer = new ChunkBlockBuffer();
            slopedFallback.fill(GenerationContext.preview(new FieldContext(1987, 0, true, 0, 0, climate), -2, 3),
                    buffer);
            checksum += buffer.blockId(7, 32, 9);
        });
    }

    private static void measure(String name, Runnable action) {
        for (int i = 0; i < 16; i++) {
            action.run();
        }
        com.sun.management.ThreadMXBean allocation = (com.sun.management.ThreadMXBean) ManagementFactory
                .getThreadMXBean();
        boolean supported = allocation.isThreadAllocatedMemorySupported();
        if (supported && !allocation.isThreadAllocatedMemoryEnabled()) {
            allocation.setThreadAllocatedMemoryEnabled(true);
        }
        long thread = Thread.currentThread().getId();
        long bytes = supported ? allocation.getThreadAllocatedBytes(thread) : 0;
        long started = System.nanoTime();
        for (int i = 0; i < 64; i++) {
            action.run();
        }
        double milliseconds = (System.nanoTime() - started) / 64000000.0;
        double allocated = supported ? (allocation.getThreadAllocatedBytes(thread) - bytes) / 65536.0 : -1;
        System.out.printf(Locale.ROOT, "%s: %.3f ms/chunk, %.1f KiB allocated/chunk%n", name, milliseconds, allocated);
    }
}
