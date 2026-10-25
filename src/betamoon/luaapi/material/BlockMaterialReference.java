package betamoon.luaapi.material;

import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

/** Lua reference to a native material identity. */
public final class BlockMaterialReference extends LuaTable {
    private final BlockMaterialRegistry.Entry entry;

    BlockMaterialReference(BlockMaterialRegistry.Entry entry) {
        this.entry = entry;
        super.rawset(LuaValue.valueOf("key"), LuaValue.valueOf(entry.key));
        super.rawset(LuaValue.valueOf("isVanilla"), LuaValue.valueOf(!entry.custom()));
        super.rawset(LuaValue.valueOf("liquid"), LuaValue.valueOf(entry.material.getIsLiquid()));
        super.rawset(LuaValue.valueOf("solid"), LuaValue.valueOf(entry.material.getIsSolid()));
        if (entry.owner != null) {
            super.rawset(LuaValue.valueOf("owner"), LuaValue.valueOf(entry.owner));
        }
    }

    BlockMaterialRegistry.Entry entry() {
        return entry;
    }

    @Override
    public void set(LuaValue key, LuaValue value) {
        throw new LuaError("Material references are read-only.");
    }

    @Override
    public void rawset(LuaValue key, LuaValue value) {
        throw new LuaError("Material references are read-only.");
    }

    @Override
    public void set(int key, LuaValue value) {
        throw new LuaError("Material references are read-only.");
    }

    @Override
    public void rawset(int key, LuaValue value) {
        throw new LuaError("Material references are read-only.");
    }

    @Override
    public LuaValue setmetatable(LuaValue metatable) {
        throw new LuaError("Material references are read-only.");
    }
}
