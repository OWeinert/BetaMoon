package betamoon.entity;

import java.util.LinkedHashMap;
import java.util.Map;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

/** Per-instance transient values for AI state and route plans; never written to NBT. */
public final class EntityMemoryStore {
    private static final int MAX_FIELDS = 64;
    private static final int MAX_ENTRIES = 256;
    private static final int MAX_DEPTH = 4;
    private final Map<String, LuaValue> values = new LinkedHashMap<>();

    public LuaValue get(String name) {
        validateName(name);
        LuaValue value = values.get(name);
        return value == null ? LuaValue.NIL : copy(value, 0, new int[]{0});
    }

    public void set(String name, LuaValue value) {
        validateName(name);
        if (value.isnil()) {
            values.remove(name);
            return;
        }
        if (!values.containsKey(name) && values.size() >= MAX_FIELDS) {
            throw new LuaError("entity.memory allows at most " + MAX_FIELDS + " fields");
        }
        values.put(name, copy(value, 0, new int[]{0}));
    }

    private static void validateName(String name) {
        if (!name.matches("[a-z][a-z0-9_]{0,63}")) {
            throw new LuaError("entity.memory names must be lowercase identifiers of at most 64 characters");
        }
    }

    private static LuaValue copy(LuaValue value, int depth, int[] count) {
        if (value.isboolean()) {
            return LuaValue.valueOf(value.toboolean());
        }
        if (value.isnumber()) {
            double number = value.todouble();
            if (!Double.isFinite(number)) {
                throw new LuaError("entity.memory numbers must be finite");
            }
            return LuaValue.valueOf(number);
        }
        if (value.isstring()) {
            String string = value.tojstring();
            if (string.length() > 1024) {
                throw new LuaError("entity.memory strings are limited to 1024 characters");
            }
            return LuaValue.valueOf(string);
        }
        if (!value.istable() || depth >= MAX_DEPTH) {
            throw new LuaError("entity.memory accepts booleans, numbers, strings and shallow tables only");
        }
        LuaTable table = new LuaTable();
        for (LuaValue key : value.checktable().keys()) {
            if (++count[0] > MAX_ENTRIES) {
                throw new LuaError("entity.memory tables are limited to " + MAX_ENTRIES + " entries");
            }
            if (!key.isstring() && !(key.isnumber() && key.todouble() >= 1
                    && key.todouble() == Math.floor(key.todouble()) && key.todouble() <= MAX_ENTRIES)) {
                throw new LuaError("entity.memory table keys must be strings or positive integer indices");
            }
            if (key.isstring() && key.tojstring().length() > 64) {
                throw new LuaError("entity.memory table keys are limited to 64 characters");
            }
            table.set(key, copy(value.get(key), depth + 1, count));
        }
        return table;
    }
}
