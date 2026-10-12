package betamoon.luaapi.input;

import betamoon.client.control.input.InputContext;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

/** Explicit cleanup handle for an additional input-context layer. */
final class LuaInputContext extends LuaTable {
    LuaInputContext(final InputContext context, String owner) {
        set("token", LuaValue.valueOf(context.token()));
        set("priority", LuaValue.valueOf(context.priority()));
        set("on", LuaInputMap.on(this, context));
        set("isActive", new VarArgFunction() {
            public Varargs invoke(Varargs args) {
                return LuaValue.valueOf(context.isActive());
            }
        });
        set("close", new VarArgFunction() {
            public Varargs invoke(Varargs args) {
                context.close();
                return LuaValue.NIL;
            }
        });
    }
}
