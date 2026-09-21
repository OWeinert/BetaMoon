package betamoon.luaapi.entity;

import betamoon.entity.EntityInstanceState;
import betamoon.luaapi.utils.LuaCallbackScope;
import net.minecraft.src.Entity;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

/** Scoped, copy-in/copy-out access to per-instance unsaved AI memory. */
public final class EntityMemoryAccess {
    private EntityMemoryAccess() {
    }

    public static LuaTable create(LuaCallbackScope scope, Entity entity, EntityInstanceState state) {
        LuaTable access = new LuaTable();
        access.set("get", new VarArgFunction() {
            public Varargs invoke(Varargs arguments) {
                scope.requireActive();
                return state.memory().get(argument(arguments, access, 1).checkjstring());
            }
        });
        access.set("set", new VarArgFunction() {
            public Varargs invoke(Varargs arguments) {
                scope.requireMutable();
                if (entity.isDead) {
                    throw new LuaError("Entity has been removed");
                }
                state.memory().set(argument(arguments, access, 1).checkjstring(),
                        argument(arguments, access, 2));
                return NIL;
            }
        });
        return access;
    }

    private static LuaValue argument(Varargs arguments, LuaValue receiver, int index) {
        return arguments.arg(index + (arguments.arg1() == receiver ? 1 : 0));
    }
}
