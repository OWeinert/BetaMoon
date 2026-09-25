package betamoon.worldgen;

import betamoon.BetaMoonCommon;
import betamoon.luamodloader.LuaScriptRegistry;
import betamoon.minecraft.MinecraftBuiltins;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import net.minecraft.src.BiomeGenBase;
import net.minecraft.src.Block;
import net.minecraft.src.MathHelper;
import net.minecraft.src.World;
import net.minecraft.src.WorldGenerator;
import org.luaj.vm2.LuaError;

/** Publishes and executes immutable Lua world-generation snapshots. */
public final class WorldGenRegistry {
    private static final String POPULATION_STAGE = "after_vanilla_population";
    private static final ThreadLocal<PublicationBatch> CURRENT_BATCH = new ThreadLocal<PublicationBatch>();
    private static volatile Snapshot active = Snapshot.empty();

    private static final class OreGenEntry {
        private final WorldGenKey key;
        private final WorldGenKey featureKey;
        private final int blockId;
        private final int veinsPerChunk;
        private final int veinSize;
        private final int minY;
        private final int maxY;
        private final GenerationDimension dimension;
        private final Integer targetBlockId;
        private final BiomeGenBase[] allowedBiomes;
        private final String resourceOwner;
        private final String owner;
        private final String sourceLocation;
        private final long salt;

        private OreGenEntry(WorldGenKey key, int blockId, int veinsPerChunk, int veinSize, int minY, int maxY,
                GenerationDimension dimension, Integer targetBlockId, BiomeGenBase[] allowedBiomes,
                String resourceOwner, String owner, String sourceLocation, long salt) {
            this.key = key;
            this.featureKey = WorldGenKey.parse(key.getNamespace() + ":feature/" + untypedPath(key),
                    WorldGenKind.FEATURE);
            this.blockId = blockId;
            this.veinsPerChunk = veinsPerChunk;
            this.veinSize = veinSize;
            this.minY = minY;
            this.maxY = maxY;
            this.dimension = dimension;
            this.targetBlockId = targetBlockId;
            this.allowedBiomes = allowedBiomes == null ? new BiomeGenBase[0] : allowedBiomes.clone();
            this.resourceOwner = resourceOwner;
            this.owner = owner;
            this.sourceLocation = sourceLocation;
            this.salt = salt;
        }

        private static String untypedPath(WorldGenKey key) {
            String prefix = WorldGenKind.PLACEMENT.getPath() + "/";
            return key.getPath().startsWith(prefix) ? key.getPath().substring(prefix.length()) : key.getPath();
        }
    }

    private static final class Snapshot {
        private final List<OreGenEntry> ores;
        private final Map<WorldGenKey, OreGenEntry> byKey;

        private Snapshot(List<OreGenEntry> entries) {
            List<OreGenEntry> sorted = new ArrayList<OreGenEntry>(entries);
            Collections.sort(sorted, new Comparator<OreGenEntry>() {
                @Override
                public int compare(OreGenEntry left, OreGenEntry right) {
                    return left.key.compareTo(right.key);
                }
            });
            ores = Collections.unmodifiableList(sorted);
            Map<WorldGenKey, OreGenEntry> keyed = new LinkedHashMap<WorldGenKey, OreGenEntry>();
            for (OreGenEntry entry : sorted) {
                keyed.put(entry.key, entry);
            }
            byKey = Collections.unmodifiableMap(keyed);
        }

        private static Snapshot empty() {
            return new Snapshot(Collections.<OreGenEntry>emptyList());
        }
    }

    /** Immutable object-free generator description for diagnostics. */
    public static final class Description {
        public final String key;
        public final String featureKey;
        public final String owner;
        public final String sourceLocation;
        public final String stage;
        public final int blockId;
        public final int veinsPerChunk;
        public final int veinSize;
        public final int minY;
        public final int maxY;
        public final String dimension;
        public final Integer targetBlockId;
        public final List<String> biomes;
        public final long salt;

