package betamoon.entity;

import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import net.minecraft.src.Block;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

import static betamoon.luaapi.utils.LuaDeclarationValues.error;
import static betamoon.luaapi.utils.LuaDeclarationValues.fields;
import static betamoon.luaapi.utils.LuaDeclarationValues.id;
import static betamoon.luaapi.utils.LuaDeclarationValues.integer;
import static betamoon.luaapi.utils.LuaDeclarationValues.string;

/** Bounded natural-spawn rule for one living entity type. */
public final class EntitySpawnDefinition {
    public enum Category {
        HOSTILE,
        PASSIVE
    }

    public final Category category;
    public final int weight;
    public final int groupMin;
    public final int groupMax;
    public final int cap;
    public final int minLight;
    public final int maxLight;
    public final int minY;
    public final int maxY;
    public final Set<Integer> dimensions;
    public final Set<String> biomes;
    public final Set<Integer> substrates;
    public final boolean nativeDespawn;

    public EntitySpawnDefinition(LuaValue value, boolean inheritedDespawn) {
        fields(value, "entity.spawning", "category", "weight", "group", "cap", "light", "height",
                "dimensions", "biomes", "substrates", "despawn");
        category = category(value.get("category"));
        weight = optionalInteger(value.get("weight"), "entity.spawning.weight", 1, 10000, 10);
        int[] group = range(value.get("group"), "entity.spawning.group", 1, 16, 1, 1);
        groupMin = group[0];
        groupMax = group[1];
        cap = optionalInteger(value.get("cap"), "entity.spawning.cap", 1, 1024, 32);
        int[] light = range(value.get("light"), "entity.spawning.light", 0, 15, 0, 15);
        minLight = light[0];
        maxLight = light[1];
        int[] height = range(value.get("height"), "entity.spawning.height", 1, 126, 1, 126);
        minY = height[0];
        maxY = height[1];
        dimensions = integers(value.get("dimensions"), "entity.spawning.dimensions", -128, 127);
        biomes = names(value.get("biomes"), "entity.spawning.biomes");
        substrates = blocks(value.get("substrates"), "entity.spawning.substrates");
        nativeDespawn = despawn(value.get("despawn"), inheritedDespawn);
    }

    private static Category category(LuaValue value) {
        String parsed = value.isnil() ? "passive" : string(value, "entity.spawning.category");
        if ("hostile".equals(parsed)) {
            return Category.HOSTILE;
        }
        if ("passive".equals(parsed)) {
            return Category.PASSIVE;
        }
        throw error("entity.spawning.category", "expected 'hostile' or 'passive'");
    }

    private static boolean despawn(LuaValue value, boolean inherited) {
        if (value.isnil()) {
            return inherited;
        }
        String parsed = string(value, "entity.spawning.despawn");
        if ("native".equals(parsed)) {
            return true;
        }
        if ("persistent".equals(parsed)) {
            return false;
        }
        throw error("entity.spawning.despawn", "expected 'native' or 'persistent'");
    }

    private static int optionalInteger(LuaValue value, String path, int min, int max, int fallback) {
        return value.isnil() ? fallback : integer(value, path, min, max);
    }

    private static int[] range(LuaValue value, String path, int min, int max, int fallbackMin, int fallbackMax) {
        if (value.isnil()) {
            return new int[]{fallbackMin, fallbackMax};
        }
        LuaTable table = value.checktable();
        fields(table, path, "min", "max");
        int lower = optionalInteger(table.get("min"), path + ".min", min, max, fallbackMin);
        int upper = optionalInteger(table.get("max"), path + ".max", min, max, fallbackMax);
        if (lower > upper) {
            throw error(path, "min must not exceed max");
        }
        return new int[]{lower, upper};
    }

    private static Set<Integer> integers(LuaValue value, String path, int min, int max) {
        if (value.isnil()) {
            return Collections.emptySet();
        }
        LuaTable table = value.checktable();
        if (table.length() > 64) {
            throw error(path, "expected at most 64 entries");
        }
        Set<Integer> result = new HashSet<>();
        for (int index = 1; index <= table.length(); index++) {
            if (!result.add(integer(table.get(index), path + "[" + index + "]", min, max))) {
                throw error(path + "[" + index + "]", "duplicate value");
            }
        }
        if (result.isEmpty() || table.keys().length != table.length()) {
            throw error(path, "expected a non-empty array");
        }
        return Collections.unmodifiableSet(result);
    }

    private static Set<String> names(LuaValue value, String path) {
        if (value.isnil()) {
            return Collections.emptySet();
        }
        LuaTable table = value.checktable();
        if (table.length() > 64) {
            throw error(path, "expected at most 64 entries");
        }
        Set<String> result = new HashSet<>();
        for (int index = 1; index <= table.length(); index++) {
            String name = string(table.get(index), path + "[" + index + "]").trim();
            if (name.isEmpty() || name.length() > 64) {
                throw error(path + "[" + index + "]", "expected 1..64 characters");
            }
            if (!result.add(name.toLowerCase(Locale.ROOT))) {
                throw error(path + "[" + index + "]", "duplicate biome");
            }
        }
        if (result.isEmpty() || table.keys().length != table.length()) {
            throw error(path, "expected a non-empty array");
        }
        return Collections.unmodifiableSet(result);
    }

    private static Set<Integer> blocks(LuaValue value, String path) {
        if (value.isnil()) {
            return Collections.emptySet();
        }
        LuaTable table = value.checktable();
        if (table.length() > 64) {
            throw error(path, "expected at most 64 entries");
        }
        Set<Integer> result = new HashSet<>();
        for (int index = 1; index <= table.length(); index++) {
            int block = id(table.get(index), path + "[" + index + "]");
            if (block <= 0 || block >= Block.blocksList.length || Block.blocksList[block] == null) {
                throw error(path + "[" + index + "]", "must reference a registered block");
            }
            if (!result.add(block)) {
                throw error(path + "[" + index + "]", "duplicate block");
            }
        }
        if (result.isEmpty() || table.keys().length != table.length()) {
            throw error(path, "expected a non-empty array");
        }
        return Collections.unmodifiableSet(result);
    }
}
