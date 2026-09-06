package betamoon.luaapi.utils;

import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

public final class PositionI extends LuaTable {
    private int x;
    private int y;
    private int z;

    public PositionI(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
        set("x", LuaValue.valueOf(x));
        set("y", LuaValue.valueOf(y));
        set("z", LuaValue.valueOf(z));
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getZ() {
        return z;
    }

    public static void attach(LuaTable module) {
        module.set("PositionI", new CreatePositionI());
    }

    private static final class CreatePositionI extends VarArgFunction {
        @Override
        public Varargs invoke(Varargs args) {
            int base = (args.narg() >= 3 && args.arg(1).istable()) ? 2 : 1;
            int x = args.checkint(base);
            int y = args.checkint(base + 1);
            int z = args.checkint(base + 2);
            return new PositionI(x, y, z);
        }
    }
}
