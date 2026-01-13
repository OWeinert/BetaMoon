package betamoon.wrappers;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.src.BiomeGenBase;
import net.minecraft.src.Block;
import net.minecraft.src.SpawnListEntry;
import net.minecraft.src.World;
import net.minecraft.src.WorldGenBigTree;
import net.minecraft.src.WorldGenTrees;
import net.minecraft.src.WorldGenerator;
import org.luaj.vm2.LuaError;

/**
 * Custom biome implementation with extra tuning hooks used by Lua.
 */
public final class BiomeGenWrapper extends BiomeGenBase {
    private static final String[] FIELD_ENABLE_SNOW = new String[] { "enableSnow", "v" };
    private static final String[] FIELD_ENABLE_RAIN = new String[] { "enableRain", "w" };
    private static final String[] FIELD_SPAWN_MONSTER = new String[] { "spawnableMonsterList", "s" };
    private static final String[] FIELD_SPAWN_CREATURE = new String[] { "spawnableCreatureList", "t" };
    private static final String[] FIELD_SPAWN_WATER = new String[] { "spawnableWaterCreatureList", "u" };
    private static final int TREE_MODE_DEFAULT = 0;
    private static final int TREE_MODE_BIG = 1;
    private static final int TREE_MODE_NORMAL = 2;
    private static final int TREE_MODE_NONE = 3;

    private int treeMode = TREE_MODE_DEFAULT;
    private int bigTreeChance = 10;

    public BiomeGenWrapper(String name) {
        setBiomeName(name);
    }

    public void applyName(String name) {
        setBiomeName(name);
    }

    public void applyColor(int color) {
        setColor(color);
    }

    public void applyFoliageColor(int color) {
        // Vanilla method name is obfuscated; this sets the foliage color used for the biome.
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
        applySnowEnabled(getBiomeFlag(source, FIELD_ENABLE_SNOW));
        applyRainEnabled(getBiomeFlag(source, FIELD_ENABLE_RAIN));
        copySpawnList(source, FIELD_SPAWN_MONSTER, spawnableMonsterList);
        copySpawnList(source, FIELD_SPAWN_CREATURE, spawnableCreatureList);
        copySpawnList(source, FIELD_SPAWN_WATER, spawnableWaterCreatureList);
    }

    public void applyTopBlock(int blockId) {
        topBlock = (byte) blockId;
    }

    public void applyFillerBlock(int blockId) {
        fillerBlock = (byte) blockId;
    }

    public void applySnowEnabled(boolean enabled) {
        setBiomeFlag(FIELD_ENABLE_SNOW, enabled);
    }

    public void applyRainEnabled(boolean enabled) {
        setBiomeFlag(FIELD_ENABLE_RAIN, enabled);
    }

    /**
     * Selects the tree generator mode for this biome.
     *
     * @param mode user-provided mode token
     */
    public void applyTreeMode(String mode) {
        String key = mode.trim().toLowerCase();
        // Map user strings to the internal tree mode constants.
        if (key.equals("default")) {
            treeMode = TREE_MODE_DEFAULT;
            return;
        }
        if (key.equals("big")) {
            treeMode = TREE_MODE_BIG;
            return;
        }
        if (key.equals("normal")) {
            treeMode = TREE_MODE_NORMAL;
            return;
        }
        if (key.equals("none")) {
            treeMode = TREE_MODE_NONE;
            return;
        }
        throw new LuaError("Biome: unknown tree generator mode: " + mode);
    }

    public void applyBigTreeChance(int chance) {
        bigTreeChance = chance;
    }

    /**
     * Clears one of the spawn lists (monsters/creatures/water).
     *
     * @param type spawn list selector
     */
    public void clearSpawns(String type) {
        List list = getSpawnList(type);
        list.clear();
    }

    /**
     * Adds a spawn entry to the selected list.
     *
     * @param type spawn list selector
     * @param entityClass entity type to spawn
     * @param weight spawn weight
     */
    public void addSpawn(String type, Class entityClass, int weight) {
        List list = getSpawnList(type);
        list.add(new SpawnListEntry(entityClass, weight));
    }

    /**
     * Resolves a spawn list based on a string token.
     *
     * @param type spawn list selector
     * @return mutable list for the selected category
     */
    private List getSpawnList(String type) {
        // Match common aliases so Lua scripts can be concise.
        String key = type.trim().toLowerCase();
        if (key.equals("monster") || key.equals("monsters")) {
            return spawnableMonsterList;
        }
        if (key.equals("creature") || key.equals("creatures") || key.equals("animal")
            || key.equals("animals")) {
            return spawnableCreatureList;
        }
        if (key.equals("water") || key.equals("watercreature") || key.equals("watercreatures")) {
            return spawnableWaterCreatureList;
        }
        throw new LuaError("Biome: unknown spawn list type: " + type);
    }

