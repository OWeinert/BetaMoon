package betamoon.entity;

import betamoon.assets.AssetKey;
import betamoon.dimension.DimensionIdentity;
import betamoon.dimension.DimensionSelector;
import betamoon.worldgen.terrain.WorldGenerationRuntime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.src.ChunkCoordIntPair;
import net.minecraft.src.ChunkCoordinates;
import net.minecraft.src.EntityPlayer;
import net.minecraft.src.MathHelper;
import net.minecraft.src.World;

/** Adds custom natural groups after vanilla's bounded spawning pass. */
public final class EntityNaturalSpawner {
    private static final int CHUNK_RADIUS = 8;
    private static final int CHUNK_ATTEMPT_CHANCE = 200;
    private static final int GROUP_POSITION_ATTEMPTS = 4;
    private static final double MIN_PLAYER_DISTANCE_SQUARED = 24 * 24;

    private EntityNaturalSpawner() {
    }

    public static int spawn(World world, boolean hostile, boolean peaceful) {
        if (world == null || world.multiplayerWorld || world.playerEntities.isEmpty() || (!hostile && !peaceful)) {
            return 0;
        }
        List<EntityTypeDefinition> definitions = available(world, hostile, peaceful);
        if (definitions.isEmpty()) {
            return 0;
        }
        Map<AssetKey, Integer> counts = counts(world);
        Set<ChunkCoordIntPair> chunks = eligibleChunks(world);
        int spawned = 0;
        for (ChunkCoordIntPair chunk : chunks) {
            if (world.rand.nextInt(CHUNK_ATTEMPT_CHANCE) != 0) {
                continue;
            }
            int x = chunk.chunkXPos * 16 + world.rand.nextInt(16);
            int z = chunk.chunkZPos * 16 + world.rand.nextInt(16);
            int y = 1 + world.rand.nextInt(126);
            EntityTypeDefinition selected = select(world, definitions, counts, x, y, z);
            if (selected != null) {
                int added = spawnGroup(world, selected, counts, x, y, z);
                spawned += added;
            }
        }
        return spawned;
    }

    private static List<EntityTypeDefinition> available(World world, boolean hostile, boolean peaceful) {
        List<EntityTypeDefinition> result = new ArrayList<>();
        String dimension = DimensionIdentity.key(world).toString();
        for (EntityTypeDefinition definition : EntityTypeRegistry.definitions()) {
            EntitySpawnDefinition rule = definition.spawning;
            if (rule == null || (rule.category == EntitySpawnDefinition.Category.HOSTILE && !hostile)
                    || (rule.category == EntitySpawnDefinition.Category.PASSIVE && !peaceful)
                    || !DimensionSelector.matches(rule.dimensions, dimension)) {
                continue;
            }
            result.add(definition);
        }
        return result;
    }

    private static Map<AssetKey, Integer> counts(World world) {
        Map<AssetKey, Integer> result = new HashMap<>();
        for (Object value : world.loadedEntityList) {
            if (value instanceof TypedEntity && !(value instanceof LuaEntityPart)) {
                EntityInstanceState state = ((TypedEntity) value).entityState();
                EntityTypeDefinition definition = state.definition();
                if (definition != null) {
                    result.put(definition.key, result.getOrDefault(definition.key, 0) + 1);
                }
            }
        }
        return result;
    }

    private static Set<ChunkCoordIntPair> eligibleChunks(World world) {
        Set<ChunkCoordIntPair> result = new HashSet<>();
        for (Object value : world.playerEntities) {
            EntityPlayer player = (EntityPlayer) value;
            int centerX = MathHelper.floor_double(player.posX / 16);
            int centerZ = MathHelper.floor_double(player.posZ / 16);
            for (int dx = -CHUNK_RADIUS; dx <= CHUNK_RADIUS; dx++) {
                for (int dz = -CHUNK_RADIUS; dz <= CHUNK_RADIUS; dz++) {
                    result.add(new ChunkCoordIntPair(centerX + dx, centerZ + dz));
                }
            }
        }
        return result;
    }

