package betamoon.luaapi.tileentity;

import betamoon.luaapi.utils.LuaCallbackScope;
import betamoon.tileentity.LuaTileEntity;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

/**
 * Reuses tile-data validation while respecting a block callback's lifetime and
 * query permissions.
 */
public final class LuaTileDataAccess {
    private LuaTileDataAccess() {
    }

    public static LuaTable create(LuaTileEntity tile) {
        return create(null, tile);
    }

    public static LuaTable create(final LuaCallbackScope scope, final LuaTileEntity tile) {
        final LuaTable data = new LuaTable();
        data.set("get", new VarArgFunction() {
            public Varargs invoke(Varargs arguments) {
                requireActive(scope);
                LuaValue name = arguments.arg(arguments.arg1() == data ? 2 : 1);
                return tile.getDataLua(name.checkjstring());
            }
        });
        data.set("set", new VarArgFunction() {
            public Varargs invoke(Varargs arguments) {
                requireMutable(scope);
                int offset = arguments.arg1() == data ? 1 : 0;
                String name = arguments.arg(offset + 1).checkjstring();
                tile.setDataLua(name, arguments.arg(offset + 2));
                return LuaValue.NIL;
            }
        });
        data.set("snapshot", new VarArgFunction() {
            public Varargs invoke(Varargs arguments) {
                requireActive(scope);
                return tile.snapshotData();
            }
        });
        return data;
    }

    private static void requireActive(LuaCallbackScope scope) {
        if (scope != null) {
            scope.requireActive();
        }
    }

    private static void requireMutable(LuaCallbackScope scope) {
        if (scope != null) {
            scope.requireMutable();
        }
    }

}
