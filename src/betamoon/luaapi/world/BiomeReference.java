package betamoon.luaapi.world;

import betamoon.worldgen.WorldGenKey;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.ZeroArgFunction;

/** Stable keyed biome handle. */
public final class BiomeReference extends LuaTable {
    private final WorldGenKey key;

    public BiomeReference(WorldGenKey key) {
        this.key = key;
        set("getKey", new ZeroArgFunction() {
            @Override
            public LuaValue call() {
                return valueOf(BiomeReference.this.key.toString());
            }
        });
    }

    WorldGenKey key() {
        return key;
    }
}
