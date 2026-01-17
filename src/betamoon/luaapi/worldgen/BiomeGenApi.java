package betamoon.luaapi.worldgen;

import betamoon.luaapi.LuaApiUtils;
import betamoon.worldgen.WorldGenRegistry;
import betamoon.wrappers.BiomeGenWrapper;
import java.util.Map;
import net.minecraft.src.BiomeGenBase;
import net.minecraft.src.Block;
import net.minecraft.src.EntityList;
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
            int base = (args.narg() >= 1 && args.arg(1).istable()) ? 2 : 1;
            String vanillaName = args.checkjstring(base);
            BiomeGenBase source = resolveBiome(vanillaName);
            if (source == null) {
                throw new LuaError("Biome: unknown biome: " + vanillaName);
            }
            String customName = args.checkjstring(base + 1);
            return new BiomeGeneratorHandle(handle, source, customName);
        }
    }

    private static final class BiomeGeneratorHandle extends LuaTable {
        private final WorldGenApi.WorldGenHandle worldGenHandle;
        private final BiomeGenWrapper biome;
        private double minTemperature = 0.0;
        private double maxTemperature = 1.0;
        private double minHumidity = 0.0;
        private double maxHumidity = 1.0;

        private BiomeGeneratorHandle(WorldGenApi.WorldGenHandle worldGenHandle, String name) {
            this.worldGenHandle = worldGenHandle;
            this.biome = new BiomeGenWrapper(name);
            // Default to vanilla grass/dirt surfaces for new custom biomes.
            this.biome.applyDefaultSurface();
            initBindings();
        }

        private BiomeGeneratorHandle(WorldGenApi.WorldGenHandle worldGenHandle, BiomeGenBase source, String name) {
            this.worldGenHandle = worldGenHandle;
            this.biome = new BiomeGenWrapper(source.biomeName);
            // Copy vanilla settings so users can tweak a known baseline.
            this.biome.applyDefaultsFrom(source);
            // Set custom name
            this.biome.applyName(name);
            initBindings();
        }

        private void initBindings() {
            set("setColor", new SetColor(this));
            set("setFoliageColor", new SetFoliageColor(this));
            set("setTopBlock", new SetTopBlock(this));
            set("setFillerBlock", new SetFillerBlock(this));
            set("setTemperatureRange", new SetTemperatureRange(this));
            set("setHumidityRange", new SetHumidityRange(this));        
            set("setTreeGenerator", new SetTreeGenerator(this));
            set("setBigTreeChance", new SetBigTreeChance(this));
            set("clearSpawns", new ClearSpawns(this));
            set("addSpawn", new AddSpawn(this));
            set("enableSnow", new SetEnableSnow(this));
            set("enableRain", new SetEnableRain(this));
            set("disableRain", new SetDisableRain(this));
            set("finishBiomeGen", new FinishBiomeGen(this));
        }
    }

    private static final class SetColor extends VarArgFunction {
        private final BiomeGeneratorHandle handle;

        private SetColor(BiomeGeneratorHandle handle) {
            this.handle = handle;
        }

        public Varargs invoke(Varargs args) {
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
            int blockId = resolveBlockId(getBlockArg(args));
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
            int blockId = resolveBlockId(getBlockArg(args));
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
            handle.biome.applyRainEnabled(false);
            handle.biome.applySnowEnabled(true);
            return handle;
        }
    }

    private static final class SetEnableRain extends VarArgFunction {
        private final BiomeGeneratorHandle handle;

        private SetEnableRain(BiomeGeneratorHandle handle) {
            this.handle = handle;
        }

        public Varargs invoke(Varargs args) {
            handle.biome.applySnowEnabled(false);
            handle.biome.applyRainEnabled(true);
            return handle;
        }
    }

    private static final class SetDisableRain extends VarArgFunction {
        private final BiomeGeneratorHandle handle;

        private SetDisableRain(BiomeGeneratorHandle handle) {
            this.handle = handle;
        }

        public Varargs invoke(Varargs args) {
            handle.biome.applyRainEnabled(false);
            return handle;
        }
    }

    private static final class SetTreeGenerator extends VarArgFunction {
        private final BiomeGeneratorHandle handle;

        private SetTreeGenerator(BiomeGeneratorHandle handle) {
            this.handle = handle;
        }

        public Varargs invoke(Varargs args) {
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
            int chance = (int) LuaApiUtils.getNumberArg(args, 1);
            if (chance < 1) {
                throw new LuaError("Biome: big tree chance must be >= 1.");
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
            String type = LuaApiUtils.getStringArg(args, 1);
            LuaValue entityValue = LuaApiUtils.getVarArg(args, 2);
            int weight = (int) LuaApiUtils.getNumberArg(args, 3);
            if (weight < 1) {
                throw new LuaError("Biome: spawn weight must be >= 1.");
            }
            Class entityClass = resolveEntityClass(entityValue);
            handle.biome.addSpawn(type, entityClass, weight);
            return handle;
        }
    }

    private static final class FinishBiomeGen extends VarArgFunction {
        private final BiomeGeneratorHandle handle;

        private FinishBiomeGen(BiomeGeneratorHandle handle) {
            this.handle = handle;
        }

        public Varargs invoke(Varargs args) {
            handle.worldGenHandle.addBiomeGenEntry(handle.biome, handle.minTemperature, handle.maxTemperature,
                handle.minHumidity, handle.maxHumidity);
            return handle.worldGenHandle;
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
            throw new LuaError("Biome: " + label + " range must be between 0 and 1.");
        }
        if (min > max) {
            throw new LuaError("Biome: " + label + " range min must be <= max.");
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
                throw new LuaError("Biome: unknown block id: " + id);
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
        throw new LuaError("Biome: block must be an id or block handle.");
    }

    /**
     * Reads the block argument, skipping the leading table when called via ':'.
     *
     * @param args Lua varargs passed to the API function
     * @return Lua value containing the block argument
     */
    private static LuaValue getBlockArg(Varargs args) {
        if (args.narg() >= 2 && args.arg(1).istable()) {
            return args.arg(2);
        }
        return args.arg(1);
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
                throw new LuaError("Biome: unknown entity name: " + name);
            }
            return clazz;
        }
        if (value.isnumber()) {
            int id = value.toint();
            Class clazz = getEntityClassFromId(id);
            if (clazz == null) {
                throw new LuaError("Biome: unknown entity id: " + id);
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
        throw new LuaError("Biome: entity must be a name or id.");
    }

    /**
     * Maps a vanilla entity name to its class using the internal registry.
     *
     * @param name entity name
     * @return entity class or null if not found
     */
    private static Class getEntityClassFromName(String name) {
        // EntityList maps are private; use reflection to stay compatible with Beta.
        Map map = getEntityMap("stringToClassMapping", "a");
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
        Map map = getEntityMap("IDtoClassMapping", "c");
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
    private static Map getEntityMap(String fieldName, String obfFieldName) {
        try {
            // Resolve the private EntityList map for id/name lookup.
            java.lang.reflect.Field field = resolveField(EntityList.class, fieldName, obfFieldName);
            return (Map) field.get(null);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static java.lang.reflect.Field resolveField(Class owner, String primary, String fallback)
        throws Exception {
        try {
            java.lang.reflect.Field field = owner.getDeclaredField(primary);
            field.setAccessible(true);
            return field;
        } catch (Exception ignored) {
        }
        java.lang.reflect.Field field = owner.getDeclaredField(fallback);
        field.setAccessible(true);
        return field;
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
}
