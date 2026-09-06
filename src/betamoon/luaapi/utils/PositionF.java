package betamoon.luaapi.utils;

import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

public final class PositionF extends LuaTable {
    private float x;
    private float y;
    private float z;

    public PositionF(float x, float y, float z) {
        this.x = x;
        this.y = y;
        this.z = z;
        set("x", LuaValue.valueOf(x));
        set("y", LuaValue.valueOf(y));
        set("z", LuaValue.valueOf(z));
    }

    public PositionF(double x, double y, double z) {
        this((float) x, (float) y, (float) z);
    }

    public float getX() {
        return x;
    }

    public float getY() {
        return y;
    }

    public float getZ() {
        return z;
    }

    public static void attach(LuaTable module) {
        module.set("PositionF", new CreatePositionF());
    }

    private static final class CreatePositionF extends VarArgFunction {
        @Override
        public Varargs invoke(Varargs args) {
            int base = (args.narg() >= 3 && args.arg(1).istable()) ? 2 : 1;
            double x = args.checkdouble(base);
            double y = args.checkdouble(base + 1);
            double z = args.checkdouble(base + 2);
            return new PositionF(x, y, z);
        }
    }
}
