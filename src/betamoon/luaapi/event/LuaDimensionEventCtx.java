package betamoon.luaapi.event;

import betamoon.event.context.DimensionEventCtx;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

public final class LuaDimensionEventCtx extends LuaTable {
    private final DimensionEventCtx context;

    public LuaDimensionEventCtx(DimensionEventCtx context) {
        this.context = context;
        set("getOldId", new GetOldId(this));
        set("getNewId", new GetNewId(this));
    }

    private static final class GetOldId extends VarArgFunction {
        private final LuaDimensionEventCtx owner;

        private GetOldId(LuaDimensionEventCtx owner) {
            this.owner = owner;
        }

        @Override
        public Varargs invoke(Varargs args) {
            if (owner.context == null) {
                return LuaValue.NIL;
            }
            return LuaValue.valueOf(owner.context.getPreviousDimension());
        }
    }

    private static final class GetNewId extends VarArgFunction {
        private final LuaDimensionEventCtx owner;

        private GetNewId(LuaDimensionEventCtx owner) {
            this.owner = owner;
        }

        @Override
        public Varargs invoke(Varargs args) {
            if (owner.context == null) {
                return LuaValue.NIL;
            }
            return LuaValue.valueOf(owner.context.getCurrentDimension());
        }
    }
}
