package betamoon.luaapi.world;

import betamoon.assets.AssetKey;
import betamoon.luaapi.utils.LuaDataReference;
import org.luaj.vm2.LuaTable;

/** Typed Lua handle for a registered reusable loot table. */
public final class LootTableReference extends LuaTable implements LuaDataReference {
    private final AssetKey key;

    LootTableReference(AssetKey key) {
        this.key = key;
        set("key", valueOf(key.toString()));
    }

    AssetKey key() {
        return key;
    }

    @Override
    public Object declarationValue() {
        return key.toString();
    }

    @Override
    public String tojstring() {
        return key.toString();
    }
}
