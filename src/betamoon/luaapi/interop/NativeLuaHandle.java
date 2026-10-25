package betamoon.luaapi.interop;

import java.util.HashMap;
import java.util.Map;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;

/** Opaque Lua value without a LuaJ userdata payload or writable metatable. */
abstract class NativeLuaHandle extends LuaValue {
    protected final Map<String, LuaValue> properties = new HashMap<>();
    private final String label;

    NativeLuaHandle(String label) {
        this.label = label;
    }

    @Override
    public int type() {
        return TUSERDATA;
    }

    @Override
    public String typename() {
        return "userdata";
    }

    @Override
    public String tojstring() {
        return label;
    }

    @Override
    public LuaValue tostring() {
        return LuaValue.valueOf(label);
    }

    @Override
    public LuaValue get(String key) {
        return get(LuaValue.valueOf(key));
    }

    @Override
    public LuaValue get(int key) {
        return get(LuaValue.valueOf(key));
    }

    @Override
    public void set(int key, LuaValue value) {
        set(LuaValue.valueOf(key), value);
    }

    @Override
    public LuaValue get(LuaValue key) {
        return key.type() == TSTRING ? properties.getOrDefault(key.tojstring(), NIL) : NIL;
    }

    @Override
    public void set(String key, LuaValue value) {
        set(LuaValue.valueOf(key), value);
    }

    @Override
    public void set(LuaValue key, LuaValue value) {
        throw new LuaError("native: this value is immutable");
    }

    @Override
    public LuaValue setmetatable(LuaValue value) {
        throw new LuaError("native: bridge metatables are immutable");
    }
}
