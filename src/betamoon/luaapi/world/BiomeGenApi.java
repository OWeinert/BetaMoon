package betamoon.luaapi.world;

import betamoon.worldgen.BiomeRegistration;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

/** Installs declarative biome generation. */
public final class BiomeGenApi {
    private BiomeGenApi() {
    }

    public static void attach(LuaTable worldgen) {
        LuaTable biomes = new LuaTable();
        biomes.set("add", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                BiomeRegistration.register(new BiomeDeclaration(args.arg(args.arg1() == biomes ? 2 : 1)));
                return NIL;
            }
        });
        worldgen.set("biomes", biomes);
    }
}
