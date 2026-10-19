package betamoon.worldgen.terrain;

import betamoon.worldgen.GenerationMetrics;
import betamoon.worldgen.spatial.FieldContext;
import betamoon.worldgen.spatial.ScalarField;
import betamoon.worldgen.spatial.ScalarFieldCompiler;
import betamoon.worldgen.spatial.SpatialRegistry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.src.Block;

import static betamoon.worldgen.spatial.GenerationValues.canonical;
import static betamoon.worldgen.spatial.GenerationValues.error;
import static betamoon.worldgen.spatial.GenerationValues.fields;
import static betamoon.worldgen.spatial.GenerationValues.integer;
import static betamoon.worldgen.spatial.GenerationValues.list;
import static betamoon.worldgen.spatial.GenerationValues.number;
import static betamoon.worldgen.spatial.GenerationValues.object;
import static betamoon.worldgen.spatial.GenerationValues.required;
import static betamoon.worldgen.spatial.GenerationValues.string;

/**
 * Void, layered, heightmap and interpolated density sources use one
 * raw-material path.
 */
public strictfp final class CompiledTerrainSource implements TerrainSource {
    private final String type;
    private final String signature;
    private final int minY;
    private final int maxY;
    private final List<TerrainBlockState> layers = new ArrayList<TerrainBlockState>();
    private final List<Integer> depths = new ArrayList<Integer>();
    private final ScalarField height;
    private final ScalarField ceiling;
    private final boolean outside;
    private final ScalarField continental;
    private final ScalarField erosion;
    private final ScalarField density;
    private final ScalarField fluidLevel;
    private final MaterialRule solid;
    private final TerrainBlockState fluid;
    private final TerrainBlockState air;
    private final TerrainBlockState ice;
    private final TerrainBlockState occupancyStone = new TerrainBlockState(1, 0);
    private final TerrainBlockState floor;
    private final int floorHeight;
    private final int cellX;
    private final int cellY;
    private final int cellZ;
    private final TerrainBlockState platform;
    private final int platformSize;
    private final int platformY;
    private final HeightSampler heightSampler;
    private final double betaBaseHeight;
    private final double betaHeightScale;
    private final double betaDetailScale;
    private static final ThreadLocal<BetaCache> BETA_CACHE = new ThreadLocal<BetaCache>();

    public CompiledTerrainSource(String key, Map<String, Object> value, SpatialRegistry.Snapshot spatial,
            MaterialRule.Resolver materials, Map<String, String> dependencies) {
        this(key, value, spatial, materials, dependencies, null);
    }

    public CompiledTerrainSource(String key, Map<String, Object> value, SpatialRegistry.Snapshot spatial,
            MaterialRule.Resolver materials, Map<String, String> dependencies, HeightSampler heightSampler) {
        this.heightSampler = heightSampler;
        type = string(value, "type", "heightmap", key);
        fields(value, key, "key", "type", "minY", "maxY", "layers", "above", "height", "density", "solidWhen", "solid",
                "material", "fluid", "seaLevel", "air", "floorHeight", "floorMaterial", "cell", "preliminarySurface",
                "spawnPlatform", "preset", "baseHeight", "heightScale", "detailScale", "noise", "ceiling", "inside");
        if (!Arrays.asList("native", "void", "flat", "heightmap", "density", "beta_compatible").contains(type)) {
            throw error(key + ".type", "unknown raw terrain source");
        }
        validateFields(value, type, key);
        betaBaseHeight = number(value, "baseHeight", 68, key);
        betaHeightScale = number(value, "heightScale", 1, key);
        betaDetailScale = number(value, "detailScale", 1, key);
        if (betaBaseHeight < 1 || betaBaseHeight > 126 || betaHeightScale <= 0 || betaHeightScale > 8
                || betaDetailScale < 0 || betaDetailScale > 8) {
            throw error(key, "Beta terrain parameters exceed their finite bounds");
        }
        if (value.containsKey("preset") && !string(value.get("preset"), key).equals("vanilla_beta")) {
            throw error(key + ".preset", "expected vanilla_beta");
        }
        minY = value.containsKey("minY") ? integer(value.get("minY"), key + ".minY", 0, 127) : 0;
        maxY = value.containsKey("maxY") ? integer(value.get("maxY"), key + ".maxY", minY, 127) : 127;
        ScalarFieldCompiler compiler = new ScalarFieldCompiler(key, spatial);
        Object heightValue = value
                .get(type.equals("density") || type.equals("beta_compatible") ? "preliminarySurface" : "height");
        height = heightValue == null ? null : compiler.compile(heightValue);
        if (height != null && (height.dependencies & 2) != 0) {
            throw error(key + ".height", "height/preliminarySurface must not depend on Y");
        }
        ceiling = value.containsKey("ceiling")
                ? new ScalarFieldCompiler(key, spatial).compile(value.get("ceiling"))
                : null;
        if (ceiling != null && (ceiling.dependencies & 2) != 0) {
            throw error(key + ".ceiling", "ceiling must not depend on Y");
        }
        String inside = string(value, "inside", "between", key);
        if (!inside.equals("between") && !inside.equals("outside") || value.containsKey("inside") && ceiling == null) {
            throw error(key + ".inside", "expected between or outside with a ceiling envelope");
        }
        outside = inside.equals("outside");
        Map<String, Object> noise = value.containsKey("noise")
                ? object(value.get("noise"), key + ".noise")
                : Collections.<String, Object>emptyMap();
        fields(noise, key + ".noise", "continental", "erosion");
        continental = betaField(noise.get("continental"), spatial, key + ".noise.continental");
        erosion = betaField(noise.get("erosion"), spatial, key + ".noise.erosion");
        density = type.equals("density")
                ? new ScalarFieldCompiler(key, spatial).compile(required(value, "density", key))
                : null;
        if (!string(value, "solidWhen", "positive", key).equals("positive")) {
            throw error(key + ".solidWhen", "expected positive");
        }
        Object solidDeclaration = value.getOrDefault("material", value.getOrDefault("solid", 1.0));
        solid = MaterialRule.compile(solidDeclaration, key + ".material", materials, spatial, 0);
        ice = type.equals("beta_compatible") ? new TerrainBlockState(79, 0) : null;
        if ((type.equals("density") || type.equals("beta_compatible")) && height == null && solid.requiresEnvelope) {
            throw error(key + ".preliminarySurface", "material rules require an explicit X/Z height envelope");
        }
        air = TerrainBlockState.read(value.getOrDefault("air", value.getOrDefault("above", 0.0)), key + ".air");
        floor = TerrainBlockState.read(value.getOrDefault("floorMaterial", 7.0), key + ".floorMaterial");
        floorHeight = value.containsKey("floorHeight")
                ? integer(value.get("floorHeight"), key + ".floorHeight", -1, 127)
                : -1;
        Object fluidDeclaration = value.get("fluid");
        Object seaLevel = value.getOrDefault("seaLevel", type.equals("beta_compatible") ? 63.0 : null);
        if (type.equals("density") && fluidDeclaration instanceof Map
                && object(fluidDeclaration, key).containsKey("level")) {
            Map<String, Object> config = object(fluidDeclaration, key + ".fluid");
            fields(config, key + ".fluid", "level", "material");
            seaLevel = config.get("level");
            fluidDeclaration = config.get("material");
        }
        fluid = TerrainBlockState.read(fluidDeclaration == null ? 9.0 : fluidDeclaration, key + ".fluid");
        fluidLevel = seaLevel == null ? null : new ScalarFieldCompiler(key, spatial).compile(seaLevel);
        if (fluidLevel != null && ((fluidLevel.dependencies & 2) != 0 || fluidLevel.min < -1 || fluidLevel.max > 127)) {
            throw error(key + ".seaLevel", "fluid level must be an X/Z field in -1..127");
        }
        if (type.equals("heightmap") && height == null && heightSampler == null) {
            throw error(key + ".height", "heightmap requires a height field");
        }
        if (type.equals("flat")) {
            int total = 0;
            for (Object entry : list(required(value, "layers", key), key + ".layers", 1, 128)) {
                Map<String, Object> layer = object(entry, key + ".layers");
                fields(layer, key + ".layers", "block", "metadata", "state", "depth");
                Map<String, Object> state = new LinkedHashMap<String, Object>(layer);
                state.remove("depth");
                TerrainBlockState block = TerrainBlockState.read(state, key + ".layers.block");
                int depth = integer(required(layer, "depth", key), key + ".layers.depth", 1, 128);
                layers.add(block);
                depths.add(depth);
                total += depth;
            }
            if (total > maxY - minY + 1) {
                throw error(key, "flat layers exceed the declared height range");
            }
        }
        if (value.containsKey("spawnPlatform")) {
            Map<String, Object> config = object(value.get("spawnPlatform"), key + ".spawnPlatform");
            fields(config, key, "block", "size", "y");
            platform = TerrainBlockState.read(required(config, "block", key), key + ".spawnPlatform.block");
            platformSize = integer(config.getOrDefault("size", 5.0), key + ".spawnPlatform.size", 1, 31);
            platformY = integer(config.getOrDefault("y", 63.0), key + ".spawnPlatform.y", 0, 125);
            if (platformSize % 2 == 0 || platformY < minY || platformY > maxY) {
                throw error(key + ".spawnPlatform", "size must be odd and platform Y must lie within the source range");
            }
        } else {
            platform = null;
            platformSize = 0;
            platformY = 0;
        }
        Map<String, Object> cell = value.containsKey("cell")
                ? object(value.get("cell"), key + ".cell")
                : Collections.<String, Object>emptyMap();
        fields(cell, key + ".cell", "x", "y", "z");
        cellX = cell(cell, "x", 4, key);
        cellY = cell(cell, "y", 8, key);
        cellZ = cell(cell, "z", 4, key);
        if (type.equals("beta_compatible") && (cellX != 4 || cellY != 8 || cellZ != 4)) {
            throw error(key + ".cell", "Beta basis requires 4x8x4 cells");
        }
        if (density != null
                && (long) density.sampleWork * (16 / cellX + 1) * (128 / cellY + 1) * (16 / cellZ + 1) > 16000000) {
            throw error(key, "density sampling exceeds the chunk work budget");
        }
        TreeMap<String, String> semantics = new TreeMap<String, String>(dependencies);
        semantics.put("source", canonical(value));
        int index = 0;
        for (String identity : solid.identities) {
            semantics.put("solid_" + index++, identity);
        }
        for (TerrainBlockState layer : layers) {
            semantics.put("layer_" + index++, layer.identity);
        }
        semantics.put("air", air.identity);
        semantics.put("fluid", fluid.identity);
        semantics.put("floor", floor.identity);
        if (platform != null) {
            semantics.put("platform", platform.identity);
        }
        signature = GenerationSignature.of(semantics);
    }

    private static int cell(Map<String, Object> value, String axis, int fallback, String path) {
        int size = value.containsKey(axis) ? integer(value.get(axis), path + ".cell." + axis, 1, 16) : fallback;
        if ((size & (size - 1)) != 0) {
            throw error(path + ".cell." + axis, "must be a power of two");
        }
        return size;
    }

    private static ScalarField betaField(Object value, SpatialRegistry.Snapshot spatial, String path) {
        if (value == null) {
            return null;
        }
        ScalarField field = new ScalarFieldCompiler(path, spatial).compile(value);
        if ((field.dependencies & 2) != 0 || field.min < -1 || field.max > 1) {
            throw error(path, "expected an X/Z field bounded within -1..1");
        }
        return field;
    }

    private static void validateFields(Map<String, Object> value, String type, String path) {
        if (type.equals("native")) {
            fields(value, path, "key", "type");
        } else if (type.equals("flat")) {
            fields(value, path, "key", "type", "minY", "maxY", "layers", "above", "air");
        } else if (type.equals("void")) {
            fields(value, path, "key", "type", "minY", "maxY", "above", "air", "seaLevel", "fluid", "spawnPlatform");
        } else if (type.equals("heightmap")) {
            fields(value, path, "key", "type", "minY", "maxY", "height", "ceiling", "inside", "solid", "material",
                    "seaLevel", "fluid", "air", "floorHeight", "floorMaterial");
        } else if (type.equals("density")) {
            fields(value, path, "key", "type", "minY", "maxY", "density", "solidWhen", "cell", "preliminarySurface",
                    "solid", "material", "seaLevel", "fluid", "air", "floorHeight", "floorMaterial");
        } else {
            fields(value, path, "key", "type", "minY", "maxY", "preset", "baseHeight", "heightScale", "detailScale",
                    "noise", "cell", "preliminarySurface", "solid", "material", "seaLevel", "fluid", "air",
                    "floorHeight", "floorMaterial");
        }
    }

    @Override
    public boolean isNative() {
        return type.equals("native");
    }

    @Override
    public String signature() {
        return signature;
    }

    @Override
    public double height(FieldContext context, int x, int z) {
        if (ceiling != null) {
            double lower = height.sample(context, x, 0, z);
            double upper = ceiling.sample(context, x, 0, z);
            if (outside) {
                return Math.ceil(upper) <= maxY ? maxY : Math.max(minY - 1, Math.min(maxY, Math.floor(lower)));
            }
            return Math.max(minY, Math.ceil(lower)) > Math.min(maxY, Math.floor(upper))
                    ? minY - 1
                    : Math.min(maxY, upper);
        }
        if ((type.equals("beta_compatible") || type.equals("density")) && height == null) {
            return TerrainHeightPreview.heights(this, context, x >> 4, z >> 4).oceanFloor(x & 15, z & 15);
        }
        if (heightSampler != null) {
            return Math.max(minY - 1, Math.min(maxY, heightSampler.height(context, x, z)));
        }
        if (height != null) {
            return Math.max(minY - 1, Math.min(maxY, height.sample(context, x, 0, z)));
        }
        if (type.equals("flat")) {
            int y = minY - 1;
            for (int depth : depths) {
                y += depth;
            }
            return y;
        }
        if (type.equals("void") && platform != null && context.hasOriginalSpawn
                && Math.abs((long) x - context.spawnX) <= platformSize / 2
                && Math.abs((long) z - context.spawnZ) <= platformSize / 2) {
            return platformY;
        }
        return -1;
    }

    public interface HeightSampler {
        double height(FieldContext context, int x, int z);
    }

    @Override
    public void fill(GenerationContext context, ChunkBlockBuffer buffer) {
        GenerationMetrics.record(GenerationMetrics.Counter.SOURCE_FILLS, 1);
        if (isNative()) {
            throw new IllegalStateException("Native terrain requires provider delegation");
        }
        double[] volume = density == null ? null : densitySamples(context);
        if (type.equals("beta_compatible")) {
            if (context.fields.environment == null) {
                throw new IllegalStateException("Beta terrain requires a climate sampling context");
            }
            BetaCache cached = BETA_CACHE.get();
            if (cached == null || cached.seed != context.fields.seed) {
                cached = new BetaCache(context.fields.seed);
                BETA_CACHE.set(cached);
            }
            volume = cached.sampler.sample(context, betaBaseHeight, betaHeightScale, betaDetailScale, continental,
                    erosion);
        }
        BitSet densityCells = volume == null ? null : densityOccupancy(volume);
        BitSet materialCells = new BitSet(ChunkBlockBuffer.CELL_COUNT);
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                double h = (type.equals("beta_compatible") || type.equals("density")) && height == null
                        ? -1
                        : height(context.fields, context.worldX(x), context.worldZ(z));
                context.preliminarySurface[z * 16 + x] = h;
                if (type.equals("flat")) {
                    int y = minY;
                    for (int i = 0; i < layers.size(); i++) {
                        TerrainBlockState block = layers.get(i);
                        int end = y + depths.get(i) - 1;
                        buffer.fillColumn(x, z, y, end, block.id, block.metadata);
                        y = end + 1;
                    }
                    if (y <= maxY && air.id != 0) {
                        buffer.fillColumn(x, z, y, maxY, air.id, air.metadata);
                    }
                    continue;
                }
                int fluidY = fluidLevel == null
                        ? -1
                        : (int) Math.floor(fluidLevel.sample(context.fields, context.worldX(x), 0, context.worldZ(z)));
                double lower = ceiling == null
                        ? h
                        : height.sample(context.fields, context.worldX(x), 0, context.worldZ(z));
                double upper = ceiling == null
                        ? h
                        : ceiling.sample(context.fields, context.worldX(x), 0, context.worldZ(z));
                if (ceiling == null
                        && (type.equals("void") || type.equals("heightmap") && solid.constantState != null)) {
                    fillConstantColumn(context, buffer, x, z, h, fluidY);
                    continue;
                }
                for (int y = minY; y <= maxY; y++) {
                    TerrainBlockState block;
                    boolean occupied = volume == null
                            ? y <= Math.floor(h) && !type.equals("void")
                            : densityCells.get((x << 11) | (z << 7) | y);
                    if (ceiling != null) {
                        occupied = outside
                                ? y <= Math.floor(lower) || y >= Math.ceil(upper)
                                : y >= Math.ceil(lower) && y <= Math.floor(upper);
                    }
                    if (occupied) {
                        block = solid.constantState == null ? occupancyStone : solid.constantState;
                        if (solid.constantState == null) {
                            materialCells.set((x << 11) | (z << 7) | y);
                        }
                    } else {
                        block = y <= fluidY ? fluid : air;
                    }
                    if (type.equals("beta_compatible") && block == fluid && y == fluidY
                            && (fluid.id == Block.waterMoving.blockID || fluid.id == Block.waterStill.blockID)
                            && context.fields.environment.biomeValue("temperature", context.worldX(x),
                                    context.worldZ(z)) < 0.5) {
                        block = ice;
                    }
                    if (y <= floorHeight) {
                        materialCells.clear((x << 11) | (z << 7) | y);
                        block = floor;
                    }
                    if (platform != null && y == platformY && context.fields.hasOriginalSpawn
                            && Math.abs((long) context.worldX(x) - context.fields.spawnX) <= platformSize / 2
                            && Math.abs((long) context.worldZ(z) - context.fields.spawnZ) <= platformSize / 2) {
                        materialCells.clear((x << 11) | (z << 7) | y);
                        block = platform;
                    }
                    context.writeGenerated(buffer, x, y, z, block);
                }
            }
        }
        if (!materialCells.isEmpty()) {
            context.captureMaterialInput(buffer, height != null || heightSampler != null ? this::height : null,
                    solid.inputRequirements);
            for (int index = materialCells.nextSetBit(0); index >= 0; index = materialCells.nextSetBit(index + 1)) {
                int x = index >> 11;
                int y = index & 127;
                int z = (index >> 7) & 15;
                TerrainBlockState block = solid.sample(context, x, y, z);
                if (block == null) {
                    throw new IllegalStateException(
                            "No material rule matched at " + context.worldX(x) + "," + y + "," + context.worldZ(z));
                }
                context.writeGenerated(buffer, x, y, z, block);
            }
        }
    }

    private void fillConstantColumn(GenerationContext context, ChunkBlockBuffer buffer, int x, int z, double height,
            int fluidY) {
        int solidTop = type.equals("void") ? minY - 1 : Math.min(maxY, (int) Math.floor(height));
        int first = minY;
        // Floor precedence also avoids touching an otherwise unreachable material
        // state.
        if (floorHeight >= first) {
            int end = Math.min(maxY, floorHeight);
            buffer.fillColumn(x, z, first, end, floor.id, floor.metadata);
            first = end + 1;
        }
        if (solidTop >= first) {
            buffer.fillColumn(x, z, first, solidTop, solid.constantState.id, solid.constantState.metadata);
            first = solidTop + 1;
        }
        if (fluidY >= first && first <= maxY) {
            int end = Math.min(maxY, fluidY);
            buffer.fillColumn(x, z, first, end, fluid.id, fluid.metadata);
            first = end + 1;
        }
        if (first <= maxY) {
            buffer.fillColumn(x, z, first, maxY, air.id, air.metadata);
        }
        if (platform != null && context.fields.hasOriginalSpawn
                && Math.abs((long) context.worldX(x) - context.fields.spawnX) <= platformSize / 2
                && Math.abs((long) context.worldZ(z) - context.fields.spawnZ) <= platformSize / 2) {
            context.writeGenerated(buffer, x, platformY, z, platform);
        }
    }

    private double[] densitySamples(GenerationContext context) {
        int sx = 16 / cellX + 1;
        int sy = 128 / cellY + 1;
        int sz = 16 / cellZ + 1;
        double[] result = new double[sx * sy * sz];
        int cursor = 0;
        for (int x = 0; x < sx; x++) {
            for (int z = 0; z < sz; z++) {
                for (int y = 0; y < sy; y++) {
                    result[cursor++] = density.sample(context.fields, context.worldX(x * cellX), y * cellY,
                            context.worldZ(z * cellZ));
                }
            }
        }
        return result;
    }

    private BitSet densityOccupancy(double[] data) {
        BitSet occupied = new BitSet(ChunkBlockBuffer.CELL_COUNT);
        int ny = 128 / cellY + 1;
        int nz = 16 / cellZ + 1;
        for (int cx = 0; cx < 16 / cellX; cx++) {
            for (int cz = 0; cz < 16 / cellZ; cz++) {
                for (int cy = minY / cellY; cy <= maxY / cellY; cy++) {
                    double a = data[(cx * nz + cz) * ny + cy];
                    double b = data[(cx * nz + cz + 1) * ny + cy];
                    double c = data[((cx + 1) * nz + cz) * ny + cy];
                    double d = data[((cx + 1) * nz + cz + 1) * ny + cy];
                    double da = (data[(cx * nz + cz) * ny + cy + 1] - a) / cellY;
                    double db = (data[(cx * nz + cz + 1) * ny + cy + 1] - b) / cellY;
                    double dc = (data[((cx + 1) * nz + cz) * ny + cy + 1] - c) / cellY;
                    double dd = (data[((cx + 1) * nz + cz + 1) * ny + cy + 1] - d) / cellY;
                    for (int dy = 0; dy < cellY; dy++) {
                        int y = cy * cellY + dy;
                        if (y >= minY && y <= maxY) {
                            double ax = (c - a) / cellX;
                            double bx = (d - b) / cellX;
                            double va = a;
                            double vb = b;
                            for (int dx = 0; dx < cellX; dx++) {
                                double az = (vb - va) / cellZ;
                                double value = va;
                                for (int dz = 0; dz < cellZ; dz++) {
                                    if (value > 0) {
                                        occupied.set(((cx * cellX + dx) << 11) | ((cz * cellZ + dz) << 7) | y);
                                    }
                                    value += az;
                                }
                                va += ax;
                                vb += bx;
                            }
                        }
                        a += da;
                        b += db;
                        c += dc;
                        d += dd;
                    }
                }
            }
        }
        return occupied;
    }

    private static final class BetaCache {
        private final long seed;
        private final BetaDensitySampler sampler;

        private BetaCache(long seed) {
            this.seed = seed;
            sampler = new BetaDensitySampler(seed);
        }
    }

}
