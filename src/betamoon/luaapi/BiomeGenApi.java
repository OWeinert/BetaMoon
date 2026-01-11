package betamoon.luaapi;

import betamoon.worldgen.BiomeGenRegistry;
import betamoon.worldgen.WorldGenRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import net.minecraft.src.BiomeGenBase;
import net.minecraft.src.Block;
import net.minecraft.src.EntityList;
import net.minecraft.src.SpawnListEntry;
import net.minecraft.src.WorldGenBigTree;
import net.minecraft.src.WorldGenTrees;
import net.minecraft.src.WorldGenerator;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

/**
 * Lua bindings for creating and configuring custom biomes.
 *
 * <p>Custom biomes are built via a handle that mirrors vanilla biome settings
 * (surface blocks, weather flags, tree generators, and spawn lists) and then
 * registered into the biome lookup table.</p>
 */
final class BiomeGenApi {
    private BiomeGenApi() {
    }

    static VarArgFunction createBiomeGen(WorldGenApi.WorldGenHandle handle) {
        return new AddBiomeGen(handle);
    }

    static VarArgFunction createBiomeGenFromDefault(WorldGenApi.WorldGenHandle handle) {
        return new AddBiomeGenFromDefault(handle);
    }

    private static final class AddBiomeGen extends VarArgFunction {
        private final WorldGenApi.WorldGenHandle handle;

        private AddBiomeGen(WorldGenApi.WorldGenHandle handle) {
            this.handle = handle;
        }

        public Varargs invoke(Varargs args) {
            handle.ensureActive();
            int base = (args.narg() >= 1 && args.arg(1).istable()) ? 2 : 1;
            String name = args.checkjstring(base);
            return new BiomeGeneratorHandle(handle, name);
        }
    }

    private static final class AddBiomeGenFromDefault extends VarArgFunction {
        private final WorldGenApi.WorldGenHandle handle;

        private AddBiomeGenFromDefault(WorldGenApi.WorldGenHandle handle) {
            this.handle = handle;
        }

        public Varargs invoke(Varargs args) {
            handle.ensureActive();
            int base = (args.narg() >= 1 && args.arg(1).istable()) ? 2 : 1;
            String name = args.checkjstring(base);
            BiomeGenBase source = resolveBiome(name);
            if (source == null) {
                throw new LuaError("Unknown biome: " + name);
            }
            return new BiomeGeneratorHandle(handle, source);
        }
    }

    private static final class BiomeGeneratorHandle extends LuaTable {
        private final WorldGenApi.WorldGenHandle worldGenHandle;
        private final LuaBiomeGen biome;
        private double minTemperature = 0.0;
        private double maxTemperature = 1.0;
        private double minHumidity = 0.0;
        private double maxHumidity = 1.0;
        private boolean registered;

        private BiomeGeneratorHandle(WorldGenApi.WorldGenHandle worldGenHandle, String name) {
            this.worldGenHandle = worldGenHandle;
            this.biome = new LuaBiomeGen(name);
            // Default to vanilla grass/dirt surfaces for new custom biomes.
            this.biome.applyDefaultSurface();
            initBindings();
        }

        private BiomeGeneratorHandle(WorldGenApi.WorldGenHandle worldGenHandle, BiomeGenBase source) {
            this.worldGenHandle = worldGenHandle;
            this.biome = new LuaBiomeGen(source.biomeName);
            // Copy vanilla settings so users can tweak a known baseline.
            this.biome.applyDefaultsFrom(source);
            initBindings();
        }

        private void initBindings() {
            set("setName", new SetName(this));
            set("setColor", new SetColor(this));
            set("setFoliageColor", new SetFoliageColor(this));
            set("setTopBlock", new SetTopBlock(this));
            set("setFillerBlock", new SetFillerBlock(this));
            set("setTemperatureRange", new SetTemperatureRange(this));
            set("setHumidityRange", new SetHumidityRange(this));
            set("setSnowEnabled", new SetSnowEnabled(this));
            set("setRainEnabled", new SetRainEnabled(this));
            set("setTreeGenerator", new SetTreeGenerator(this));
            set("setBigTreeChance", new SetBigTreeChance(this));
            set("clearSpawns", new ClearSpawns(this));
            set("addSpawn", new AddSpawn(this));
            set("setEnableSnow", new SetEnableSnow(this));
            set("setDisableRain", new SetDisableRain(this));
            set("registerBiomeGenerator", new RegisterBiomeGenerator(this));
        }
    }

