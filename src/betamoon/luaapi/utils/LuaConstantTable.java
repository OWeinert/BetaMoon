package betamoon.luaapi.utils;

import java.util.Map;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

/** Enumerable Lua constants with strict lookup and no writable access path. */
public final class LuaConstantTable extends LuaTable {
    private final String path;

    public LuaConstantTable(String path, Map<String, LuaValue> values) {
        this.path = path;
        for (Map.Entry<String, LuaValue> entry : values.entrySet()) {
            super.rawset(LuaValue.valueOf(entry.getKey()), entry.getValue());
        }
    }

    @Override
    public LuaValue get(int key) {
        return requireKnown(LuaValue.valueOf(key), super.rawget(key));
    }

    @Override
    public LuaValue get(LuaValue key) {
        return requireKnown(key, super.rawget(key));
    }

    @Override
    public LuaValue rawget(int key) {
        return requireKnown(LuaValue.valueOf(key), super.rawget(key));
    }

    @Override
    public LuaValue rawget(LuaValue key) {
        return requireKnown(key, super.rawget(key));
    }

    @Override
    public void set(int key, LuaValue value) {
        readOnly();
    }

    @Override
    public void set(LuaValue key, LuaValue value) {
        readOnly();
    }

    @Override
    public void rawset(int key, LuaValue value) {
        readOnly();
    }

    @Override
    public void rawset(LuaValue key, LuaValue value) {
        readOnly();
    }

    @Override
    public LuaValue remove(int position) {
        return readOnly();
    }

    @Override
    public void insert(int position, LuaValue value) {
        readOnly();
    }

    @Override
    public void sort(LuaValue comparator) {
        readOnly();
    }

    @Override
    public LuaValue setmetatable(LuaValue metatable) {
        return readOnly();
    }

    private LuaValue requireKnown(LuaValue key, LuaValue value) {
        if (value.isnil()) {
            throw new LuaError(path + " has no constant named '" + key.tojstring() + "'.");
        }
        return value;
    }

    private LuaValue readOnly() {
        throw new LuaError(path + " is read-only.");
    }
}