        private Description(OreGenEntry entry) {
            key = entry.key.toString();
            featureKey = entry.featureKey.toString();
            owner = entry.owner;
            sourceLocation = entry.sourceLocation;
            stage = POPULATION_STAGE;
            blockId = entry.blockId;
            veinsPerChunk = entry.veinsPerChunk;
            veinSize = entry.veinSize;
            minY = entry.minY;
            maxY = entry.maxY;
            dimension = entry.dimension.getLuaName();
            targetBlockId = entry.targetBlockId;
            List<String> names = new ArrayList<String>();
            for (BiomeGenBase biome : entry.allowedBiomes) {
                names.add(biome == null ? "unknown" : biome.biomeName);
            }
            biomes = Collections.unmodifiableList(names);
            salt = entry.salt;
        }
    }

    /** Owner-local transaction used while one script initializes. */
    public static final class PublicationBatch implements AutoCloseable {
        private final String resourceOwner;
        private final String owner;
        private final List<OreGenEntry> ores = new ArrayList<OreGenEntry>();
        private int declarationIndex;
        private boolean published;
        private boolean closed;

        private PublicationBatch(String resourceOwner, String owner) {
            this.resourceOwner = resourceOwner;
            this.owner = owner;
        }

        public void publish() {
            ensureOpen();
            publishBatch(this);
            published = true;
        }

        private void add(String key, int blockId, int veinsPerChunk, int veinSize, int minY, int maxY,
                GenerationDimension dimension, Integer targetBlockId, BiomeGenBase[] allowedBiomes, long salt) {
            ensureOpen();
            int index = declarationIndex++;
            WorldGenKey typedKey = key == null || key.trim().isEmpty()
                    ? WorldGenKey.privateKey(WorldGenKind.PLACEMENT, resourceOwner, index)
                    : parseKey(key, WorldGenKind.PLACEMENT, "OreGen.key");
            ores.add(createOreEntry(typedKey, blockId, veinsPerChunk, veinSize, minY, maxY, dimension, targetBlockId,
                    allowedBiomes, resourceOwner, owner, resourceOwner + ":worldgen[" + (index + 1) + "]", salt));
        }

        private void ensureOpen() {
            if (closed || published) {
                throw new IllegalStateException("World-generation publication is already completed");
            }
            if (CURRENT_BATCH.get() != this) {
                throw new IllegalStateException("World-generation publication owner is not active");
            }
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            if (CURRENT_BATCH.get() == this) {
                CURRENT_BATCH.remove();
            }
            closed = true;
        }
    }

    private WorldGenRegistry() {
    }

    public static PublicationBatch beginPublication(String resourceOwner, String owner) {
        if (CURRENT_BATCH.get() != null) {
            throw new IllegalStateException("A world-generation publication is already active");
        }
        PublicationBatch batch = new PublicationBatch(requiredOwner(resourceOwner), requiredOwner(owner));
        CURRENT_BATCH.set(batch);
        return batch;
    }

    /** Removes all definitions, primarily for complete shutdown and tests. */
    public static synchronized void clear() {
        active = Snapshot.empty();
    }

    /** Removes definitions whose script package no longer exists after a load pass. */
    public static synchronized void retainOwners(Set<String> resourceOwners) {
        List<OreGenEntry> retained = new ArrayList<OreGenEntry>();
        for (OreGenEntry entry : active.ores) {
            if (resourceOwners.contains(entry.resourceOwner)) {
                retained.add(entry);
            }
        }
        active = new Snapshot(retained);
    }

    public static void addOreGen(int blockId, int veinsPerChunk, int veinSize, int minY, int maxY,
            GenerationDimension dimension, Integer targetBlockId, BiomeGenBase[] allowedBiomes) {
        addOreGen(null, blockId, veinsPerChunk, veinSize, minY, maxY, dimension, targetBlockId, allowedBiomes, 0L);
    }

