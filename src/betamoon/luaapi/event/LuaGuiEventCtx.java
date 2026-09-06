package betamoon.luaapi.event;

import betamoon.event.context.GuiEventCtx;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

public final class LuaGuiEventCtx extends LuaTable {
    private final GuiEventCtx context;

    public LuaGuiEventCtx(GuiEventCtx context) {
        this.context = context;
        if (context != null) {
            String current = context.getCurrentScreenName();
            String previous = context.getPreviousScreenName();
            set("name", current == null ? LuaValue.NIL : LuaValue.valueOf(current));
            set("previousName", previous == null ? LuaValue.NIL : LuaValue.valueOf(previous));
        }
    }

    private static final class GetName extends VarArgFunction {
        private final LuaGuiEventCtx owner;

        private GetName(LuaGuiEventCtx owner) {
            this.owner = owner;
        }

        @Override
        public Varargs invoke(Varargs args) {
            String name = owner.context == null ? null : owner.context.getGuiClassName();
            if (name == null) {
                return LuaValue.NIL;
            }
            return LuaValue.valueOf(name);
        }
    }

    private static final class GetOldName extends VarArgFunction {
        private final LuaGuiEventCtx owner;

        private GetOldName(LuaGuiEventCtx owner) {
            this.owner = owner;
        }

        @Override
        public Varargs invoke(Varargs args) {
            String name = owner.context == null ? null : owner.context.getPreviousScreenName();
            if (name == null) {
                return LuaValue.NIL;
            }
            return LuaValue.valueOf(name);
        }
    }

    private static final class GetNewName extends VarArgFunction {
        private final LuaGuiEventCtx owner;

        private GetNewName(LuaGuiEventCtx owner) {
            this.owner = owner;
        }

        @Override
        public Varargs invoke(Varargs args) {
            String name = owner.context == null ? null : owner.context.getCurrentScreenName();
            if (name == null) {
                return LuaValue.NIL;
            }
            return LuaValue.valueOf(name);
        }
    }
}
