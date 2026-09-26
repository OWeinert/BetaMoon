package betamoon.luaapi.world;

import betamoon.worldgen.WorldGenKey;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.ZeroArgFunction;

/** Stable keyed surface-rule handle. */
public final class SurfaceReference extends LuaTable {
    private final WorldGenKey key;

    public SurfaceReference(WorldGenKey key) {
        this.key = key;
        set("getKey", new ZeroArgFunction() {
            @Override
            public LuaValue call() {
                return valueOf(SurfaceReference.this.key.toString());
            }
        });
    }

    WorldGenKey key() {
        return key;
    }
}
