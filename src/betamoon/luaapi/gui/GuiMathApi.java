package betamoon.luaapi.gui;

import betamoon.gui.animation.GuiEasing;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

/** Small calculation helpers; the presentation runtime performs its own Java-only sampling. */
public final class GuiMathApi extends LuaTable {
    public GuiMathApi() {
        set("lerp", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                int offset = args.arg1() == GuiMathApi.this ? 1 : 0;
                double from = GuiEasing.number(args.arg(offset + 1));
                double to = GuiEasing.number(args.arg(offset + 2));
                double t = GuiEasing.number(args.arg(offset + 3));
                double difference = to - from;
                double value = Double.isFinite(difference) ? from + difference * t : from * (1 - t) + to * t;
                if (!Double.isFinite(value)) {
                    throw new LuaError("lerp result is not finite.");
                }
                return LuaValue.valueOf(value);
            }
        });
        set("ease", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                int offset = args.arg1() == GuiMathApi.this ? 1 : 0;
                double t = GuiEasing.number(args.arg(offset + 2));
                if (t < 0 || t > 1) {
                    throw new LuaError("ease progress must be in 0..1.");
                }
                return LuaValue.valueOf(GuiEasing.parse(args.arg(offset + 1)).sample(t));
            }
        });
    }
}
