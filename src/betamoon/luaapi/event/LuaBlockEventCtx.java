package betamoon.luaapi.event;

import betamoon.event.context.BlockEventCtx;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

public final class LuaBlockEventCtx extends LuaTable {
    private final BlockEventCtx context;

    public LuaBlockEventCtx(BlockEventCtx context) {
        this.context = context;
        set("getX", new GetX(this));
        set("getY", new GetY(this));
        set("getZ", new GetZ(this));
        set("getPos", new GetPos(this));
        set("getSideHit", new GetSideHit(this));
    }

    private static final class GetX extends VarArgFunction {
        private final LuaBlockEventCtx owner;

        private GetX(LuaBlockEventCtx owner) {
            this.owner = owner;
        }

        @Override
        public Varargs invoke(Varargs args) {
            if (owner.context == null) {
                return LuaValue.NIL;
            }
            return LuaValue.valueOf(owner.context.getX());
        }
    }

    private static final class GetY extends VarArgFunction {
        private final LuaBlockEventCtx owner;

        private GetY(LuaBlockEventCtx owner) {
            this.owner = owner;
        }

        @Override
        public Varargs invoke(Varargs args) {
            if (owner.context == null) {
                return LuaValue.NIL;
            }
            return LuaValue.valueOf(owner.context.getY());
        }
    }

    private static final class GetZ extends VarArgFunction {
        private final LuaBlockEventCtx owner;

        private GetZ(LuaBlockEventCtx owner) {
            this.owner = owner;
        }

        @Override
        public Varargs invoke(Varargs args) {
            if (owner.context == null) {
                return LuaValue.NIL;
            }
            return LuaValue.valueOf(owner.context.getZ());
        }
    }

    private static final class GetSideHit extends VarArgFunction {
        private final LuaBlockEventCtx owner;

        private GetSideHit(LuaBlockEventCtx owner) {
            this.owner = owner;
        }

        @Override
        public Varargs invoke(Varargs args) {
            if (owner.context == null) {
                return LuaValue.NIL;
            }
            return LuaValue.valueOf(owner.context.getSideHit());
        }
    }

    private static final class GetPos extends VarArgFunction {
        private final LuaBlockEventCtx owner;

        private GetPos(LuaBlockEventCtx owner) {
            this.owner = owner;
        }

        @Override
        public Varargs invoke(Varargs args) {
            if (owner.context == null) {
                return LuaValue.NIL;
            }
            LuaTable pos = new LuaTable();
            pos.set("x", LuaValue.valueOf(owner.context.getX()));
            pos.set("y", LuaValue.valueOf(owner.context.getY()));
            pos.set("z", LuaValue.valueOf(owner.context.getZ()));
            return pos;
        }
    }
}
