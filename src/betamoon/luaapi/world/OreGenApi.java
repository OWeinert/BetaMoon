package betamoon.luaapi.world;

import betamoon.worldgen.WorldGenRegistry;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

/** Installs declarative ore generation. */
public final class OreGenApi {
    private OreGenApi() {
    }

    public static void attach(LuaTable worldgen) {
        LuaTable ores = new LuaTable();
        ores.set("add", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                OreDeclaration declaration = new OreDeclaration(args.arg(args.arg1() == ores ? 2 : 1));
                WorldGenRegistry.addOreGen(declaration.blockId, declaration.veinsPerChunk, declaration.veinSize,
                        declaration.minY, declaration.maxY, declaration.dimension, declaration.targetBlockId,
                        declaration.getAllowedBiomes());
                return NIL;
            }
        });
        worldgen.set("ores", ores);
    }
}