    private static final class SetName extends VarArgFunction {
        private final BiomeGeneratorHandle handle;

        private SetName(BiomeGeneratorHandle handle) {
            this.handle = handle;
        }

        public Varargs invoke(Varargs args) {
            ensureBiomeHandleActive(handle);
            String name = LuaApiUtils.getStringArg(args, 1);
            handle.biome.applyName(name);
            return handle;
        }
    }

    private static final class SetColor extends VarArgFunction {
        private final BiomeGeneratorHandle handle;

        private SetColor(BiomeGeneratorHandle handle) {
            this.handle = handle;
        }

        public Varargs invoke(Varargs args) {
            ensureBiomeHandleActive(handle);
            int color = (int) LuaApiUtils.getNumberArg(args, 1);
            handle.biome.applyColor(color);
            return handle;
        }
    }

    private static final class SetFoliageColor extends VarArgFunction {
        private final BiomeGeneratorHandle handle;

        private SetFoliageColor(BiomeGeneratorHandle handle) {
            this.handle = handle;
        }

        public Varargs invoke(Varargs args) {
            ensureBiomeHandleActive(handle);
            int color = (int) LuaApiUtils.getNumberArg(args, 1);
            handle.biome.applyFoliageColor(color);
            return handle;
        }
    }

    private static final class SetTopBlock extends VarArgFunction {
        private final BiomeGeneratorHandle handle;

        private SetTopBlock(BiomeGeneratorHandle handle) {
            this.handle = handle;
        }

        public Varargs invoke(Varargs args) {
            ensureBiomeHandleActive(handle);
            int blockId = resolveBlockId(args.arg(1));
            handle.biome.applyTopBlock(blockId);
            return handle;
        }
    }

    private static final class SetFillerBlock extends VarArgFunction {
        private final BiomeGeneratorHandle handle;

        private SetFillerBlock(BiomeGeneratorHandle handle) {
            this.handle = handle;
        }

        public Varargs invoke(Varargs args) {
            ensureBiomeHandleActive(handle);
            int blockId = resolveBlockId(args.arg(1));
            handle.biome.applyFillerBlock(blockId);
            return handle;
        }
    }

    private static final class SetTemperatureRange extends VarArgFunction {
        private final BiomeGeneratorHandle handle;

        private SetTemperatureRange(BiomeGeneratorHandle handle) {
            this.handle = handle;
        }

        public Varargs invoke(Varargs args) {
            ensureBiomeHandleActive(handle);
            double min = LuaApiUtils.getNumberArg(args, 1);
            double max = LuaApiUtils.getNumberArg(args, 2);
            validateRange("temperature", min, max);
            handle.minTemperature = min;
            handle.maxTemperature = max;
            return handle;
        }
    }

    private static final class SetHumidityRange extends VarArgFunction {
        private final BiomeGeneratorHandle handle;

        private SetHumidityRange(BiomeGeneratorHandle handle) {
            this.handle = handle;
        }

        public Varargs invoke(Varargs args) {
            ensureBiomeHandleActive(handle);
            double min = LuaApiUtils.getNumberArg(args, 1);
            double max = LuaApiUtils.getNumberArg(args, 2);
            validateRange("humidity", min, max);
            handle.minHumidity = min;
            handle.maxHumidity = max;
            return handle;
        }
    }

    private static final class SetEnableSnow extends VarArgFunction {
        private final BiomeGeneratorHandle handle;

        private SetEnableSnow(BiomeGeneratorHandle handle) {
            this.handle = handle;
        }

        public Varargs invoke(Varargs args) {
            ensureBiomeHandleActive(handle);
            handle.biome.applySnowEnabled(true);
            return handle;
        }
    }

    private static final class SetDisableRain extends VarArgFunction {
        private final BiomeGeneratorHandle handle;

        private SetDisableRain(BiomeGeneratorHandle handle) {
            this.handle = handle;
        }

        public Varargs invoke(Varargs args) {
            ensureBiomeHandleActive(handle);
            handle.biome.applyRainEnabled(false);
            return handle;
        }
    }

    private static final class SetSnowEnabled extends VarArgFunction {
        private final BiomeGeneratorHandle handle;

