package betamoon.registry;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.src.BiomeGenBase;
import net.minecraft.src.MathHelper;
import net.minecraft.src.World;
import net.minecraft.src.WorldGenerator;

/**
 * Registry for custom ore-like world generation entries driven by Lua.
 */
public final class WorldGenRegistry {
    private static final List<OreGenEntry> ORE_ENTRIES = new ArrayList<OreGenEntry>();

    /**
     * Immutable ore generation configuration.
     */
    private static final class OreGenEntry {
        private final int blockId;
        private final int veinsPerChunk;
        private final int veinSize;
        private final int minY;
        private final int maxY;
        private final boolean nether;
        private final int targetBlockId;
        private final BiomeGenBase[] allowedBiomes;

        private OreGenEntry(int blockId, int veinsPerChunk, int veinSize, int minY, int maxY, boolean nether,
            int targetBlockId, BiomeGenBase[] allowedBiomes) {
            this.blockId = blockId;
            this.veinsPerChunk = veinsPerChunk;
            this.veinSize = veinSize;
            this.minY = minY;
            this.maxY = maxY;
            this.nether = nether;
            this.targetBlockId = targetBlockId;
            this.allowedBiomes = allowedBiomes;
        }
    }

    /**
     * Utility class, not meant to be instantiated.
     */
    private WorldGenRegistry() {
    }

    /**
     * Registers a new ore generation entry.
     *
     * @param blockId block id to place
     * @param veinsPerChunk number of veins per chunk
     * @param veinSize number of blocks per vein
     * @param minY minimum Y level for generation (inclusive)
     * @param maxY maximum Y level for generation (inclusive)
     * @param nether true for nether generation, false for overworld
     * @param targetBlockId block id to replace when generating
     * @param allowedBiomes optional whitelist of biomes for generation
     */
    public static void addOreGen(int blockId, int veinsPerChunk, int veinSize, int minY, int maxY, boolean nether,
        int targetBlockId, BiomeGenBase[] allowedBiomes) {
        ORE_ENTRIES.add(new OreGenEntry(blockId, veinsPerChunk, veinSize, minY, maxY, nether, targetBlockId,
            allowedBiomes));
    }

    /**
     * Runs overworld generation for registered entries.
     *
     * @param world world instance
     * @param random chunk-level random
     * @param chunkX chunk origin x (block coordinates)
     * @param chunkZ chunk origin z (block coordinates)
     */
    public static void generateSurface(World world, Random random, int chunkX, int chunkZ) {
        generate(world, random, chunkX, chunkZ, false);
    }

    /**
     * Runs nether generation for registered entries.
     *
     * @param world world instance
     * @param random chunk-level random
     * @param chunkX chunk origin x (block coordinates)
     * @param chunkZ chunk origin z (block coordinates)
     */
    public static void generateNether(World world, Random random, int chunkX, int chunkZ) {
        generate(world, random, chunkX, chunkZ, true);
    }

    /**
     * Executes generation for either overworld or nether entries.
     *
     * @param world world instance
     * @param random chunk-level random
     * @param chunkX chunk origin x (block coordinates)
     * @param chunkZ chunk origin z (block coordinates)
     * @param nether true for nether entries, false for overworld
     */
    private static void generate(World world, Random random, int chunkX, int chunkZ, boolean nether) {
        for (int i = 0; i < ORE_ENTRIES.size(); i++) {
            OreGenEntry entry = ORE_ENTRIES.get(i);
            if (entry.nether != nether) {
                continue;
            }
            for (int vein = 0; vein < entry.veinsPerChunk; vein++) {
                int x = chunkX + random.nextInt(16);
                int y = entry.minY + random.nextInt(entry.maxY - entry.minY + 1);
                int z = chunkZ + random.nextInt(16);
                if (!isBiomeAllowed(world, x, z, entry.allowedBiomes)) {
                    continue;
                }
                WorldGenerator generator = new ReplaceableMinableGenerator(entry.blockId, entry.veinSize,
                    entry.targetBlockId);
                generator.generate(world, random, x, y, z);
            }
        }
    }

    /**
     * Checks whether the position is in one of the allowed biomes.
     *
     * @param world world instance
     * @param x block x coordinate
     * @param z block z coordinate
     * @param allowedBiomes biome whitelist, or empty for no restriction
     * @return true if generation is allowed
     */
    private static boolean isBiomeAllowed(World world, int x, int z, BiomeGenBase[] allowedBiomes) {
        if (allowedBiomes == null || allowedBiomes.length == 0) {
            return true;
        }
        BiomeGenBase biome = world.getWorldChunkManager().getBiomeGenAt(x, z);
        if (biome == null) {
            return false;
        }
        for (int i = 0; i < allowedBiomes.length; i++) {
            if (biome == allowedBiomes[i]) {
                return true;
            }
        }
        return false;
    }

    /**
     * Resolves biome names to biome instances.
     *
     * @param names biome names or field names from {@link BiomeGenBase}
     * @return resolved biomes, or empty when none match
     */
    public static BiomeGenBase[] resolveBiomes(String[] names) {
        if (names == null || names.length == 0) {
            return new BiomeGenBase[0];
        }
        List list = new ArrayList();
        for (int i = 0; i < names.length; i++) {
            BiomeGenBase biome = resolveBiomeByName(names[i]);
            if (biome != null) {
                list.add(biome);
            }
        }
        if (list.isEmpty()) {
            return new BiomeGenBase[0];
        }
        BiomeGenBase[] result = new BiomeGenBase[list.size()];
        for (int i = 0; i < list.size(); i++) {
            result[i] = (BiomeGenBase) list.get(i);
        }
        return result;
    }

