package betamoon.dimension;

import betamoon.dimension.storage.DimensionMetadataStore;
import betamoon.teleport.TeleportLocation;
import betamoon.teleport.TeleportPlatformDefinition;
import betamoon.worldgen.spatial.FieldContext;
import betamoon.worldgen.spatial.GenerationValues;
import betamoon.worldgen.terrain.BetaMoonChunkProvider;
import betamoon.worldgen.terrain.ChunkBlockBuffer;
import betamoon.worldgen.terrain.GenerationContext;
import betamoon.worldgen.terrain.GeneratorSpawnSearch;
import betamoon.worldgen.terrain.NativeGeneratorFamily;
import betamoon.worldgen.terrain.ProfileWorldChunkManager;
import betamoon.worldgen.terrain.WorldGenerationRuntime;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import net.minecraft.src.Chunk;
import net.minecraft.src.IChunkProvider;
import net.minecraft.src.World;

/**
 * Bounded detached spawn preview. Persist its fixed X/Z anchor before
 * publishing actual world chunks.
 */
public final class DimensionBootstrap {
    private final World world;
    private final DimensionGenerationBinding binding;
    private final DimensionMetadataStore metadata;
    private final List<double[]> candidates = new ArrayList<double[]>();
    private final ProfileWorldChunkManager previewBiomes;
    private final IChunkProvider nativePreview;
    private int cursor;
    private TeleportLocation proposal;
    private String failure;

    public DimensionBootstrap(World world) {
        this.world = world;
        DimensionRuntime.WorldBinding runtime = DimensionRuntime.binding(world);
        if (runtime == null || runtime.save == null) {
            throw new IllegalArgumentException("Bootstrap requires a custom dimension binding");
        }
        metadata = runtime.save.metadata;
        binding = DimensionRuntime.generation(world);
        FieldContext fields = DimensionQueries.detachedContext(binding, metadata.seed, metadata.numericId, false, 0, 0);
        previewBiomes = (ProfileWorldChunkManager) fields.environment;
        nativePreview = binding.profile == null || binding.profile.terrain == null || binding.profile.terrain.isNative()
                ? NativeGeneratorFamily.provider(world, binding.nativeBase)
                : null;
        Map<String, Object> spawn = binding.definition.spawn;
        double x = GenerationValues.number(spawn, "x", 0, "dimension.spawn");
        double z = GenerationValues.number(spawn, "z", 0, "dimension.spawn");
        double centerX = Math.floor(x) + 0.5;
        double centerZ = Math.floor(z) + 0.5;
        int radius = spawn.containsKey("radius")
                ? GenerationValues.integer(spawn.get("radius"), "spawn.radius", 0, 256)
                : 64;
        int extent = radius / 16;
        for (int dx = -extent; dx <= extent; dx++) {
            for (int dz = -extent; dz <= extent; dz++) {
                if ((long) dx * dx + (long) dz * dz <= (long) extent * extent) {
                    candidates.add(new double[]{centerX + dx * 16, centerZ + dz * 16});
                }
            }
        }
        candidates.sort(
                Comparator.comparingDouble((double[] point) -> square(point[0] - centerX) + square(point[1] - centerZ))
                        .thenComparingDouble(point -> point[0]).thenComparingDouble(point -> point[1]));
    }

    public boolean step() throws IOException {
        if (proposal != null || failure != null) {
            return true;
        }
        Map<String, Object> spawn = binding.definition.spawn;
        if ("fixed".equals(spawn.get("type"))) {
            select(GenerationValues.number(spawn.get("x"), "spawn.x"),
                    GenerationValues.number(spawn.get("y"), "spawn.y"),
                    GenerationValues.number(spawn.get("z"), "spawn.z"));
            return true;
        }
        if (metadata.hasAnchor()) {
            select(metadata.anchorX() + 0.5, metadata.anchorY(), metadata.anchorZ() + 0.5);
            return true;
        }
        for (int unit = 0; unit < 2 && cursor < candidates.size() && cursor < 256; unit++) {
            double[] position = candidates.get(cursor++);
            int x = (int) Math.floor(position[0]);
            int z = (int) Math.floor(position[1]);
            if (Math.abs((long) x) >= 30000000 || Math.abs((long) z) >= 30000000) {
                continue;
            }
            Chunk nativeChunk = null;
            ChunkBlockBuffer owned = null;
            if (nativePreview != null) {
                nativeChunk = nativePreview.provideChunk(x >> 4, z >> 4);
            } else {
                FieldContext fields = new FieldContext(metadata.seed, metadata.numericId, metadata.key.toString(), true,
                        x, z, previewBiomes, binding.spatial);
                previewBiomes.setFieldContext(fields);
                owned = BetaMoonChunkProvider.generateRaw(binding.profile,
                        GenerationContext.preview(fields, x >> 4, z >> 4));
            }
            for (int y = 125; y >= 0; y--) {
                int support = id(nativeChunk, owned, x, y, z);
                if (GeneratorSpawnSearch.safeGround(support) && id(nativeChunk, owned, x, y + 1, z) == 0
                        && id(nativeChunk, owned, x, y + 2, z) == 0) {
                    select(position[0], y + 1, position[1]);
                    return true;
                }
            }
        }
        if (cursor >= candidates.size() || cursor >= 256) {
            TeleportPlatformDefinition platform = fallback();
            if (platform != null) {
                double[] center = candidates.get(0);
                select(center[0], platform.y + 1, center[1]);
            } else {
                failure = cursor >= 256 ? "budget_exhausted" : "unsafe_destination";
            }
            return true;
        }
        return false;
    }

    public TeleportPlatformDefinition fallback() {
        Object value = binding.definition.spawn.get("fallback");
        if (!(value instanceof Map)) {
            return null;
        }
        Map<String, Object> fallback = GenerationValues.object(value, "dimension.spawn.fallback");
        return "platform".equals(fallback.get("type"))
                ? new TeleportPlatformDefinition(fallback, "dimension.spawn.fallback")
                : null;
    }

    private void select(double x, double y, double z) throws IOException {
        Map<String, Object> spawn = binding.definition.spawn;
        proposal = new TeleportLocation(metadata.key, x, y, z,
                TeleportLocation.normalizeYaw(GenerationValues.number(spawn, "yaw", 0, "spawn")),
                (float) GenerationValues.number(spawn, "pitch", 0, "spawn"));
        metadata.bootstrapAnchor((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
        if (world.getWorldChunkManager() instanceof ProfileWorldChunkManager) {
            ((ProfileWorldChunkManager) world.getWorldChunkManager())
                    .setFieldContext(WorldGenerationRuntime.fieldContext(world));
        }
    }

    public TeleportLocation proposal() {
        return proposal;
    }

    public String failure() {
        return failure;
    }

    private static int id(Chunk chunk, ChunkBlockBuffer owned, int x, int y, int z) {
        return chunk == null ? owned.blockId(x & 15, y, z & 15) : chunk.getBlockID(x & 15, y, z & 15);
    }

    private static double square(double value) {
        return value * value;
    }
}
