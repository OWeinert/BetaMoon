package betamoon.worldgen.terrain;

import betamoon.worldgen.GenerationMetrics;
import betamoon.worldgen.GenerationStage;
import betamoon.worldgen.WorldGenRegistry;
import java.util.Collections;
import java.util.List;
import net.minecraft.src.BlockSand;
import net.minecraft.src.Chunk;
import net.minecraft.src.ChunkProviderGenerate;
import net.minecraft.src.IChunkProvider;
import net.minecraft.src.IProgressUpdate;
import net.minecraft.src.World;

/**
 * Native-delegation baseline; retains the delegate's ModLoader population
 * identity.
 */
public final class BetaMoonChunkProvider implements IChunkProvider {
    private final IChunkProvider nativeProvider;
    private final WorldGeneratorProfile profile;
    private final World world;
    private final RawTerrainCache rawTerrain = new RawTerrainCache();

    public BetaMoonChunkProvider(IChunkProvider nativeProvider, WorldGeneratorProfile profile) {
        this(null, nativeProvider, profile);
    }

    public BetaMoonChunkProvider(World world, IChunkProvider nativeProvider, WorldGeneratorProfile profile) {
        if (nativeProvider == null || nativeProvider instanceof BetaMoonChunkProvider || profile == null) {
            throw new IllegalArgumentException("Generator requires one native delegate and a resolved profile");
        }
        if (profile.population != null && !profile.population.nativeCategories.isEmpty()
                && profile.population.nativeCategories.size() != 9
                && nativeProvider.getClass() != ChunkProviderGenerate.class) {
            throw new IllegalArgumentException("Granular native population requires Beta's native chunk provider; "
                    + "use native=all or native=none with a replacement provider");
        }
        this.nativeProvider = nativeProvider;
        this.profile = profile;
        this.world = world;
    }

    public WorldGeneratorProfile profile() {
        return profile;
    }

    public IChunkProvider nativeDelegate() {
        return nativeProvider;
    }

    @Override
    public boolean chunkExists(int x, int z) {
        return nativeProvider.chunkExists(x, z);
    }

    @Override
    public Chunk provideChunk(int x, int z) {
        if (profile.terrain == null || profile.terrain.isNative()) {
            try {
                Chunk nativeChunk = nativeProvider.provideChunk(x, z);
                if (world != null) {
                    NativeBiomeOverrideRuntime.finish(world, x, z, nativeChunk);
                }
                return nativeChunk;
            } finally {
                NativeBiomeOverrideRuntime.clear();
            }
        }
        GenerationContext context = new GenerationContext(WorldGenerationRuntime.fieldContext(world), x, z,
                world.getWorldChunkManager().loadBlockGeneratorData(null, x * 16, z * 16, 16, 16));
        return RawChunkFinalizer.build(world, x, z, generateRaw(profile, context, rawTerrain,
                WorldGenerationRuntime.overrides(world)));
    }

    public static ChunkBlockBuffer generateRaw(WorldGeneratorProfile profile, GenerationContext context) {
        return generateRaw(profile, context, null);
    }

    static ChunkBlockBuffer generateRaw(WorldGeneratorProfile profile, GenerationContext context,
            RawTerrainCache cache) {
        return generateRaw(profile, context, cache, Collections.emptyList());
    }

    static ChunkBlockBuffer generateRaw(WorldGeneratorProfile profile, GenerationContext context,
            RawTerrainCache cache, List<BiomeRegionOverride> overrides) {
        ChunkBlockBuffer buffer = new ChunkBlockBuffer();
        context.terrainSource = profile.terrain;
        long started = GenerationMetrics.start();
        if (cache == null) {
            profile.terrain.fill(context, buffer);
            context.rawNeighborhood = new RawGenerationNeighborhood(profile.terrain, context, buffer);
        } else {
            BaseTerrainSnapshot base = cache.base(profile.terrain, context, buffer);
            context.rawNeighborhood = new RawGenerationNeighborhood(profile.terrain, context, base.blocks, cache);
        }
        GenerationMetrics.finish(GenerationMetrics.Counter.BASE_NANOS, started);
        if (!overrides.isEmpty()) {
            OwnedBiomeOverrideRuntime.replaceTerrain(context, buffer, overrides);
        }
        byte[] beforeCarvers = overrides.isEmpty() ? null : buffer.copyBlocks();
        byte[] beforeMetadata = overrides.isEmpty() ? null : buffer.copyMetadata();
        started = GenerationMetrics.start();
        for (CompiledCarver carver : profile.carvers) {
            carver.apply(context, buffer);
        }
        if (!overrides.isEmpty()) {
            OwnedBiomeOverrideRuntime.applyCarvers(context, buffer, beforeCarvers, beforeMetadata, overrides);
        }
        GenerationMetrics.finish(GenerationMetrics.Counter.CARVER_NANOS, started);
        started = GenerationMetrics.start();
        byte[] beforeSurfaces = overrides.isEmpty() ? null : buffer.copyBlocks();
        byte[] beforeSurfaceMetadata = overrides.isEmpty() ? null : buffer.copyMetadata();
        if (profile.applySurfaces) {
            profile.surfaces.apply(context, buffer);
        }
        if (!overrides.isEmpty()) {
            OwnedBiomeOverrideRuntime.applySurfaces(context, buffer, beforeSurfaces, beforeSurfaceMetadata, overrides);
        }
        GenerationMetrics.finish(GenerationMetrics.Counter.SURFACE_NANOS, started);
        return buffer;
    }

    @Override
    public Chunk prepareChunk(int x, int z) {
        return world == null ? nativeProvider.prepareChunk(x, z) : provideChunk(x, z);
    }

    @Override
    public void populate(IChunkProvider provider, int x, int z) {
        boolean falling = BlockSand.fallInstantly;
        try {
            BlockSand.fallInstantly = true;
            if (profile.population == null || profile.population.nativePopulation) {
                try (NativePopulationRuntime policy = NativePopulationRuntime.begin(profile.population)) {
                    nativeProvider.populate(provider, x, z);
                }
            }
            if (profile.population != null) {
                for (GenerationStage stage : GenerationStage.values()) {
                    if (profile.population.stages.contains(stage)) {
                        WorldGenRegistry.generateOwnedStage(world, x, z, stage);
                    }
                }
            }
        } finally {
            BlockSand.fallInstantly = falling;
        }
    }

    @Override
    public boolean saveChunks(boolean all, IProgressUpdate progress) {
        return nativeProvider.saveChunks(all, progress);
    }

    @Override
    public boolean unload100OldestChunks() {
        return nativeProvider.unload100OldestChunks();
    }

    @Override
    public boolean canSave() {
        return nativeProvider.canSave();
    }

    @Override
    public String makeString() {
        return nativeProvider.makeString();
    }
}
