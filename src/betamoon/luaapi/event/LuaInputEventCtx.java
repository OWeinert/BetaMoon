package betamoon.luaapi.event;

import betamoon.event.context.InputEventCtx;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

public final class LuaInputEventCtx extends LuaTable {
    private final InputEventCtx context;

    public LuaInputEventCtx(InputEventCtx context) {
        this.context = context;
        set("isKey", new IsKey(this));
        set("isMouse", new IsMouse(this));
        set("getKeyCode", new GetKeyCode(this));
        set("getChar", new GetChar(this));
        set("getButton", new GetButton(this));
        set("getX", new GetX(this));
        set("getY", new GetY(this));
        set("isPressed", new IsPressed(this));
        set("isReleased", new IsReleased(this));
        set("getAction", new GetAction(this));
    }

    private static final class IsKey extends VarArgFunction {
        private final LuaInputEventCtx owner;

        private IsKey(LuaInputEventCtx owner) {
            this.owner = owner;
        }

        @Override
        public Varargs invoke(Varargs args) {
            if (owner.context == null) {
                return LuaValue.NIL;
            }
            return LuaValue.valueOf(owner.context.isKeyEvent());
        }
    }

    private static final class IsMouse extends VarArgFunction {
        private final LuaInputEventCtx owner;

        private IsMouse(LuaInputEventCtx owner) {
            this.owner = owner;
        }

        @Override
        public Varargs invoke(Varargs args) {
            if (owner.context == null) {
                return LuaValue.NIL;
            }
            return LuaValue.valueOf(owner.context.isMouseEvent());
        }
    }

    private static final class GetKeyCode extends VarArgFunction {
        private final LuaInputEventCtx owner;

        private GetKeyCode(LuaInputEventCtx owner) {
            this.owner = owner;
        }

        @Override
        public Varargs invoke(Varargs args) {
            if (owner.context == null || !owner.context.isKeyEvent()) {
                return LuaValue.NIL;
            }
            return LuaValue.valueOf(owner.context.getKeyCode());
        }
    }

    private static final class GetChar extends VarArgFunction {
        private final LuaInputEventCtx owner;

        private GetChar(LuaInputEventCtx owner) {
            this.owner = owner;
        }

        @Override
        public Varargs invoke(Varargs args) {
            if (owner.context == null || !owner.context.isKeyEvent()) {
                return LuaValue.NIL;
            }
            char value = owner.context.getKeyChar();
            if (value == '\0') {
                return LuaValue.NIL;
            }
            return LuaValue.valueOf(String.valueOf(value));
        }
    }

    private static final class GetButton extends VarArgFunction {
        private final LuaInputEventCtx owner;

        private GetButton(LuaInputEventCtx owner) {
            this.owner = owner;
        }

        @Override
        public Varargs invoke(Varargs args) {
            if (owner.context == null || !owner.context.isMouseEvent()) {
                return LuaValue.NIL;
            }
            return LuaValue.valueOf(owner.context.getMouseButton());
        }
    }

    private static final class GetX extends VarArgFunction {
        private final LuaInputEventCtx owner;

        private GetX(LuaInputEventCtx owner) {
            this.owner = owner;
        }

        @Override
        public Varargs invoke(Varargs args) {
            if (owner.context == null || !owner.context.isMouseEvent()) {
                return LuaValue.NIL;
            }
            return LuaValue.valueOf(owner.context.getMouseX());
        }
    }

    private static final class GetY extends VarArgFunction {
        private final LuaInputEventCtx owner;

        private GetY(LuaInputEventCtx owner) {
            this.owner = owner;
        }

        @Override
        public Varargs invoke(Varargs args) {
            if (owner.context == null || !owner.context.isMouseEvent()) {
                return LuaValue.NIL;
            }
            return LuaValue.valueOf(owner.context.getMouseY());
        }
    }

    private static final class IsPressed extends VarArgFunction {
        private final LuaInputEventCtx owner;

        private IsPressed(LuaInputEventCtx owner) {
            this.owner = owner;
        }

        @Override
        public Varargs invoke(Varargs args) {
            if (owner.context == null) {
                return LuaValue.NIL;
            }
            return LuaValue.valueOf(owner.context.isPressed());
        }
    }

    private static final class IsReleased extends VarArgFunction {
        private final LuaInputEventCtx owner;

        private IsReleased(LuaInputEventCtx owner) {
            this.owner = owner;
        }

        @Override
        public Varargs invoke(Varargs args) {
            if (owner.context == null) {
                return LuaValue.NIL;
            }
            return LuaValue.valueOf(owner.context.isReleased());
        }
    }

    private static final class GetAction extends VarArgFunction {
        private final LuaInputEventCtx owner;

        private GetAction(LuaInputEventCtx owner) {
            this.owner = owner;
        }

        @Override
        public Varargs invoke(Varargs args) {
            if (owner.context == null || owner.context.getAction() == null) {
                return LuaValue.NIL;
            }
            return LuaValue.valueOf(owner.context.getAction().name().toLowerCase());
        }
    }
}
