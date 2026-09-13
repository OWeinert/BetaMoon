package betamoon.luaapi.world;

import betamoon.luaapi.utils.LuaCallbackScope;
import betamoon.luaapi.utils.LuaDeclarationValues;
import net.minecraft.src.Block;
import net.minecraft.src.World;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;
import static betamoon.luaapi.utils.LuaDeclarationValues.id;
import static betamoon.luaapi.utils.LuaDeclarationValues.integer;
import static betamoon.luaapi.utils.LuaDeclarationValues.number;
import static betamoon.luaapi.utils.LuaDeclarationValues.string;

/** Narrow live access for a single callback invocation. */
public final class LuaWorldActionAccess {
    private LuaWorldActionAccess() {
    }

    public static LuaTable create(LuaCallbackScope scope, World world, int x, int y, int z) {

        final LuaTable api = new LuaTable();
        api.set("getBlock", new VarArgFunction() {
            public Varargs invoke(Varargs a) {
                scope.requireActive();
                int bx = coordinate(argument(a, api, 1));
                int by = coordinate(argument(a, api, 2));
                int bz = coordinate(argument(a, api, 3));
                LuaTable value = new LuaTable();
                value.set("id", world.getBlockId(bx, by, bz));
                value.set("damage", world.getBlockMetadata(bx, by, bz));
                return value;
            }
        });
        api.set("setBlock", new VarArgFunction() {
            public Varargs invoke(Varargs a) {
                scope.requireMutable();
                int bx = coordinate(argument(a, api, 1));
                int by = integer(argument(a, api, 2), "y", 0, 127);
                int bz = coordinate(argument(a, api, 3));
                int id = id(argument(a, api, 4), "setBlock.id");
                if (id >= Block.blocksList.length || id != 0 && Block.blocksList[id] == null) {
                    throw LuaDeclarationValues.error("setBlock.id", "unknown block");
                }
                int damage = argument(a, api, 5).isnil() ? 0 : integer(argument(a, api, 5), "setBlock.damage", 0, 15);
                return valueOf(world.setBlockAndMetadataWithNotify(bx, by, bz, id, damage));
            }
        });
        api.set("playSound", new VarArgFunction() {
            public Varargs invoke(Varargs a) {
                scope.requireMutable();
                String sound = string(argument(a, api, 1), "sound");
                float volume = argument(a, api, 2).isnil() ? 1 : (float) number(argument(a, api, 2), "volume");
                float pitch = argument(a, api, 3).isnil() ? 1 : (float) number(argument(a, api, 3), "pitch");
                world.playSoundEffect(x + .5, y + .5, z + .5, sound, volume, pitch);
                return NIL;
            }
        });
        api.set("spawnParticle", new VarArgFunction() {
            public Varargs invoke(Varargs a) {
                scope.requireMutable();
                String name = string(argument(a, api, 1), "particle");
                world.spawnParticle(name, x + .5, y + .5, z + .5, 0, 0, 0);
                return NIL;
            }
        });
        return api;

    }

    private static LuaValue argument(Varargs args, LuaValue receiver, int index) {
        return args.arg(index + (args.arg1() == receiver ? 1 : 0));
    }

    private static int coordinate(LuaValue value) {
        return integer(value, "coordinate", -30000000, 30000000);
    }
}