    public static void addOreGen(String key, int blockId, int veinsPerChunk, int veinSize, int minY, int maxY,
            GenerationDimension dimension, Integer targetBlockId, BiomeGenBase[] allowedBiomes, long salt) {
        PublicationBatch batch = CURRENT_BATCH.get();
        if (batch != null) {
            batch.add(key, blockId, veinsPerChunk, veinSize, minY, maxY, dimension, targetBlockId, allowedBiomes, salt);
            return;
        }

        String resourceOwner = requiredOwner(LuaScriptRegistry.getCurrentScriptFile());
        String owner = LuaScriptRegistry.getCurrentScriptIdentity();
        int index = countOwnerEntries(resourceOwner);
        WorldGenKey typedKey = key == null || key.trim().isEmpty()
                ? WorldGenKey.privateKey(WorldGenKind.PLACEMENT, resourceOwner, index)
                : parseKey(key, WorldGenKind.PLACEMENT, "OreGen.key");
        OreGenEntry entry = createOreEntry(typedKey, blockId, veinsPerChunk, veinSize, minY, maxY, dimension,
                targetBlockId, allowedBiomes, resourceOwner, owner, resourceOwner + ":worldgen[" + (index + 1) + "]",
                salt);
        publishImmediate(entry);
    }

    public static List<Description> snapshot() {
        List<Description> result = new ArrayList<Description>();
        for (OreGenEntry entry : active.ores) {
            result.add(new Description(entry));
        }
        return Collections.unmodifiableList(result);
    }

    public static void generateSurface(World world, Random ignored, int chunkX, int chunkZ) {
        generate(world, chunkX, chunkZ, false);
    }

    public static void generateNether(World world, Random ignored, int chunkX, int chunkZ) {
        generate(world, chunkX, chunkZ, true);
    }

    private static void generate(World world, int chunkX, int chunkZ, boolean nether) {
        Snapshot snapshot = active;
        String dimensionKey = nether ? "minecraft:nether" : "minecraft:overworld";
        int logicalChunkX = Math.floorDiv(chunkX, 16);
        int logicalChunkZ = Math.floorDiv(chunkZ, 16);
        for (OreGenEntry entry : snapshot.ores) {
            if (!entry.dimension.includes(nether)) {
                continue;
            }
            long seed = SeedMixer.generationSeed(world.getRandomSeed(), dimensionKey, POPULATION_STAGE, logicalChunkX,
                    logicalChunkZ, entry.key, entry.salt);
            Random random = new Random(seed);
            try {
                generateOre(world, random, chunkX, chunkZ, nether, entry);
            } catch (Throwable error) {
                BetaMoonCommon.LOGGER.warning("Worldgen placement " + entry.key + " failed in chunk " + logicalChunkX
                        + "," + logicalChunkZ + ": " + error.getMessage());
            }
        }
    }

    private static void generateOre(World world, Random random, int chunkX, int chunkZ, boolean nether,
            OreGenEntry entry) {
        for (int vein = 0; vein < entry.veinsPerChunk; vein++) {
            int x = chunkX + random.nextInt(16);
            int y = entry.minY + random.nextInt(entry.maxY - entry.minY + 1);
            int z = chunkZ + random.nextInt(16);
            if (!isBiomeAllowed(world, x, z, entry.allowedBiomes)) {
                continue;
            }
            int targetBlockId = entry.targetBlockId == null
                    ? nether ? Block.netherrack.blockID : Block.stone.blockID
                    : entry.targetBlockId.intValue();
            WorldGenerator generator = new ReplaceableMinableGenerator(entry.blockId, entry.veinSize, targetBlockId);
            generator.generate(world, random, x, y, z);
        }
    }

    private static synchronized void publishBatch(PublicationBatch batch) {
        validateBatchKeys(batch.ores);
        List<OreGenEntry> next = new ArrayList<OreGenEntry>();
        for (OreGenEntry current : active.ores) {
            if (!current.resourceOwner.equals(batch.resourceOwner)) {
                next.add(current);
            }
        }
        ensureNoForeignCollisions(next, batch.ores, batch.resourceOwner);
        next.addAll(batch.ores);
        active = new Snapshot(next);
    }

    private static synchronized void publishImmediate(OreGenEntry entry) {
        List<OreGenEntry> next = new ArrayList<OreGenEntry>(active.ores);
        ensureNoForeignCollisions(next, Collections.singletonList(entry), entry.resourceOwner);
        if (active.byKey.containsKey(entry.key)) {
            throw new LuaError("OreGen.key: duplicate world-generation key: " + entry.key);
        }
        next.add(entry);
        active = new Snapshot(next);
    }

    private static void validateBatchKeys(List<OreGenEntry> entries) {
        Set<WorldGenKey> keys = new HashSet<WorldGenKey>();
        for (OreGenEntry entry : entries) {
            if (!keys.add(entry.key)) {
                throw new LuaError("OreGen.key: duplicate world-generation key: " + entry.key);
            }
        }
    }

