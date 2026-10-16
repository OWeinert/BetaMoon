package betamoon.luaapi.world;

import betamoon.luaapi.utils.LuaDataReference;
import betamoon.worldgen.WorldGenKey;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.ZeroArgFunction;

/** Stable keyed biome-source handle. */
public final class BiomeSourceReference extends LuaTable implements LuaDataReference {
    private final WorldGenKey key;

    public BiomeSourceReference(WorldGenKey key) {
        this.key = key;
        set("getKey", new ZeroArgFunction() {
            @Override
            public LuaValue call() {
                return valueOf(BiomeSourceReference.this.key.toString());
            }
        });
    }

    WorldGenKey key() {
        return key;
    }

    @Override
    public Object declarationValue() {
        return key.toString();
    }
}