    public WorldGenerator getRandomWorldGenForTrees(Random random) {
        // Mirror vanilla behavior while allowing explicit overrides.
        // Explicit modes bypass random selection.
        if (treeMode == TREE_MODE_BIG) {
            return new WorldGenBigTree();
        }
        if (treeMode == TREE_MODE_NORMAL) {
            return new WorldGenTrees();
        }
        if (treeMode == TREE_MODE_NONE) {
            return NoopWorldGenerator.INSTANCE;
        }
        // Default mode uses a weighted chance for big trees.
        if (random.nextInt(bigTreeChance) == 0) {
            return new WorldGenBigTree();
        }
        return new WorldGenTrees();
    }

    /**
     * Sets a private boolean flag on the vanilla biome class.
     *
     * @param fieldName private field to modify
     * @param value boolean to set
     */
    private void setBiomeFlag(String[] fieldNames, boolean value) {
        try {
            // Vanilla flags are private, so reflection is required here.
            java.lang.reflect.Field field = resolveField(BiomeGenBase.class, fieldNames);
            field.setAccessible(true);
            field.setBoolean(this, value);
        } catch (Exception e) {
            throw new LuaError("Biome: unable to set biome flag '" + fieldNames[0] + "': " + e.getMessage());
        }
    }

    /**
     * Reads a private boolean flag from a vanilla biome.
     *
     * @param source biome instance to read
     * @param fieldName private field to access
     * @return flag value
     */
    private boolean getBiomeFlag(BiomeGenBase source, String[] fieldNames) {
        try {
            // Private flags require reflection for read access.
            java.lang.reflect.Field field = resolveField(BiomeGenBase.class, fieldNames);
            field.setAccessible(true);
            return field.getBoolean(source);
        } catch (Exception e) {
            throw new LuaError("Biome: unable to read biome flag '" + fieldNames[0] + "': " + e.getMessage());
        }
    }

    /**
     * Copies spawn list entries from a vanilla biome into this biome.
     *
     * @param source biome to copy from
     * @param fieldName private list field name
     * @param target target list to populate
     */
    private void copySpawnList(BiomeGenBase source, String[] fieldNames, List target) {
        // Clone entries so Lua scripts don't mutate vanilla lists.
        List list = getSpawnListField(source, fieldNames);
        target.clear();
        for (int i = 0; i < list.size(); i++) {
            SpawnListEntry entry = (SpawnListEntry) list.get(i);
            target.add(new SpawnListEntry(entry.entityClass, entry.spawnRarityRate));
        }
    }

    /**
     * Reads a spawn list field from a vanilla biome via reflection.
     *
     * @param source biome to read from
     * @param fieldName private list field name
     * @return list instance or empty list when missing
     */
    private List getSpawnListField(BiomeGenBase source, String[] fieldNames) {
        try {
            // Spawn lists are protected; use reflection for consistent access.
            java.lang.reflect.Field field = resolveField(BiomeGenBase.class, fieldNames);
            field.setAccessible(true);
            List list = (List) field.get(source);
            if (list == null) {
                return new ArrayList();
            }
            return list;
        } catch (Exception e) {
            throw new LuaError("Biome: unable to read biome spawn list '" + fieldNames[0] + "': " + e.getMessage());
        }
    }

    private static java.lang.reflect.Field resolveField(Class owner, String[] fieldNames) throws Exception {
        Exception last = null;
        for (int i = 0; i < fieldNames.length; i++) {
            try {
                java.lang.reflect.Field field = owner.getDeclaredField(fieldNames[i]);
                field.setAccessible(true);
                return field;
            } catch (Exception e) {
                last = e;
            }
        }
        if (last != null) {
            throw last;
        }
        throw new NoSuchFieldException("No matching field.");
    }

    /**
     * An empty WorldGenerator used if TREE_MODE_NONE is selected as the biome's tree generation mode.
     */
    private static final class NoopWorldGenerator extends WorldGenerator {
        private static final NoopWorldGenerator INSTANCE = new NoopWorldGenerator();

        private NoopWorldGenerator() {
        }

        public boolean generate(World world, Random random, int x, int y, int z) {
            return false;
        }
    }
}
