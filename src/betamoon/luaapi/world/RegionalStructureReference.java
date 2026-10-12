package betamoon.luaapi.world;

import betamoon.worldgen.WorldGenKey;
import betamoon.worldgen.WorldGenRegistry;
import net.minecraft.src.World;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;
import org.luaj.vm2.lib.ZeroArgFunction;

/** Stable handle for one deterministic regional structure definition. */
public final class RegionalStructureReference extends LuaTable {
    private final WorldGenKey key;

    public RegionalStructureReference(WorldGenKey key) {
        this.key = key;
        set("getKey", new ZeroArgFunction() {
            @Override
            public LuaValue call() {
                return valueOf(RegionalStructureReference.this.key.toString());
            }
        });
        set("locate", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs arguments) {
                int offset = arguments.arg1() == RegionalStructureReference.this ? 1 : 0;
                World world = LuaWorldActionAccess.requireWorld(arguments.arg(1 + offset));
                int x = coordinate(arguments.arg(2 + offset), "regionalStructure.locate.x");
                int z = coordinate(arguments.arg(3 + offset), "regionalStructure.locate.z");
                int maxRegions = arguments.arg(4 + offset).isnil() ? 32
                        : integer(arguments.arg(4 + offset), "regionalStructure.locate.maxRegions", 0, 128);
                WorldGenRegistry.RegionalStructureLocation location = WorldGenRegistry.locateRegionalStructure(world,
                        RegionalStructureReference.this.key, x, z, maxRegions);
                if (location == null) {
                    return NIL;
                }
                LuaTable result = new LuaTable();
                result.set("x", location.x);
                result.set("y", location.y == null ? NIL : valueOf(location.y.intValue()));
                result.set("z", location.z);
                result.set("chunkX", location.chunkX);
                result.set("chunkZ", location.chunkZ);
                result.set("generated", valueOf(location.generated));
                return result;
            }
        });
    }

    public WorldGenKey key() {
        return key;
    }

    private static int coordinate(LuaValue value, String path) {
        return integer(value, path, -30000000, 30000000);
    }

    private static int integer(LuaValue value, String path, int min, int max) {
        if (!value.isint()) {
            throw new LuaError(path + ": expected an integer");
        }
        int result = value.toint();
        if (result < min || result > max) {
            throw new LuaError(path + ": expected " + min + ".." + max);
        }
        return result;
    }
}
