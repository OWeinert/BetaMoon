package betamoon.luaapi.tileentity;

import betamoon.luaapi.utils.LuaCallbackScope;
import betamoon.tileentity.LuaTileEntity;
import betamoon.tileentity.TileEntityDefinition;
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
                return toLua(tile.getDataValue(name.checkjstring()));
            }
        });
        data.set("set", new VarArgFunction() {
            public Varargs invoke(Varargs arguments) {
                requireMutable(scope);
                int offset = arguments.arg1() == data ? 1 : 0;
                String name = arguments.arg(offset + 1).checkjstring();
                TileEntityDefinition.Field field = tile.getDefinition().fields.get(name);
                if (field == null) {
                    throw new LuaError("Unknown tile entity data field: " + name);
                }
                tile.setDataValue(name, field.type.fromLua(arguments.arg(offset + 2)));
                return LuaValue.NIL;
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

    private static LuaValue toLua(Object value) {
        if (value == null) {
            return LuaValue.NIL;
        }
        if (value instanceof Boolean) {
            return LuaValue.valueOf(((Boolean) value).booleanValue());
        }
        if (value instanceof Integer) {
            return LuaValue.valueOf(((Integer) value).intValue());
        }
        if (value instanceof Number) {
            return LuaValue.valueOf(((Number) value).doubleValue());
        }
        return LuaValue.valueOf(String.valueOf(value));
    }
}
