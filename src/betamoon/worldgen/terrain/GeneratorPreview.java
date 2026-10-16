package betamoon.worldgen.terrain;

import betamoon.worldgen.GenerationStage;
import betamoon.worldgen.spatial.FieldContext;
import betamoon.worldgen.spatial.NativeBiomeEnvironment;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.src.WorldChunkManager;

/**
 * Read-only raw preview: no chunk publication, native population, world RNG or
 * neighbor requests.
 */
public final class GeneratorPreview {
    private GeneratorPreview() {
    }

    public static Map<String, Object> describe(WorldGeneratorProfile profile) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("key", profile.key);
        result.put("signature", profile.signature);
        result.put("owner", profile.owner);
        result.put("nativeTerrain", profile.terrain == null || profile.terrain.isNative());
        result.put("surfaces", profile.applySurfaces);
        result.put("carvers", profile.carvers.size());
        List<Object> stages = new ArrayList<Object>();
        if (profile.population != null) {
            for (GenerationStage stage : profile.population.stages) {
                stages.add(stage.getName());
            }
        }
        result.put("stages", stages);
        Map<String, Object> stagePolicy = new LinkedHashMap<String, Object>();
        for (GenerationStage stage : GenerationStage.values()) {
            stagePolicy.put(stage.getName(),
                    profile.population == null
                            ? "after_vanilla_population"
                            : profile.population.stages.contains(stage) ? "enabled" : "disabled_by_policy");
        }
        result.put("stagePolicy", stagePolicy);
        List<Object> nativeCategories = new ArrayList<Object>();
        if (profile.population == null) {
            Collections.addAll(nativeCategories, "lakes", "dungeons", "clay", "ores", "trees", "vegetation", "springs",
                    "snow", "animals");
        } else {
            nativeCategories.addAll(profile.population.nativeCategories);
        }
        result.put("nativeCategories", nativeCategories);
        Map<String, Object> filters = new LinkedHashMap<String, Object>();
        if (profile.settings != null) {
            filters.put("features", profile.settings.features.describe());
            filters.put("structures", profile.settings.structures.describe());
        }
        result.put("contentFilters", filters);
        result.put("dependencies", new TreeMap<String, String>(profile.dependencies));
        return result;
    }

    public static Map<String, Object> chunk(WorldGeneratorProfile profile, FieldContext fields, int x, int z) {
        if (profile.terrain == null || profile.terrain.isNative()) {
            throw new IllegalArgumentException("native_preview_unavailable");
        }
        fields = previewFields(profile, fields);
        GenerationContext context = GenerationContext.preview(fields, x, z);
        ChunkBlockBuffer buffer = new ChunkBlockBuffer();
        Map<String, Object> stages = new LinkedHashMap<String, Object>();
        long started = System.nanoTime();
        context.terrainSource = profile.terrain;
        profile.terrain.fill(context, buffer);
        context.rawNeighborhood = new RawGenerationNeighborhood(profile.terrain, context, buffer);
        stages.put("terrainMs", (System.nanoTime() - started) / 1000000.0);
        started = System.nanoTime();
        for (CompiledCarver carver : profile.carvers) {
            carver.apply(context, buffer);
        }
        stages.put("carversMs", (System.nanoTime() - started) / 1000000.0);
        started = System.nanoTime();
        if (profile.applySurfaces) {
            profile.surfaces.apply(context, buffer);
        }
        stages.put("surfacesMs", (System.nanoTime() - started) / 1000000.0);
        Map<String, Object> result = describe(profile);
        result.put("chunkX", x);
        result.put("chunkZ", z);
        result.put("timings", stages);
        TerrainHeightmaps heights = new TerrainHeightmaps(buffer);
        Map<String, Object> maps = new LinkedHashMap<String, Object>();
        maps.put("world_surface", map(heights, 0));
        maps.put("ocean_floor", map(heights, 1));
        maps.put("fluid_surface", map(heights, 2));
        maps.put("motion_blocking", map(heights, 3));
        result.put("heightmaps", maps);
        Map<String, Object> materials = new TreeMap<String, Object>();
        byte[] blocks = buffer.copyBlocks();
        byte[] metadata = buffer.copyMetadata();
        for (int i = 0; i < blocks.length; i++) {
            int state = metadata[i >> 1] >> ((i & 1) * 4) & 15;
            String material = (blocks[i] & 255) + ":" + state;
            materials.put(material, ((Number) materials.getOrDefault(material, 0)).intValue() + 1);
        }
        result.put("materials", materials);
        Map<String, String> raw = new LinkedHashMap<String, String>();
        raw.put("blocks", hex(blocks));
        raw.put("metadata", hex(metadata));
        result.put("rawHash", GenerationSignature.of(raw));
        return result;
    }

    private static FieldContext previewFields(WorldGeneratorProfile profile, FieldContext fields) {
        FieldContext.Environment environment = fields.environment;
        boolean matching = environment instanceof ProfileWorldChunkManager
                && ((ProfileWorldChunkManager) environment).usesSource(profile.biomeSource)
                && fields.spatial == profile.spatial;
        WorldChunkManager climate = environment instanceof ProfileWorldChunkManager
                ? ((ProfileWorldChunkManager) environment).climateManager()
                : environment instanceof NativeBiomeEnvironment
                        ? ((NativeBiomeEnvironment) environment).manager()
                        : null;
        ProfileWorldChunkManager preview = !matching && climate != null
                ? new ProfileWorldChunkManager(climate, profile.biomeSource)
                : null;
        FieldContext result = new FieldContext(fields.seed, fields.dimension, fields.hasOriginalSpawn, fields.spawnX,
                fields.spawnZ, preview == null ? environment : preview, profile.spatial);
        if (preview != null) {
            preview.setFieldContext(result);
        }
        return result;
    }

    private static List<Object> map(TerrainHeightmaps heights, int kind) {
        List<Object> result = new ArrayList<Object>(256);
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                result.add(kind == 0
                        ? heights.worldSurface(x, z)
                        : kind == 1
                                ? heights.oceanFloor(x, z)
                                : kind == 2 ? heights.fluidSurface(x, z) : heights.motionBlocking(x, z));
            }
        }
        return result;
    }

    private static String hex(byte[] bytes) {
        char[] digits = "0123456789abcdef".toCharArray();
        char[] output = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            output[i * 2] = digits[(bytes[i] & 255) >>> 4];
            output[i * 2 + 1] = digits[bytes[i] & 15];
        }
        return new String(output);
    }
}
