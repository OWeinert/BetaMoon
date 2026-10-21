package betamoon.worldgen.terrain;

import betamoon.worldgen.surface.SurfaceRuleSet;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.src.BiomeGenBase;
import net.minecraft.src.Chunk;
import net.minecraft.src.ChunkProviderGenerate;
import net.minecraft.src.MapGenBase;
import net.minecraft.src.IChunkProvider;
import net.minecraft.src.World;

/** Detached native-stage edits for the currently generating Overworld chunk. */
public final class NativeBiomeOverrideRuntime {
    private static final ThreadLocal<Run> CURRENT = new ThreadLocal<Run>();
    private static final ThreadLocal<Boolean> SAMPLING_SURFACE = new ThreadLocal<Boolean>();

    private NativeBiomeOverrideRuntime() {
    }

    public static void afterTerrain(World world, int chunkX, int chunkZ, byte[] blocks, BiomeGenBase[] biomes) {
        List<BiomeRegionOverride> overrides = WorldGenerationRuntime.overrides(world);
        if (overrides.isEmpty()) {
            return;
        }
        Run run = new Run(world, chunkX, chunkZ, biomes, overrides, blocks);
        if (run.relevant()) {
            CURRENT.set(run);
        }
    }

    public static void afterSurface(ChunkProviderGenerate provider, World world, int chunkX, int chunkZ,
            byte[] blocks, BiomeGenBase[] biomes) {
        if (Boolean.TRUE.equals(SAMPLING_SURFACE.get())) {
            return;
        }
        Run run = CURRENT.get();
        if (run != null && run.matches(world, chunkX, chunkZ)) {
            run.captureNativeSurface(blocks);
            run.sync(blocks);
            run.replaceTerrain(provider, blocks, biomes);
        }
    }

    public static void carve(MapGenBase caves, IChunkProvider provider, World world, int chunkX, int chunkZ,
            byte[] blocks) {
        Run run = CURRENT.get();
        if (run == null || !run.matches(world, chunkX, chunkZ)) {
            caves.func_867_a(provider, world, chunkX, chunkZ, blocks);
            return;
        }
        byte[] before = blocks.clone();
        byte[] metadata = run.buffer.copyMetadata();
        byte[] nativeCaves = null;
        if (run.rawAffectsCaves) {
            nativeCaves = run.nativeSurface.clone();
            caves.func_867_a(provider, world, chunkX, chunkZ, nativeCaves);
        }
        caves.func_867_a(provider, world, chunkX, chunkZ, blocks);
        run.applyCarvers(before, metadata, blocks, nativeCaves);
    }

    public static void finish(World world, int chunkX, int chunkZ, Chunk chunk) {
        Run run = CURRENT.get();
        if (run == null || !run.matches(world, chunkX, chunkZ)) {
            return;
        }
        run.buffer.copyMetadataTo(chunk.data.data);
        chunk.func_1024_c();
        GeneratedChunkLighting.initialize(chunk);
    }

    public static void clear() {
        CURRENT.remove();
        SAMPLING_SURFACE.remove();
    }

    private static final class Run {
        private final World world;
        private final int chunkX;
        private final int chunkZ;
        private final GenerationContext context;
        private final List<BiomeRegionOverride> overrides;
        private final List<double[]> influences = new ArrayList<double[]>();
        private final ChunkBlockBuffer buffer = new ChunkBlockBuffer();
        private final byte[] rawBase;
        private byte[] nativeSurface;
        private boolean rawAffectsCaves;
        private SurfaceRuleSet[] surfaceRulesForCarvers;

