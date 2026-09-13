package betamoon.luaapi.entity;

import betamoon.luaapi.utils.LuaCallbackScope;
import net.minecraft.src.Entity;
import net.minecraft.src.EntityLiving;
import net.minecraft.src.EntityPlayer;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;
import static betamoon.luaapi.utils.LuaDeclarationValues.integer;
import static betamoon.luaapi.utils.LuaDeclarationValues.number;

/** Narrow live access for a single callback invocation. */
public final class LuaEntityActionAccess {
    private LuaEntityActionAccess() {
    }

    public static LuaTable create(LuaCallbackScope scope, EntityPlayer player, final Entity entity) {

        final LuaTable api = new LuaTable();
        api.set("isPlayer", new VarArgFunction() {
            public Varargs invoke(Varargs a) {
                scope.requireActive();
                return valueOf(entity instanceof EntityPlayer);
            }
        });
        api.set("isSneaking", new VarArgFunction() {
            public Varargs invoke(Varargs a) {
                scope.requireActive();
                return valueOf(entity.isSneaking());
            }
        });
        api.set("heal", new VarArgFunction() {
            public Varargs invoke(Varargs a) {
                scope.requireMutable();
                if (!(entity instanceof EntityLiving)) {
                    return FALSE;
                }
                ((EntityLiving) entity).heal(integer(argument(a, api, 1), "heal", 0, 32767));
                return TRUE;
            }
        });
        api.set("damage", new VarArgFunction() {
            public Varargs invoke(Varargs a) {
                scope.requireMutable();
                return valueOf(entity.attackEntityFrom(player, integer(argument(a, api, 1), "damage", 0, 32767)));
            }
        });
        api.set("setVelocity", new VarArgFunction() {
            public Varargs invoke(Varargs a) {
                scope.requireMutable();
                entity.motionX = number(argument(a, api, 1), "velocity.x");
                entity.motionY = number(argument(a, api, 2), "velocity.y");
                entity.motionZ = number(argument(a, api, 3), "velocity.z");
                return NIL;
            }
        });
        api.set("getVelocity", new VarArgFunction() {
            public Varargs invoke(Varargs a) {
                scope.requireActive();
                return velocitySnapshot(entity);
            }
        });
        return api;

    }

    private static LuaValue argument(Varargs args, LuaValue receiver, int index) {
        return args.arg(index + (args.arg1() == receiver ? 1 : 0));
    }

    private static LuaTable velocitySnapshot(Entity entity) {
        LuaTable velocity = new LuaTable();
        velocity.set("x", entity.motionX);
        velocity.set("y", entity.motionY);
        velocity.set("z", entity.motionZ);
        return velocity;
    }

    private static int coordinate(LuaValue value) {
        return integer(value, "coordinate", -30000000, 30000000);
    }
}