    private static EntityTypeDefinition select(World world, List<EntityTypeDefinition> definitions,
            Map<AssetKey, Integer> counts, int x, int y, int z) {
        List<EntityTypeDefinition> matching = new ArrayList<>();
        double totalWeight = 0;
        List<Double> weights = new ArrayList<Double>();
        boolean hasSpatial = false;
        for (EntityTypeDefinition definition : definitions) {
            EntitySpawnDefinition rule = definition.spawning;
            if (counts.getOrDefault(definition.key, 0) < rule.cap && matches(world, rule, x, y, z)) {
                double weight = rule.weight * spatialWeight(world, rule, x, z);
                if (weight > 0) {
                    hasSpatial |= rule.spatial != null;
                    matching.add(definition);
                    weights.add(weight);
                    totalWeight += weight;
                }
            }
        }
        if (matching.isEmpty()) {
            return null;
        }
        double choice = hasSpatial ? world.rand.nextDouble() * totalWeight : world.rand.nextInt((int) totalWeight);
        for (int index = 0; index < matching.size(); index++) {
            EntityTypeDefinition definition = matching.get(index);
            choice -= weights.get(index);
            if (choice < 0) {
                return definition;
            }
        }
        throw new AssertionError("Weighted spawn selection exhausted");
    }

    private static double spatialWeight(World world, EntitySpawnDefinition rule, int x, int z) {
        return rule.spatial == null ? 1 : rule.spatial.weight(WorldGenerationRuntime.fieldContext(world), x, z);
    }

    static boolean matches(World world, EntitySpawnDefinition rule, int x, int y, int z) {
        if (y < rule.minY || y > rule.maxY || !world.checkChunksExist(x - 1, 0, z - 1, x + 1, 127, z + 1)) {
            return false;
        }
        if (spatialWeight(world, rule, x, z) <= 0) {
            return false;
        }
        int light = world.getBlockLightValue(x, y, z);
        if (light < rule.minLight || light > rule.maxLight) {
            return false;
        }
        if (!rule.biomes.isEmpty() && !WorldGenerationRuntime.biomeMatches(world, x, z, rule.biomes)) {
            return false;
        }
        int substrate = world.getBlockId(x, y - 1, z);
        if (!rule.substrates.isEmpty() && !rule.substrates.contains(substrate)) {
            return false;
        }
        return world.isBlockNormalCube(x, y - 1, z) && !world.isBlockNormalCube(x, y, z)
                && !world.getBlockMaterial(x, y, z).getIsLiquid()
                && !world.isBlockNormalCube(x, y + 1, z);
    }

    private static int spawnGroup(World world, EntityTypeDefinition definition, Map<AssetKey, Integer> counts,
            int originX, int originY, int originZ) {
        EntitySpawnDefinition rule = definition.spawning;
        int wanted = rule.groupMin + world.rand.nextInt(rule.groupMax - rule.groupMin + 1);
        int spawned = 0;
        ChunkCoordinates worldSpawn = world.getSpawnPoint();
        for (int member = 0; member < wanted && counts.getOrDefault(definition.key, 0) < rule.cap; member++) {
            for (int attempt = 0; attempt < GROUP_POSITION_ATTEMPTS; attempt++) {
                int x = originX + world.rand.nextInt(6) - world.rand.nextInt(6);
                int y = originY;
                int z = originZ + world.rand.nextInt(6) - world.rand.nextInt(6);
                double px = x + 0.5;
                double pz = z + 0.5;
                EntityPlayer player = world.getClosestPlayer(px, y, pz, 24);
                double spawnDistance = square(px - worldSpawn.x) + square(y - worldSpawn.y) + square(pz - worldSpawn.z);
                if (player != null || spawnDistance < MIN_PLAYER_DISTANCE_SQUARED || !matches(world, rule, x, y, z)) {
                    continue;
                }
                EntitySpawner.Result result = EntitySpawner.spawn(world, definition.key, px, y, pz,
                        world.rand.nextFloat() * 360, 0, null, "natural");
                if (result.entity != null) {
                    spawned++;
                    counts.put(definition.key, counts.getOrDefault(definition.key, 0) + 1);
                    break;
                }
            }
        }
        return spawned;
    }

    private static double square(double value) {
        return value * value;
    }
}
