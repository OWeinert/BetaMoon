package betamoon.luaapi.audio;

import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

/** Lua handle for a registered block step sound. */
public final class StepSoundReference extends LuaTable {
    private final BlockStepSounds.Entry entry;

    StepSoundReference(BlockStepSounds.Entry entry) {
        this.entry = entry;
        super.rawset(LuaValue.valueOf("key"), LuaValue.valueOf(entry.key));
        super.rawset(LuaValue.valueOf("owner"), LuaValue.valueOf(entry.owner));
    }

    BlockStepSounds.Entry entry() {
        return entry;
    }

    @Override
    public void set(LuaValue key, LuaValue value) {
        throw new LuaError("Step sound references are read-only.");
    }

    @Override
    public void rawset(LuaValue key, LuaValue value) {
        throw new LuaError("Step sound references are read-only.");
    }

    @Override
    public void set(int key, LuaValue value) {
        throw new LuaError("Step sound references are read-only.");
    }

    @Override
    public void rawset(int key, LuaValue value) {
        throw new LuaError("Step sound references are read-only.");
    }

    @Override
    public LuaValue setmetatable(LuaValue metatable) {
        throw new LuaError("Step sound references are read-only.");
    }
}