        private Run(World world, int chunkX, int chunkZ, BiomeGenBase[] biomes,
                List<BiomeRegionOverride> overrides, byte[] nativeBlocks) {
            this.world = world;
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.overrides = overrides;
            rawBase = nativeBlocks.clone();
            context = new GenerationContext(WorldGenerationRuntime.fieldContext(world), chunkX, chunkZ, biomes);
            buffer.restore(nativeBlocks, new byte[ChunkBlockBuffer.CELL_COUNT / 2]);
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    int surface = 127;
                    while (surface >= 0 && nativeBlocks[(x << 11) | (z << 7) | surface] == 0) {
                        surface--;
                    }
                    context.preliminarySurface[z * 16 + x] = surface;
                }
            }
            for (BiomeRegionOverride override : overrides) {
                double[] weights = new double[256];
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        weights[z * 16 + x] = override.influence(context.fields, chunkX * 16 + x,
                                chunkZ * 16 + z);
                    }
                }
                influences.add(weights);
            }
        }

        private boolean matches(World other, int x, int z) {
            return world == other && chunkX == x && chunkZ == z;
        }

        private boolean relevant() {
            for (int index = 0; index < overrides.size(); index++) {
                BiomeRegionOverride override = overrides.get(index);
                if (override.terrain == null && override.material == null && override.carverMode.equals("inherit")
                        && override.surfaceMode.equals("inherit")) {
                    continue;
                }
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        if (selected(override, index, x, z)) {
                            return true;
                        }
                    }
                }
            }
            return false;
        }

        private void captureNativeSurface(byte[] blocks) {
            nativeSurface = blocks.clone();
        }

        private void replaceTerrain(ChunkProviderGenerate provider, byte[] blocks, BiomeGenBase[] biomes) {
            ChunkBlockBuffer raw = new ChunkBlockBuffer();
            raw.restore(rawBase, new byte[ChunkBlockBuffer.CELL_COUNT / 2]);
            boolean[] edited = new boolean[256];
            String[] surfaceModes = new String[256];
            SurfaceRuleSet[] surfaceRules = new SurfaceRuleSet[256];
            for (int index = 0; index < overrides.size(); index++) {
                BiomeRegionOverride override = overrides.get(index);
                if (!hasInfluence(index)) {
                    continue;
                }
                ChunkBlockBuffer replacement = null;
                GenerationContext sourceContext = null;
                if (override.terrain != null) {
                    replacement = new ChunkBlockBuffer();
                    sourceContext = new GenerationContext(context.fields, chunkX, chunkZ, context.copyBiomes());
                    sourceContext.terrainSource = override.terrain;
                    override.terrain.fill(sourceContext, replacement);
                }
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        if (!selected(override, index, x, z)) {
                            continue;
                        }
                        int column = z * 16 + x;
                        if (replacement != null) {
                            for (int y = 0; y < 128; y++) {
                                raw.set(x, y, z, replacement.blockId(x, y, z), replacement.metadata(x, y, z));
                            }
                            context.preliminarySurface[column] = sourceContext.preliminarySurface[column];
                            edited[column] = true;
                        }
                        if (override.material != null) {
                            edited[column] = true;
                        }
                        if (!override.surfaceMode.equals("inherit")) {
                            surfaceModes[column] = override.surfaceMode;
                            surfaceRules[column] = override.surface;
                        }
                    }
                }
                BiomeOverrideOperations.applyMaterials(context, raw, override);
            }
            byte[] unsurfaced = raw.copyBlocks();
            byte[] surfaced = unsurfaced.clone();
            boolean sample = false;
            for (boolean column : edited) {
                sample |= column;
            }
            if (sample) {
                try {
                    SAMPLING_SURFACE.set(Boolean.TRUE);
                    provider.replaceBlocksForBiome(chunkX, chunkZ, surfaced, biomes);
                } finally {
                    SAMPLING_SURFACE.remove();
                }
            }
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    int column = z * 16 + x;
                    if (!edited[column] && surfaceModes[column] == null) {
                        continue;
                    }
                    rawAffectsCaves = true;
                    boolean inheritedSurface = surfaceModes[column] == null;
                    for (int y = 0; y < 128; y++) {
                        int cell = (x << 11) | (z << 7) | y;
                        int id = inheritedSurface ? surfaced[cell] & 255 : unsurfaced[cell] & 255;
                        int damage = inheritedSurface && surfaced[cell] != unsurfaced[cell]
                                ? 0 : raw.metadata(x, y, z);
                        buffer.setGenerated(cell, id, damage);
                    }
                }
            }
            surfaceRulesForCarvers = surfaceRules;
            byte[] changed = buffer.copyBlocks();
            System.arraycopy(changed, 0, blocks, 0, blocks.length);
        }

        private void sync(byte[] blocks) {
            byte[] previous = buffer.copyBlocks();
            for (int index = 0; index < blocks.length; index++) {
                if (blocks[index] != previous[index]) {
                    buffer.setGenerated(index, blocks[index] & 255, 0);
                }
            }
        }

        private void applyCarvers(byte[] before, byte[] metadata, byte[] blocks, byte[] nativeCaves) {
            sync(blocks);
            for (int index = 0; index < overrides.size(); index++) {
                BiomeRegionOverride override = overrides.get(index);
                if (override.carverMode.equals("inherit") || !hasInfluence(index)) {
                    continue;
                }
                if (override.carverMode.equals("replace") || override.carverMode.equals("none")) {
                    for (int x = 0; x < 16; x++) {
                        for (int z = 0; z < 16; z++) {
                            if (!selected(override, index, x, z)) {
                                continue;
                            }
                            int start = (x << 11) | (z << 7);
                            for (int y = 0; y < 128; y++) {
                                int cell = start | y;
                                int damage = metadata[cell >> 1] >> ((cell & 1) * 4) & 15;
                                buffer.setGenerated(cell, before[cell] & 255, damage);
                            }
                        }
                    }
                }
                if (!override.carvers.isEmpty()) {
                    ChunkBlockBuffer carved = new ChunkBlockBuffer();
                    carved.restore(buffer.copyBlocks(), buffer.copyMetadata());
                    for (CompiledCarver carver : override.carvers) {
                        carver.apply(context, carved);
                    }
                    for (int x = 0; x < 16; x++) {
                        for (int z = 0; z < 16; z++) {
                            if (!selected(override, index, x, z)) {
                                continue;
                            }
                            for (int y = 0; y < 128; y++) {
                                buffer.set(x, y, z, carved.blockId(x, y, z), carved.metadata(x, y, z));
                            }
                        }
                    }
                }
            }
            if (nativeCaves != null) {
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        boolean affected = false;
                        for (int index = 0; index < overrides.size(); index++) {
                            BiomeRegionOverride override = overrides.get(index);
                            if ((override.terrain != null || override.material != null
                                    || !override.carverMode.equals("inherit")
                                    || !override.surfaceMode.equals("inherit"))
                                    && selected(override, index, x, z)) {
                                affected = true;
                                break;
                            }
                        }
                        if (!affected) {
                            int start = (x << 11) | (z << 7);
                            for (int y = 0; y < 128; y++) {
                                int cell = start | y;
                                buffer.setGenerated(cell, nativeCaves[cell] & 255, 0);
                            }
                        }
                    }
                }
            }
            if (surfaceRulesForCarvers != null) {
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        SurfaceRuleSet surface = surfaceRulesForCarvers[z * 16 + x];
                        if (surface != null) {
                            buffer.applySurface(surface, context, x, z);
                        }
                    }
                }
            }
            byte[] changed = buffer.copyBlocks();
            System.arraycopy(changed, 0, blocks, 0, blocks.length);
        }

        private boolean hasInfluence(int index) {
            for (double influence : influences.get(index)) {
                if (influence > 0) {
                    return true;
                }
            }
            return false;
        }

        private boolean selected(BiomeRegionOverride override, int index, int x, int z) {
            return BiomeOverrideOperations.selected(context.fields, override, influences.get(index)[z * 16 + x],
                    context.worldX(x), context.worldZ(z));
        }
    }
}
