package betamoon.luaapi.world;

import org.luaj.vm2.LuaTable;

/** Assembles the declarative world-generation domains. */
public final class WorldGenApi {
    private WorldGenApi() {
    }

    public static void attach(LuaTable module) {
        LuaTable worldgen = new LuaTable();
        FeaturePlacementApi.attach(worldgen);
        StructureGenApi.attach(worldgen);
        TreeGenApi.attach(worldgen);
        OreGenApi.attach(worldgen);
        SurfaceGenApi.attach(worldgen);
        BiomeSourceApi.attach(worldgen);
        BiomeGenApi.attach(worldgen);
        module.set("worldgen", worldgen);
    }
}