        private SetSnowEnabled(BiomeGeneratorHandle handle) {
            this.handle = handle;
        }

        public Varargs invoke(Varargs args) {
            ensureBiomeHandleActive(handle);
            boolean enabled = args.arg(1).toboolean();
            handle.biome.applySnowEnabled(enabled);
            return handle;
        }
    }

    private static final class SetRainEnabled extends VarArgFunction {
        private final BiomeGeneratorHandle handle;

        private SetRainEnabled(BiomeGeneratorHandle handle) {
            this.handle = handle;
        }

        public Varargs invoke(Varargs args) {
            ensureBiomeHandleActive(handle);
            boolean enabled = args.arg(1).toboolean();
            handle.biome.applyRainEnabled(enabled);
            return handle;
        }
    }

    private static final class SetTreeGenerator extends VarArgFunction {
        private final BiomeGeneratorHandle handle;

        private SetTreeGenerator(BiomeGeneratorHandle handle) {
            this.handle = handle;
        }

        public Varargs invoke(Varargs args) {
            ensureBiomeHandleActive(handle);
            String mode = LuaApiUtils.getStringArg(args, 1);
            handle.biome.applyTreeMode(mode);
            return handle;
        }
    }

    private static final class SetBigTreeChance extends VarArgFunction {
        private final BiomeGeneratorHandle handle;

        private SetBigTreeChance(BiomeGeneratorHandle handle) {
            this.handle = handle;
        }

        public Varargs invoke(Varargs args) {
            ensureBiomeHandleActive(handle);
            int chance = (int) LuaApiUtils.getNumberArg(args, 1);
            if (chance < 1) {
                throw new LuaError("Big tree chance must be >= 1.");
            }
            handle.biome.applyBigTreeChance(chance);
            return handle;
        }
    }

    private static final class ClearSpawns extends VarArgFunction {
        private final BiomeGeneratorHandle handle;

        private ClearSpawns(BiomeGeneratorHandle handle) {
            this.handle = handle;
        }

        public Varargs invoke(Varargs args) {
            ensureBiomeHandleActive(handle);
            String type = LuaApiUtils.getStringArg(args, 1);
            handle.biome.clearSpawns(type);
            return handle;
        }
    }

    private static final class AddSpawn extends VarArgFunction {
        private final BiomeGeneratorHandle handle;

        private AddSpawn(BiomeGeneratorHandle handle) {
            this.handle = handle;
        }

        public Varargs invoke(Varargs args) {
            ensureBiomeHandleActive(handle);
            String type = LuaApiUtils.getStringArg(args, 1);
            LuaValue entityValue = args.arg(2);
            int weight = (int) LuaApiUtils.getNumberArg(args, 3);
            if (weight < 1) {
                throw new LuaError("Spawn weight must be >= 1.");
            }
            Class entityClass = resolveEntityClass(entityValue);
            handle.biome.addSpawn(type, entityClass, weight);
            return handle;
        }
    }

    private static final class RegisterBiomeGenerator extends VarArgFunction {
        private final BiomeGeneratorHandle handle;

        private RegisterBiomeGenerator(BiomeGeneratorHandle handle) {
            this.handle = handle;
        }

        public Varargs invoke(Varargs args) {
            ensureBiomeHandleActive(handle);
            handle.registered = true;
            BiomeGenRegistry.registerBiomeGenerator(handle.biome, handle.minTemperature, handle.maxTemperature,
                handle.minHumidity, handle.maxHumidity);
            return handle.worldGenHandle;
        }
    }

    /**
     * Verifies the handle is still valid before applying mutations.
     *
     * @param handle biome handle to check
     */
    private static void ensureBiomeHandleActive(BiomeGeneratorHandle handle) {
        handle.worldGenHandle.ensureActive();
        if (handle.registered) {
            // Prevent modifying or re-registering a biome once it has been committed.
            throw new LuaError("Biome generator already registered.");
        }
    }

    /**
     * Validates a normalized 0..1 range and reports consistent Lua errors.
     *
     * @param label range name for error messages
     * @param min minimum value
     * @param max maximum value
     */
    private static void validateRange(String label, double min, double max) {
        // Clamp checks are explicit so Lua scripts get clear error messages.
        if (min < 0.0 || max < 0.0 || min > 1.0 || max > 1.0) {
            throw new LuaError("Biome " + label + " range must be between 0 and 1.");
        }
        if (min > max) {
            throw new LuaError("Biome " + label + " range min must be <= max.");
        }
    }

