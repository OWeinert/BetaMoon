package betamoon.luaapi.entity;

import betamoon.assets.AssetKey;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.ZeroArgFunction;

/** Stable key handle for a registered prop type. */
public final class EntityTypeReference extends LuaTable {
    private final AssetKey key;

    public EntityTypeReference(AssetKey key) {
        this.key = key;
        set("getKey", new ZeroArgFunction() {
            public LuaValue call() {
                return valueOf(EntityTypeReference.this.key.toString());
            }
        });
    }

    public AssetKey key() {
        return key;
    }
}