    private static void ensureNoForeignCollisions(List<OreGenEntry> current, List<OreGenEntry> additions,
            String resourceOwner) {
        Map<WorldGenKey, OreGenEntry> occupied = new LinkedHashMap<WorldGenKey, OreGenEntry>();
        for (OreGenEntry entry : current) {
            occupied.put(entry.key, entry);
        }
        for (OreGenEntry entry : additions) {
            OreGenEntry conflict = occupied.get(entry.key);
            if (conflict != null && !conflict.resourceOwner.equals(resourceOwner)) {
                throw new LuaError("World-generation key " + entry.key + " belongs to " + conflict.owner);
            }
        }
    }

    private static OreGenEntry createOreEntry(WorldGenKey key, int blockId, int veinsPerChunk, int veinSize,
            int minY, int maxY, GenerationDimension dimension, Integer targetBlockId, BiomeGenBase[] allowedBiomes,
            String resourceOwner, String owner, String sourceLocation, long salt) {
        if (blockId <= 0 || blockId >= Block.blocksList.length || Block.blocksList[blockId] == null) {
            throw new LuaError("OreGen.block: unknown block id: " + blockId);
        }
        if (veinsPerChunk < 0 || veinsPerChunk > WorldGenLimits.MAX_ATTEMPTS_PER_CHUNK) {
            throw new LuaError("OreGen.veinsPerChunk: expected 0.." + WorldGenLimits.MAX_ATTEMPTS_PER_CHUNK);
        }
        if (veinSize < 1 || veinSize > WorldGenLimits.MAX_ORE_VEIN_SIZE) {
            throw new LuaError("OreGen.veinSize: expected 1.." + WorldGenLimits.MAX_ORE_VEIN_SIZE);
        }
        if (minY < WorldGenLimits.MIN_HEIGHT || maxY > WorldGenLimits.MAX_HEIGHT || minY > maxY) {
            throw new LuaError("OreGen.height: expected 0 <= min <= max <= 127");
        }
        if (dimension == null) {
            throw new LuaError("OreGen.dimension: value is required");
        }
        if (targetBlockId != null && (targetBlockId.intValue() <= 0
                || targetBlockId.intValue() >= Block.blocksList.length
                || Block.blocksList[targetBlockId.intValue()] == null)) {
            throw new LuaError("OreGen.replace: unknown block id: " + targetBlockId);
        }
        int biomeCount = allowedBiomes == null ? 0 : allowedBiomes.length;
        if (biomeCount > WorldGenLimits.MAX_BIOME_FILTERS) {
            throw new LuaError("OreGen.biomes: at most " + WorldGenLimits.MAX_BIOME_FILTERS + " biomes are allowed");
        }
        return new OreGenEntry(key, blockId, veinsPerChunk, veinSize, minY, maxY, dimension, targetBlockId,
                allowedBiomes, resourceOwner, owner, sourceLocation, salt);
    }

    private static boolean isBiomeAllowed(World world, int x, int z, BiomeGenBase[] allowedBiomes) {
        if (allowedBiomes.length == 0) {
            return true;
        }
        BiomeGenBase biome = world.getWorldChunkManager().getBiomeGenAt(x, z);
        if (biome == null) {
            return false;
        }
        for (BiomeGenBase allowed : allowedBiomes) {
            if (biome == allowed) {
                return true;
            }
        }
        return false;
    }

    public static BiomeGenBase[] resolveBiomes(String[] names) {
        if (names == null || names.length == 0) {
            return new BiomeGenBase[0];
        }
        if (names.length > WorldGenLimits.MAX_BIOME_FILTERS) {
            throw new LuaError("Biome filter has more than " + WorldGenLimits.MAX_BIOME_FILTERS + " entries");
        }
        List<BiomeGenBase> list = new ArrayList<BiomeGenBase>();
        for (String name : names) {
            BiomeGenBase biome = MinecraftBuiltins.resolveBiome(name);
            if (biome == null) {
                throw new LuaError("Biome: unknown biome: " + name);
            }
            list.add(biome);
        }
        return list.toArray(new BiomeGenBase[list.size()]);
    }