    /**
     * Resolves a block id from a number, table, or block handle.
     *
     * @param value Lua id or handle
     * @return numeric block id
     */
    private static int resolveBlockId(LuaValue value) {
        // Accept raw ids, { id = ... } tables, or block handles with getId().
        if (value.isnumber()) {
            int id = value.toint();
            if (id < 0 || id >= Block.blocksList.length || Block.blocksList[id] == null) {
                throw new LuaError("Unknown block id: " + id);
            }
            return id;
        }
        if (value.istable()) {
            LuaValue idValue = value.get("id");
            if (!idValue.isnil()) {
                return resolveBlockId(idValue);
            }
            LuaValue getter = value.get("getId");
            if (!getter.isnil()) {
                return resolveBlockId(getter.call(value));
            }
        }
        throw new LuaError("Block must be an id or block handle.");
    }

    /**
     * Resolves an entity class from a name, id, or table.
     *
     * @param value Lua id/name/handle-like table
     * @return entity class registered in {@link EntityList}
     */
    private static Class resolveEntityClass(LuaValue value) {
        // Accept entity name or numeric id, including via tables with name/id fields.
        if (value.isstring()) {
            String name = value.tojstring();
            Class clazz = getEntityClassFromName(name);
            if (clazz == null) {
                throw new LuaError("Unknown entity name: " + name);
            }
            return clazz;
        }
        if (value.isnumber()) {
            int id = value.toint();
            Class clazz = getEntityClassFromId(id);
            if (clazz == null) {
                throw new LuaError("Unknown entity id: " + id);
            }
            return clazz;
        }
        if (value.istable()) {
            // Support named fields so Lua callers can pass { name = "Zombie" } or { id = 54 }.
            LuaValue nameValue = value.get("name");
            if (!nameValue.isnil()) {
                return resolveEntityClass(nameValue);
            }
            LuaValue idValue = value.get("id");
            if (!idValue.isnil()) {
                return resolveEntityClass(idValue);
            }
        }
        throw new LuaError("Entity must be a name or id.");
    }

    /**
     * Maps a vanilla entity name to its class using the internal registry.
     *
     * @param name entity name
     * @return entity class or null if not found
     */
    private static Class getEntityClassFromName(String name) {
        // EntityList maps are private; use reflection to stay compatible with Beta.
        Map map = getEntityMap("stringToClassMapping");
        if (map == null) {
            return null;
        }
        return (Class) map.get(name);
    }

    /**
     * Maps a vanilla entity id to its class using the internal registry.
     *
     * @param id entity id
     * @return entity class or null if not found
     */
    private static Class getEntityClassFromId(int id) {
        Map map = getEntityMap("IDtoClassMapping");
        if (map == null) {
            return null;
        }
        return (Class) map.get(new Integer(id));
    }