    /**
     * Resolves a single biome by name or static field name.
     *
     * @param name biome name or field name to match
     * @return matching biome or null if none matched
     */
    private static BiomeGenBase resolveBiomeByName(String name) {
        if (name == null) {
            return null;
        }
        String target = name.trim().toLowerCase();
        if (target.length() == 0) {
            return null;
        }
        try {
            java.lang.reflect.Field[] fields = BiomeGenBase.class.getDeclaredFields();
            for (int i = 0; i < fields.length; i++) {
                java.lang.reflect.Field field = fields[i];
                if (field.getType() != BiomeGenBase.class) {
                    continue;
                }
                Object value = field.get(null);
                if (value instanceof BiomeGenBase) {
                    BiomeGenBase biome = (BiomeGenBase) value;
                    if (biome.biomeName != null && biome.biomeName.toLowerCase().equals(target)) {
                        return biome;
                    }
                    if (field.getName().toLowerCase().equals(target)) {
                        return biome;
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    /**
     * World generator that replaces only a specific target block.
     */
    private static final class ReplaceableMinableGenerator extends WorldGenerator {
        private final int minableBlockId;
        private final int numberOfBlocks;
        private final int targetBlockId;

        private ReplaceableMinableGenerator(int blockId, int numberOfBlocks, int targetBlockId) {
            this.minableBlockId = blockId;
            this.numberOfBlocks = numberOfBlocks;
            this.targetBlockId = targetBlockId;
        }

        public boolean generate(World world, Random random, int x, int y, int z) {
            /*
             * Build a vein path using a randomized angle and length.
             * x1/x2 and z1/z2 define the path endpoints around the chunk center.
             * y1/y2 give a small vertical offset so the vein can slope up or down.
             */
            float angle = random.nextFloat() * (float) Math.PI;
            double x1 = (double) ((float) (x + 8) + MathHelper.sin(angle) * (float) this.numberOfBlocks / 8.0F);
            double x2 = (double) ((float) (x + 8) - MathHelper.sin(angle) * (float) this.numberOfBlocks / 8.0F);
            double z1 = (double) ((float) (z + 8) + MathHelper.cos(angle) * (float) this.numberOfBlocks / 8.0F);
            double z2 = (double) ((float) (z + 8) - MathHelper.cos(angle) * (float) this.numberOfBlocks / 8.0F);
            double y1 = (double) (y + random.nextInt(3) + 2);
            double y2 = (double) (y + random.nextInt(3) + 2);

            for (int i = 0; i <= this.numberOfBlocks; ++i) {
                /*
                 * Interpolate along the vein path and compute a local ellipsoid size.
                 * xPos/yPos/zPos are the current center point along the path.
                 * hSize/vSize define the ellipsoid radius in horizontal/vertical axes.
                 */
                double xPos = x1 + (x2 - x1) * (double) i / (double) this.numberOfBlocks;
                double yPos = y1 + (y2 - y1) * (double) i / (double) this.numberOfBlocks;
                double zPos = z1 + (z2 - z1) * (double) i / (double) this.numberOfBlocks;
                double size = random.nextDouble() * (double) this.numberOfBlocks / 16.0D;
                double hSize = (double) (MathHelper.sin((float) i * (float) Math.PI / (float) this.numberOfBlocks)
                    + 1.0F) * size + 1.0D;
                double vSize = (double) (MathHelper.sin((float) i * (float) Math.PI / (float) this.numberOfBlocks)
                    + 1.0F) * size + 1.0D;
                /*
                 * Compute bounding box around the ellipsoid so we can scan only
                 * candidate blocks instead of the entire chunk.
                 */
                int minX = MathHelper.floor_double(xPos - hSize / 2.0D);
                int minY = MathHelper.floor_double(yPos - vSize / 2.0D);
                int minZ = MathHelper.floor_double(zPos - hSize / 2.0D);
                int maxX = MathHelper.floor_double(xPos + hSize / 2.0D);
                int maxY = MathHelper.floor_double(yPos + vSize / 2.0D);
                int maxZ = MathHelper.floor_double(zPos + hSize / 2.0D);

                for (int xi = minX; xi <= maxX; ++xi) {
                    // Normalize distance from ellipsoid center on X.
                    double dx = ((double) xi + 0.5D - xPos) / (hSize / 2.0D);
                    if (dx * dx < 1.0D) {
                        for (int yi = minY; yi <= maxY; ++yi) {
                            // Normalize distance from ellipsoid center on Y.
                            double dy = ((double) yi + 0.5D - yPos) / (vSize / 2.0D);
                            if (dx * dx + dy * dy < 1.0D) {
                                for (int zi = minZ; zi <= maxZ; ++zi) {
                                    // Normalize distance from ellipsoid center on Z.
                                    double dz = ((double) zi + 0.5D - zPos) / (hSize / 2.0D);
                                    /*
                                     * Check ellipsoid volume and replace only matching target blocks.
                                     * This keeps ore generation constrained to the requested base block.
                                     */
                                    if (dx * dx + dy * dy + dz * dz < 1.0D
                                        && world.getBlockId(xi, yi, zi) == this.targetBlockId) {
                                        world.setBlock(xi, yi, zi, this.minableBlockId);
                                    }
                                }
                            }
                        }
                    }
                }
            }

            return true;
        }
    }
}
