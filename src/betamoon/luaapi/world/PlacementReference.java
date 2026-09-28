package betamoon.luaapi.world;

import betamoon.worldgen.WorldGenKey;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.ZeroArgFunction;

/** Stable key handle for a compiled placement. */
public class PlacementReference extends LuaTable {
    private final WorldGenKey key;
    private final WorldGenKey featureKey;

    public PlacementReference(WorldGenKey key, WorldGenKey featureKey) {
        this.key = key;
        this.featureKey = featureKey;
        set("getKey", new ZeroArgFunction() {
            @Override
            public LuaValue call() {
                return valueOf(PlacementReference.this.key.toString());
            }
        });
        set("getFeature", new ZeroArgFunction() {
            @Override
            public LuaValue call() {
                return new FeatureReference(PlacementReference.this.featureKey);
            }
        });
    }

    public WorldGenKey key() {
        return key;
    }
}
