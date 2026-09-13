package betamoon.luaapi.world;

import betamoon.minecraft.MinecraftBuiltins;
import betamoon.worldgen.BiomeSpawnGroup;
import betamoon.worldgen.BiomeTreeMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.src.BiomeGenBase;
import net.minecraft.src.Block;
import net.minecraft.src.EntityLiving;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import static betamoon.luaapi.utils.LuaDeclarationValues.required;

/**
 * Fully read biome settings; runtime installation is performed by
 * BiomeRegistration.
 */
public final class BiomeDeclaration {
    public enum Weather {
        INHERIT, SNOW, RAIN, DRY
    }

    public final String name;
    public final BiomeGenBase basedOn;
    public final Integer color;
    public final Integer foliageColor;
    public final Integer topBlock;
    public final Integer fillerBlock;
    public final Range temperature;
    public final Range humidity;
    public final BiomeTreeMode treeMode;
    public final Integer bigTreeChance;
    public final Weather weather;
    public final Map<BiomeSpawnGroup, List<Spawn>> spawns;

    public BiomeDeclaration(LuaValue definition) {
        if (!definition.istable()) {
            throw new LuaError("worldgen.biomes:add expects a definition table.");
        }
        name = required(definition, "name").checkjstring();
        LuaValue source = definition.get("basedOn");
        basedOn = source.isnil() ? null : resolveBiome(source.checkjstring());
        if (!source.isnil() && basedOn == null) {
            throw new LuaError("Biome: unknown biome: " + source.checkjstring());
        }
        color = optionalInteger(definition.get("color"));
        foliageColor = optionalInteger(definition.get("foliageColor"));
        LuaValue surface = definition.get("surface");
        topBlock = surface.istable() && !surface.get("top").isnil() ? resolveBlockId(surface.get("top")) : null;
        fillerBlock = surface.istable() && !surface.get("filler").isnil()
                ? resolveBlockId(surface.get("filler"))
                : null;
        LuaValue range = definition.get("range");
        temperature = new Range(range.istable() ? range.get("temperature") : LuaValue.NIL, "temperature",
                "setTemperatureRange");
        humidity = new Range(range.istable() ? range.get("humidity") : LuaValue.NIL, "humidity", "setHumidityRange");
        LuaValue trees = definition.get("trees");
        treeMode = trees.istable() && !trees.get("type").isnil()
                ? BiomeTreeMode.parse(trees.get("type").checkjstring())
                : null;
        bigTreeChance = trees.istable() ? optionalInteger(trees.get("bigTreeChance")) : null;
        if (bigTreeChance != null && bigTreeChance < 1) {
            throw new LuaError("Biome: big tree chance must be >= 1.");
        }
        LuaValue climate = definition.get("weather");
        weather = !climate.istable()
                ? Weather.INHERIT
                : climate.get("snow").toboolean()
                        ? Weather.SNOW
                        : climate.get("rain").toboolean() ? Weather.RAIN : Weather.DRY;
        spawns = readSpawns(definition.get("spawns"));
    }

    private static Integer optionalInteger(LuaValue value) {
        return value.isnil() ? null : Integer.valueOf((int) value.checkdouble());
    }

    private static Map<BiomeSpawnGroup, List<Spawn>> readSpawns(LuaValue groups) {
        Map<BiomeSpawnGroup, List<Spawn>> result = new LinkedHashMap<>();
        if (groups.istable()) {
            LuaValue key = LuaValue.NIL;
            while (true) {
                Varargs group = groups.next(key);
                key = group.arg1();
                if (key.isnil()) {
                    break;
                }
                String type = key.checkjstring();
                BiomeSpawnGroup spawnGroup = BiomeSpawnGroup.parse(type);
                LuaValue entries = group.arg(2);
                if (!entries.istable()) {
                    throw new LuaError("Biome spawn group '" + type + "' must be a list.");
                }
                List<Spawn> spawns = new ArrayList<>();
                for (int i = 1; i <= entries.length(); i++) {
                    LuaValue entry = entries.get(i);
                    LuaValue entity = required(entry, "entity");
                    int weight = (int) required(entry, "weight").checkdouble();
                    if (weight < 1) {
                        throw new LuaError("Biome: spawn weight must be >= 1.");
                    }
                    spawns.add(new Spawn(resolveEntityClass(entity), weight));
                }
                result.put(spawnGroup, Collections.unmodifiableList(spawns));
            }
        }
        return Collections.unmodifiableMap(result);
    }

    public static final class Spawn {
        public final Class<?> entity;
        public final int weight;

        private Spawn(Class<?> entity, int weight) {
            this.entity = entity;
            this.weight = weight;
        }
    }

    public static final class Range {
        public final double min;
        public final double max;

        private Range(LuaValue value, String label, String method) {
            if (value.isnil()) {
                min = 0;
                max = 1;
                return;
            }
            if (!value.istable()) {
                throw new LuaError(method + " range must be a table.");
            }
            min = (value.get("min").isnil() ? value.get(1) : value.get("min")).checkdouble();
            max = (value.get("max").isnil() ? value.get(2) : value.get("max")).checkdouble();
            validateRange(label, min, max);
        }
    }

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
     * @param value
     *            Lua id or handle
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

    /** Resolves an entity name or id, including named table references. */
    private static Class<?> resolveEntityClass(LuaValue value) {
        // Accept entity name or numeric id, including via tables with name/id fields.
        if (value.isstring()) {
            String name = value.tojstring();
            Class<?> clazz = MinecraftBuiltins.resolveEntity(name);
            if (clazz == null) {
                throw new LuaError("Biome: unknown entity name: " + name);
            }
            return requireLivingEntity(clazz, name);
        }
        if (value.isnumber()) {
            int id = value.toint();
            Class<?> clazz = MinecraftBuiltins.resolveEntity(id);
            if (clazz == null) {
                throw new LuaError("Biome: unknown entity id: " + id);
            }
            return requireLivingEntity(clazz, Integer.toString(id));
        }
        if (value.istable()) {
            // Support named fields so Lua callers can pass { name = "Zombie" } or { id = 54
            // }.
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

    private static Class<?> requireLivingEntity(Class<?> entityClass, String value) {
        if (!EntityLiving.class.isAssignableFrom(entityClass)) {
            throw new LuaError("Biome: entity must be a living entity: " + value);
        }
        return entityClass;
    }

    /**
     * Resolves a vanilla biome from its name or alias.
     *
     * @param name
     *            biome name or alias
     * @return biome or null if not found
     */
    private static BiomeGenBase resolveBiome(String name) {
        return MinecraftBuiltins.resolveBiome(name);
    }
}
