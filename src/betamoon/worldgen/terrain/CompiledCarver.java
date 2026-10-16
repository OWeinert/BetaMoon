package betamoon.worldgen.terrain;

import betamoon.worldgen.SeedMixer;
import betamoon.worldgen.spatial.ScalarField;
import betamoon.worldgen.spatial.ScalarFieldCompiler;
import betamoon.worldgen.spatial.SpatialRegistry;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import net.minecraft.src.Block;

import static betamoon.worldgen.spatial.GenerationValues.bool;
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
 * Pure bounded masks; neighboring origins never read or write neighboring
 * chunks.
 */
public strictfp final class CompiledCarver {
    private static final int[][] NEIGHBORS = {{0, 0, 0}, {1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1},
            {0, 0, -1}};
    private final Mask mask;
    private final boolean[] replaceable;
    private final boolean[] protectedBlocks;
    private final boolean avoidFluids;
    private final TerrainBlockState carve;
    private final TerrainBlockState below;
    private final int belowLevel;
    private final boolean nativeCaves;
    private final List<String> includeBiomes = new ArrayList<String>();
    private final List<String> excludeBiomes = new ArrayList<String>();
    private final List<String> profiles = new ArrayList<String>();
    private int expandedMaskCount = 1;
    private long maximumMaskWork = 1;
    private final ThreadLocal<NativeCaveSampler> caveSampler = ThreadLocal.withInitial(NativeCaveSampler::new);
    public final String signature;

    public CompiledCarver(String key, Map<String, Object> declaration, SpatialRegistry.Snapshot spatial,
            Resolver resolver, Map<String, String> dependencies) {
        fields(declaration, key, "key", "type", "field", "min", "max", "children", "operation", "probability", "count",
                "minY", "maxY", "radius", "verticalRadius", "length", "salt", "replaceable", "protected", "avoidFluids",
                "carveBlock", "belowLevel", "belowBlock", "biomes", "profiles");
        nativeCaves = string(declaration, "type", "tunnel", key).equals("native_caves");
        validateFields(key, declaration);
        if (declaration.containsKey("biomes")) {
            Map<String, Object> biomes = object(declaration.get("biomes"), key + ".biomes");
            fields(biomes, key + ".biomes", "include", "exclude", "includeTags", "excludeTags");
            selectors(biomes.get("include"), includeBiomes, false, key);
            selectors(biomes.get("exclude"), excludeBiomes, false, key);
            selectors(biomes.get("includeTags"), includeBiomes, true, key);
            selectors(biomes.get("excludeTags"), excludeBiomes, true, key);
        }
        selectors(declaration.get("profiles"), profiles, false, key);

        replaceable = blocks(declaration.get("replaceable"), key + ".replaceable", true);
        protectedBlocks = blocks(declaration.get("protected"), key + ".protected", false);
        for (int metadata = 0; metadata < 16; metadata++) {
            protectedBlocks[(Block.bedrock.blockID << 4) | metadata] = true;
        }
        avoidFluids = bool(declaration, "avoidFluids", true, key);
        carve = TerrainBlockState.read(declaration.getOrDefault("carveBlock", 0.0), key + ".carveBlock");
        below = TerrainBlockState.read(declaration.getOrDefault("belowBlock", 11.0), key + ".belowBlock");
        belowLevel = declaration.containsKey("belowLevel")
                ? integer(declaration.get("belowLevel"), key + ".belowLevel", -1, 127)
                : -1;
        TreeMap<String, String> semantics = new TreeMap<String, String>(dependencies);
        semantics.put("carver", canonical(declaration));
        semantics.put("carve", carve.identity);
        semantics.put("below", below.identity);
        mask = nativeCaves ? null : compileMask(key, declaration, spatial, resolver, semantics);
        signature = GenerationSignature.of(semantics);
    }

    public void apply(GenerationContext context, ChunkBlockBuffer buffer) {
        if (nativeCaves) {
            byte[] original = buffer.copyBlocks();
            byte[] carved = original.clone();
            caveSampler.get().carve(context.fields.seed, context.chunkX, context.chunkZ, carved);
            for (int index = 0; index < carved.length; index++) {
                if (original[index] != carved[index]) {
                    buffer.set(index >> 11, index & 127, (index >> 7) & 15, carved[index] & 255, 0);
                }
            }
            return;
        }
        BitSet selected = sampleMask(context);
        byte[] original = avoidFluids ? buffer.copyBlocks() : null;
        for (int index = selected.nextSetBit(0); index >= 0; index = selected.nextSetBit(index + 1)) {
            int x = index >> 11;
            int y = index & 127;
            int z = (index >> 7) & 15;
            int id = buffer.blockId(x, y, z);
            int state = (id << 4) | buffer.metadata(x, y, z);
            if (!replaceable[state] || protectedBlocks[state]
                    || avoidFluids && touchesFluid(context, original, x, y, z)) {
                continue;
            }
            (y <= belowLevel ? below : carve).set(buffer, x, y, z);
        }
    }

    public long maximumMaskWork() {
        return maximumMaskWork;
    }

    private BitSet sampleMask(GenerationContext context) {
        BitSet selected = mask.sample(context);
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                if (!matchesColumn(context, x, z)) {
                    selected.clear((x << 11) | (z << 7), ((x << 11) | (z << 7)) + 128);
                }
            }
        }
        return selected;
    }

    private static void selectors(Object value, List<String> target, boolean tag, String path) {
        if (value == null) {
            return;
        }
        for (Object selector : list(value, path, 0, 64)) {
            target.add((tag ? "#" : "") + string(selector, path));
        }
    }

    private boolean matchesColumn(GenerationContext context, int x, int z) {
        for (String selector : excludeBiomes) {
            if (context.biomeMatches(x, z, selector)) {
                return false;
            }
        }
        boolean included = includeBiomes.isEmpty();
        for (String selector : includeBiomes) {
            included |= context.biomeMatches(x, z, selector);
        }
        if (!included) {
            return false;
        }
        if (profiles.isEmpty()) {
            return true;
        }
        if (context.terrainSource != null) {
            for (String profile : profiles) {
                if (context.terrainSource.usesHeightProfile(context.fields, context.worldX(x), context.worldZ(z),
                        profile)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void validateFields(String key, Map<String, Object> value) {
        String type = string(value, "type", "tunnel", key);
        List<String> allowed = new ArrayList<String>();
        allowed.add("key");
        allowed.add("type");
        if (!type.equals("native_caves")) {
            Collections.addAll(allowed, "replaceable", "protected", "avoidFluids", "carveBlock", "belowLevel",
                    "belowBlock", "biomes", "profiles");
            if (type.equals("field")) {
                Collections.addAll(allowed, "field", "min", "max", "minY", "maxY");
            } else if (type.equals("compose")) {
                Collections.addAll(allowed, "children", "operation");
            } else {
                Collections.addAll(allowed, "probability", "count", "minY", "maxY", "radius", "verticalRadius", "salt");
                if (!type.equals("room")) {
                    allowed.add("length");
                }
            }
        }
        fields(value, key, allowed.toArray(new String[allowed.size()]));
    }

    private Mask compileMask(String key, Map<String, Object> value, SpatialRegistry.Snapshot spatial, Resolver resolver,
            Map<String, String> signatures) {
        String type = string(value, "type", "tunnel", key);
        if (type.equals("compose")) {
            List<CompiledCarver> children = new ArrayList<CompiledCarver>();
            for (Object child : list(required(value, "children", key), key + ".children", 1, 16)) {
                CompiledCarver carver = child instanceof String
                        ? resolver.carver((String) child)
                        : resolver.inlineCarver(key + "/child_" + children.size(), object(child, key + ".children"));
                if (carver.nativeCaves) {
                    throw error(key, "native_caves is a raw pass rather than a composable mask");
                }
                maximumMaskWork += carver.maximumMaskWork;
                if (maximumMaskWork > 32000000) {
                    throw error(key, "composed carver exceeds the 32-million-operation work budget");
                }
                expandedMaskCount += carver.expandedMaskCount;
                if (expandedMaskCount > 32) {
                    throw error(key, "expanded carver composition exceeds the 32-mask work budget");
                }
                children.add(carver);
                signatures.put("child_" + children.size(), carver.signature);
            }
            String operation = string(value, "operation", "union", key);
            if (!operation.equals("union") && !operation.equals("intersection")) {
                throw error(key + ".operation", "expected union or intersection");
            }
            return context -> {
                BitSet result = children.get(0).sampleMask(context);
                for (int i = 1; i < children.size(); i++) {
                    BitSet next = children.get(i).sampleMask(context);
                    if (operation.equals("union")) {
                        result.or(next);
                    } else {
                        result.and(next);
                    }
                }
                return result;
            };
        }
        if (type.equals("field")) {
            ScalarField field = new ScalarFieldCompiler(key, spatial).compile(required(value, "field", key));
            double min = number(value, "min", 0, key);
            double max = number(value, "max", field.max, key);
            if (min > max || (long) field.sampleWork * ChunkBlockBuffer.CELL_COUNT > 8000000) {
                throw error(key, "invalid field interval or carver work budget exceeded");
            }
            maximumMaskWork = (long) field.sampleWork * ChunkBlockBuffer.CELL_COUNT;
            int minY = integer(value.getOrDefault("minY", 0.0), key + ".minY", 0, 127);
            int maxY = integer(value.getOrDefault("maxY", 127.0), key + ".maxY", minY, 127);
            return context -> {
                BitSet result = new BitSet(ChunkBlockBuffer.CELL_COUNT);
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        for (int y = minY; y <= maxY; y++) {
                            double sample = field.sample(context.fields, context.worldX(x), y, context.worldZ(z));
                            if (sample >= min && sample <= max) {
                                result.set((x << 11) | (z << 7) | y);
                            }
                        }
                    }
                }
                return result;
            };
        }
        if (!type.equals("tunnel") && !type.equals("room") && !type.equals("ravine")) {
            throw error(key + ".type", "unknown carver type");
        }
        double probability = number(value, "probability", 0.15, key);
        int count = integer(value.getOrDefault("count", 1.0), key + ".count", 1, 4);
        int minY = integer(value.getOrDefault("minY", 8.0), key + ".minY", 0, 127);
        int maxY = integer(value.getOrDefault("maxY", 80.0), key + ".maxY", minY, 127);
        double radius = number(value, "radius", type.equals("room") ? 8 : 3, key);
        double vertical = number(value, "verticalRadius", type.equals("ravine") ? 20 : radius, key);
        int length = integer(value.getOrDefault("length", type.equals("room") ? 1.0 : 48.0), key + ".length", 1, 96);
        if (probability < 0 || probability > 1 || radius <= 0 || radius > 16 || vertical <= 0 || vertical > 48) {
            throw error(key, "carver probability/radii exceed their supported bounds");
        }
        int reach = (int) Math.ceil((length + radius) / 16.0) + 1;
        maximumMaskWork = (long) (reach * 2 + 1) * (reach * 2 + 1) * count * length * (int) Math.ceil(radius * 2 + 1)
                * (int) Math.ceil(radius * 2 + 1) * (int) Math.ceil(vertical * 2 + 1);
        if (maximumMaskWork > 32000000) {
            throw error(key, "carver exceeds the 32-million-operation mask work budget; reduce count, length or radii");
        }
        long salt = SeedMixer.hash(key + ":" + value.getOrDefault("salt", "0"));
        return context -> {
            BitSet result = new BitSet(ChunkBlockBuffer.CELL_COUNT);
            for (int ox = context.chunkX - reach; ox <= context.chunkX + reach; ox++) {
                for (int oz = context.chunkZ - reach; oz <= context.chunkZ + reach; oz++) {
                    long origin = ((long) ox << 32) ^ (oz & 0xffffffffL);
                    Random random = new Random(SeedMixer.derive(SeedMixer.derive(context.fields.seed, salt), origin));
                    if (random.nextDouble() >= probability) {
                        continue;
                    }
                    for (int n = 0; n < count; n++) {
                        double x = ox * 16.0 + random.nextInt(16);
                        double z = oz * 16.0 + random.nextInt(16);
                        double y = minY + random.nextInt(maxY - minY + 1);
                        double angle = random.nextDouble() * Math.PI * 2;
                        double pitch = (random.nextDouble() - 0.5) * 0.15;
                        int steps = type.equals("room") ? 1 : length;
                        for (int step = 0; step < steps; step++) {
                            double shape = type.equals("room") ? 1 : 0.4 + 0.6 * Math.sin(Math.PI * step / steps);
                            ellipsoid(result, context, x, y, z, radius * shape, vertical * shape);
                            x += Math.cos(angle);
                            z += Math.sin(angle);
                            y += pitch;
                            angle += (random.nextDouble() - 0.5) * 0.12;
                        }
                    }
                }
            }
            return result;
        };
    }

    private static void ellipsoid(BitSet result, GenerationContext context, double cx, double cy, double cz,
            double radius, double vertical) {
        int minX = Math.max(0, (int) Math.floor(cx - radius - context.chunkX * 16));
        int maxX = Math.min(15, (int) Math.floor(cx + radius - context.chunkX * 16));
        int minZ = Math.max(0, (int) Math.floor(cz - radius - context.chunkZ * 16));
        int maxZ = Math.min(15, (int) Math.floor(cz + radius - context.chunkZ * 16));
        int minY = Math.max(0, (int) Math.floor(cy - vertical));
        int maxY = Math.min(127, (int) Math.floor(cy + vertical));
        for (int x = minX; x <= maxX; x++) {
            double dx = (context.worldX(x) + 0.5 - cx) / radius;
            for (int z = minZ; z <= maxZ; z++) {
                double dz = (context.worldZ(z) + 0.5 - cz) / radius;
                for (int y = minY; y <= maxY; y++) {
                    double dy = (y + 0.5 - cy) / vertical;
                    if (dx * dx + dy * dy + dz * dz < 1) {
                        result.set((x << 11) | (z << 7) | y);
                    }
                }
            }
        }
    }

    private static boolean[] blocks(Object value, String path, boolean defaults) {
        boolean[] result = new boolean[4096];
        if (value == null) {
            if (defaults) {
                for (int block = 1; block <= 3; block++) {
                    for (int metadata = 0; metadata < 16; metadata++) {
                        result[(block << 4) | metadata] = true;
                    }
                }
            }
            return result;
        }
        for (Object block : list(value, path, 0, 256)) {
            TerrainBlockState state = TerrainBlockState.read(block, path);
            boolean exact = block instanceof Map
                    && (((Map<?, ?>) block).containsKey("metadata") || ((Map<?, ?>) block).containsKey("state"));
            for (int metadata = 0; metadata < 16; metadata++) {
                if (!exact || metadata == state.metadata) {
                    result[(state.id << 4) | metadata] = true;
                }
            }
        }
        return result;
    }

    private static boolean touchesFluid(GenerationContext context, byte[] original, int x, int y, int z) {
        for (int[] offset : NEIGHBORS) {
            int nx = x + offset[0];
            int ny = y + offset[1];
            int nz = z + offset[2];
            int id = context.rawNeighborhood != null
                    ? context.rawNeighborhood.block(context.worldX(nx), ny, context.worldZ(nz))
                    : nx >= 0 && nx < 16 && ny >= 0 && ny < 128 && nz >= 0 && nz < 16
                            ? original[(nx << 11) | (nz << 7) | ny] & 255
                            : 0;
            if (id != 0) {
                Block block = Block.blocksList[id];
                if (block != null && block.blockMaterial.getIsLiquid()) {
                    return true;
                }
            }
        }
        return false;
    }

    public interface Resolver {
        CompiledCarver carver(String key);

        default CompiledCarver inlineCarver(String path, Map<String, Object> value) {
            throw error(path, "inline carver resolution is unavailable");
        }
    }

    private interface Mask {
        BitSet sample(GenerationContext context);
    }
}