    /**
     * Reads a private map field from {@link EntityList}.
     *
     * @param fieldName private field name
     * @return map instance or null when reflection fails
     */
    private static Map getEntityMap(String fieldName) {
        try {
            // Resolve the private EntityList map for id/name lookup.
            java.lang.reflect.Field field = EntityList.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            return (Map) field.get(null);
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * Resolves vanilla biome names via the shared world-gen registry helper.
     */
    /**
     * Resolves a vanilla biome from its name or alias.
     *
     * @param name biome name or alias
     * @return biome or null if not found
     */
    private static BiomeGenBase resolveBiome(String name) {
        // Reuse the registry's name resolution so vanilla aliases stay consistent.
        BiomeGenBase[] biomes = WorldGenRegistry.resolveBiomes(new String[] { name });
        if (biomes.length == 0) {
            return null;
        }
        return biomes[0];
    }

    /**
     * Custom biome implementation with extra tuning hooks used by Lua.
     */
    private static final class LuaBiomeGen extends BiomeGenBase {
        private static final String FIELD_ENABLE_SNOW = "enableSnow";
        private static final String FIELD_ENABLE_RAIN = "enableRain";
        private static final String FIELD_SPAWN_MONSTER = "spawnableMonsterList";
        private static final String FIELD_SPAWN_CREATURE = "spawnableCreatureList";
        private static final String FIELD_SPAWN_WATER = "spawnableWaterCreatureList";
        private static final int TREE_MODE_DEFAULT = 0;
        private static final int TREE_MODE_BIG = 1;
        private static final int TREE_MODE_NORMAL = 2;
        private static final int TREE_MODE_NONE = 3;

        private int treeMode = TREE_MODE_DEFAULT;
        private int bigTreeChance = 10;

        private LuaBiomeGen(String name) {
            setBiomeName(name);
        }

        private void applyName(String name) {
            setBiomeName(name);
        }

        private void applyColor(int color) {
            setColor(color);
        }

        private void applyFoliageColor(int color) {
            // Vanilla method name is obfuscated; this sets the foliage color used for the biome.
            func_4124_a(color);
        }

        /**
         * Applies the default grass/dirt surface used by most vanilla biomes.
         */
        private void applyDefaultSurface() {
            topBlock = (byte) Block.grass.blockID;
            fillerBlock = (byte) Block.dirt.blockID;
        }

        /**
         * Copies the vanilla biome fields we expose to Lua.
         */
        private void applyDefaultsFrom(BiomeGenBase source) {
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

        private void applyTopBlock(int blockId) {
            topBlock = (byte) blockId;
        }

        private void applyFillerBlock(int blockId) {
            fillerBlock = (byte) blockId;
        }

        private void applySnowEnabled(boolean enabled) {
            setBiomeFlag(FIELD_ENABLE_SNOW, enabled);
        }

        private void applyRainEnabled(boolean enabled) {
            setBiomeFlag(FIELD_ENABLE_RAIN, enabled);
        }

        /**
         * Selects the tree generator mode for this biome.
         *
         * @param mode user-provided mode token
         */
        private void applyTreeMode(String mode) {
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
            throw new LuaError("Unknown tree generator mode: " + mode);
        }

        private void applyBigTreeChance(int chance) {
            bigTreeChance = chance;
        }

        /**
         * Clears one of the spawn lists (monsters/creatures/water).
         *
         * @param type spawn list selector
         */
        private void clearSpawns(String type) {
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
        private void addSpawn(String type, Class entityClass, int weight) {
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
            throw new LuaError("Unknown spawn list type: " + type);
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
        private void setBiomeFlag(String fieldName, boolean value) {
            try {
                // Vanilla flags are private, so reflection is required here.
                java.lang.reflect.Field field = BiomeGenBase.class.getDeclaredField(fieldName);
                field.setAccessible(true);
                field.setBoolean(this, value);
            } catch (Exception e) {
                throw new LuaError("Unable to set biome flag '" + fieldName + "': " + e.getMessage());
            }
        }

        /**
         * Reads a private boolean flag from a vanilla biome.
         *
         * @param source biome instance to read
         * @param fieldName private field to access
         * @return flag value
         */
        private boolean getBiomeFlag(BiomeGenBase source, String fieldName) {
            try {
                // Private flags require reflection for read access.
                java.lang.reflect.Field field = BiomeGenBase.class.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field.getBoolean(source);
            } catch (Exception e) {
                throw new LuaError("Unable to read biome flag '" + fieldName + "': " + e.getMessage());
            }
        }

        /**
         * Copies spawn list entries from a vanilla biome into this biome.
         *
         * @param source biome to copy from
         * @param fieldName private list field name
         * @param target target list to populate
         */
        private void copySpawnList(BiomeGenBase source, String fieldName, List target) {
            // Clone entries so Lua scripts don't mutate vanilla lists.
            List list = getSpawnListField(source, fieldName);
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
        private List getSpawnListField(BiomeGenBase source, String fieldName) {
            try {
                // Spawn lists are protected; use reflection for consistent access.
                java.lang.reflect.Field field = BiomeGenBase.class.getDeclaredField(fieldName);
                field.setAccessible(true);
                List list = (List) field.get(source);
                if (list == null) {
                    return new ArrayList();
                }
                return list;
            } catch (Exception e) {
                throw new LuaError("Unable to read biome spawn list '" + fieldName + "': " + e.getMessage());
            }
        }
    }

    private static final class NoopWorldGenerator extends WorldGenerator {
        private static final NoopWorldGenerator INSTANCE = new NoopWorldGenerator();

        private NoopWorldGenerator() {
        }

        public boolean generate(net.minecraft.src.World world, Random random, int x, int y, int z) {
            return false;
        }
    }
}
