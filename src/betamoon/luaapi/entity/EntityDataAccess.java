package betamoon.luaapi.entity;

import betamoon.entity.EntityInstanceState;
import betamoon.entity.EntityTypeDefinition;
import betamoon.luaapi.utils.LuaCallbackScope;
import net.minecraft.src.Entity;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

/** Scoped access to declared, typed entity data. */
public final class EntityDataAccess {
    private EntityDataAccess() {
    }

    public static LuaTable create(LuaCallbackScope scope, Entity entity, EntityInstanceState state) {
        LuaTable access = new LuaTable();
        EntityDataLuaContext context = new EntityDataLuaContext(scope, entity.worldObj);
        access.set("get", new VarArgFunction() {
            public Varargs invoke(Varargs arguments) {
                scope.requireActive();
                String name = argument(arguments, access, 1).checkjstring();
                return state.data().get(requireDefinition(state), name, context);
            }
        });
        access.set("set", new VarArgFunction() {
            public Varargs invoke(Varargs arguments) {
                scope.requireMutable();
                if (entity.isDead) {
                    throw new LuaError("Entity has been removed");
                }
                String name = argument(arguments, access, 1).checkjstring();
                LuaValue value = argument(arguments, access, 2);
                state.data().set(requireDefinition(state), name, value, context);
                return NIL;
            }
        });
        return access;
    }

    private static EntityTypeDefinition requireDefinition(EntityInstanceState state) {
        EntityTypeDefinition definition = state.definition();
        if (definition == null) {
            throw new LuaError("Entity definition or saved data is unavailable: " + state.typeName());
        }
        return definition;
    }

    private static LuaValue argument(Varargs args, LuaValue receiver, int index) {
        return args.arg(index + (args.arg1() == receiver ? 1 : 0));
    }
}
