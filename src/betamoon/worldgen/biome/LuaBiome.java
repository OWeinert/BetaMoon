package betamoon.worldgen.biome;

import java.util.List;
import java.util.Random;
import betamoon.worldgen.BiomeSpawnGroup;
import betamoon.worldgen.BiomeTreeMode;
import net.minecraft.src.BiomeGenBase;
import net.minecraft.src.Block;
import net.minecraft.src.SpawnListEntry;
import net.minecraft.src.World;
import net.minecraft.src.WorldGenBigTree;
import net.minecraft.src.WorldGenTrees;
import net.minecraft.src.WorldGenerator;

/**
 * Custom biome implementation with extra tuning hooks used by Lua.
 */
public final class LuaBiome extends BiomeGenBase {
    private BiomeTreeMode treeMode = BiomeTreeMode.DEFAULT;
    private int bigTreeChance = 10;

    public LuaBiome(String name) {
        setBiomeName(name);
    }

    public void applyName(String name) {
        setBiomeName(name);
    }

    public void applyColor(int color) {
        setColor(color);
    }

    public void applyFoliageColor(int color) {
        // Vanilla method name is obfuscated; this sets the foliage color used for the
        // biome.
        func_4124_a(color);
    }

    /**
     * Applies the default grass/dirt surface used by most vanilla biomes.
     */
    public void applyDefaultSurface() {
        topBlock = (byte) Block.grass.blockID;
        fillerBlock = (byte) Block.dirt.blockID;
    }

    /**
     * Copies the vanilla biome fields we expose to Lua.
     */
    public void applyDefaultsFrom(BiomeGenBase source) {
        // Copy the fields the Lua API exposes; any custom setters can override later.
        biomeName = source.biomeName;
        color = source.color;
        field_6502_q = source.field_6502_q;
        topBlock = source.topBlock;
        fillerBlock = source.fillerBlock;
        // Copy weather flags and spawn lists to match the vanilla baseline.
        applySnowEnabled(BiomeFields.SNOW.readFlag(source));
        applyRainEnabled(BiomeFields.RAIN.readFlag(source));
        copySpawnList(source, BiomeFields.MONSTERS, getSpawnList(BiomeSpawnGroup.MONSTER));
        copySpawnList(source, BiomeFields.CREATURES, getSpawnList(BiomeSpawnGroup.CREATURE));
        copySpawnList(source, BiomeFields.WATER_CREATURES, getSpawnList(BiomeSpawnGroup.WATER));
    }

    public void applyTopBlock(int blockId) {
        topBlock = (byte) blockId;
    }

    public void applyFillerBlock(int blockId) {
        fillerBlock = (byte) blockId;
    }

    public void applySnowEnabled(boolean enabled) {
        BiomeFields.SNOW.writeFlag(this, enabled);
    }

    public void applyRainEnabled(boolean enabled) {
        BiomeFields.RAIN.writeFlag(this, enabled);
    }

    /**
     * Selects the tree generator mode for this biome.
     *
     * @param mode
     *            user-provided mode token
     */
    public void applyTreeMode(BiomeTreeMode mode) {
        treeMode = mode;
    }

    public void applyBigTreeChance(int chance) {
        bigTreeChance = chance;
    }

    public BiomeTreeMode treeMode() {
        return treeMode;
    }

    public int bigTreeChance() {
        return bigTreeChance;
    }

    public boolean snowEnabled() {
        return BiomeFields.SNOW.readFlag(this);
    }

    public boolean rainEnabled() {
        return BiomeFields.RAIN.readFlag(this);
    }

    /**
     * Clears one of the spawn lists (monsters/creatures/water).
     *
     * @param type
     *            spawn list selector
     */
    public void clearSpawns(BiomeSpawnGroup type) {
        List<SpawnListEntry> list = getSpawnList(type);
        list.clear();
    }

    /**
     * Adds a spawn entry to the selected list.
     *
     * @param type
     *            spawn list selector
     * @param entityClass
     *            entity type to spawn
     * @param weight
     *            spawn weight
     */
    public void addSpawn(BiomeSpawnGroup type, Class<?> entityClass, int weight) {
        List<SpawnListEntry> list = getSpawnList(type);
        list.add(new SpawnListEntry(entityClass, weight));
    }

    /**
     * Resolves a spawn list based on a string token.
     *
     * @param type
     *            spawn list selector
     * @return mutable list for the selected category
     */
    @SuppressWarnings("unchecked") // Minecraft exposes its spawn lists without generic signatures.
    private List<SpawnListEntry> getSpawnList(BiomeSpawnGroup type) {
        switch (type) {
            case MONSTER:
                return spawnableMonsterList;
            case CREATURE:
                return spawnableCreatureList;
            case WATER:
                return spawnableWaterCreatureList;
            default:
                throw new IllegalArgumentException("Unsupported spawn group: " + type);
        }
    }

    @Override
    public WorldGenerator getRandomWorldGenForTrees(Random random) {
        // Mirror vanilla definition while allowing explicit overrides.
        // Explicit modes bypass random selection.
        if (treeMode == BiomeTreeMode.BIG) {
            return new WorldGenBigTree();
        }
        if (treeMode == BiomeTreeMode.NORMAL) {
            return new WorldGenTrees();
        }
        if (treeMode == BiomeTreeMode.NONE) {
            return NoopWorldGenerator.INSTANCE;
        }
        // Default mode uses a weighted chance for big trees.
        if (random.nextInt(bigTreeChance) == 0) {
            return new WorldGenBigTree();
        }
        return new WorldGenTrees();
    }

    /**
     * Copies spawn list entries from a vanilla biome into this biome.
     *
     * @param source
     *            biome to copy from
     * @param field
     *            native spawn-list field
     * @param target
     *            target list to populate
     */
    private void copySpawnList(BiomeGenBase source, BiomeFields field, List<SpawnListEntry> target) {
        // Clone entries so Lua scripts don't mutate vanilla lists.
        List<?> list = field.readSpawns(source);
        target.clear();
        for (int i = 0; i < list.size(); i++) {
            SpawnListEntry entry = (SpawnListEntry) list.get(i);
            target.add(new SpawnListEntry(entry.entityClass, entry.spawnRarityRate));
        }
    }

    /**
     * An empty WorldGenerator used if TREE_MODE_NONE is selected as the biome's
     * tree generation mode.
     */
    private static final class NoopWorldGenerator extends WorldGenerator {
        private static final NoopWorldGenerator INSTANCE = new NoopWorldGenerator();

        private NoopWorldGenerator() {
        }

        @Override
        public boolean generate(World world, Random random, int x, int y, int z) {
            return false;
        }
    }
}