    private static WorldGenKey parseKey(String value, WorldGenKind kind, String field) {
        try {
            return WorldGenKey.parse(value.trim(), kind);
        } catch (IllegalArgumentException error) {
            throw new LuaError(field + ": " + error.getMessage());
        }
    }

    private static int countOwnerEntries(String resourceOwner) {
        int count = 0;
        for (OreGenEntry entry : active.ores) {
            if (entry.resourceOwner.equals(resourceOwner)) {
                count++;
            }
        }
        return count;
    }

    private static String requiredOwner(String owner) {
        if (owner == null || owner.trim().isEmpty()) {
            throw new IllegalStateException("World-generation declarations require an active script owner");
        }
        return owner;
    }

    /** Ore generator that replaces only one configured base block. */
    private static final class ReplaceableMinableGenerator extends WorldGenerator {
        private final int minableBlockId;
        private final int numberOfBlocks;
        private final int targetBlockId;

        private ReplaceableMinableGenerator(int blockId, int numberOfBlocks, int targetBlockId) {
            minableBlockId = blockId;
            this.numberOfBlocks = numberOfBlocks;
            this.targetBlockId = targetBlockId;
        }

        @Override
        public boolean generate(World world, Random random, int x, int y, int z) {
            float angle = random.nextFloat() * (float) Math.PI;
            double x1 = x + 8 + MathHelper.sin(angle) * numberOfBlocks / 8.0F;
            double x2 = x + 8 - MathHelper.sin(angle) * numberOfBlocks / 8.0F;
            double z1 = z + 8 + MathHelper.cos(angle) * numberOfBlocks / 8.0F;
            double z2 = z + 8 - MathHelper.cos(angle) * numberOfBlocks / 8.0F;
            double y1 = y + random.nextInt(3) + 2;
            double y2 = y + random.nextInt(3) + 2;

            for (int index = 0; index <= numberOfBlocks; index++) {
                double xPosition = x1 + (x2 - x1) * index / numberOfBlocks;
                double yPosition = y1 + (y2 - y1) * index / numberOfBlocks;
                double zPosition = z1 + (z2 - z1) * index / numberOfBlocks;
                double size = random.nextDouble() * numberOfBlocks / 16.0D;
                double horizontalSize = (MathHelper.sin((float) index * (float) Math.PI / numberOfBlocks) + 1.0F)
                        * size + 1.0D;
                double verticalSize = (MathHelper.sin((float) index * (float) Math.PI / numberOfBlocks) + 1.0F)
                        * size + 1.0D;
                int minX = MathHelper.floor_double(xPosition - horizontalSize / 2.0D);
                int minY = Math.max(WorldGenLimits.MIN_HEIGHT,
                        MathHelper.floor_double(yPosition - verticalSize / 2.0D));
                int minZ = MathHelper.floor_double(zPosition - horizontalSize / 2.0D);
                int maxX = MathHelper.floor_double(xPosition + horizontalSize / 2.0D);
                int maxY = Math.min(WorldGenLimits.MAX_HEIGHT,
                        MathHelper.floor_double(yPosition + verticalSize / 2.0D));
                int maxZ = MathHelper.floor_double(zPosition + horizontalSize / 2.0D);

                for (int blockX = minX; blockX <= maxX; blockX++) {
                    double dx = (blockX + 0.5D - xPosition) / (horizontalSize / 2.0D);
                    if (dx * dx >= 1.0D) {
                        continue;
                    }
                    for (int blockY = minY; blockY <= maxY; blockY++) {
                        double dy = (blockY + 0.5D - yPosition) / (verticalSize / 2.0D);
                        if (dx * dx + dy * dy >= 1.0D) {
                            continue;
                        }
                        for (int blockZ = minZ; blockZ <= maxZ; blockZ++) {
                            double dz = (blockZ + 0.5D - zPosition) / (horizontalSize / 2.0D);
                            if (dx * dx + dy * dy + dz * dz < 1.0D
                                    && world.getBlockId(blockX, blockY, blockZ) == targetBlockId) {
                                world.setBlock(blockX, blockY, blockZ, minableBlockId);
                            }
                        }
                    }
                }
            }
            return true;
        }
    }
}
