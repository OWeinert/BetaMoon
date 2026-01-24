package betamoon.luaapi.event;

import betamoon.event.context.GameEventCtx;
import betamoon.luaapi.world.LuaWorldInfo;
import net.minecraft.src.World;
import net.minecraft.src.WorldInfo;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

public final class LuaGameEventCtx extends LuaTable {
    private final GameEventCtx context;

    public LuaGameEventCtx(GameEventCtx context) {
        this.context = context;
        set("getWorldName", new GetWorldName(this));
        set("getWorldInfo", new GetWorldInfo(this));
    }

    private static final class GetWorldName extends VarArgFunction {
        private final LuaGameEventCtx owner;

        private GetWorldName(LuaGameEventCtx owner) {
            this.owner = owner;
        }

        @Override
        public Varargs invoke(Varargs args) {
            World world = owner.context == null ? null : owner.context.getWorld();
            if (world == null) {
                return LuaValue.NIL;
            }
            WorldInfo info = world.getWorldInfo();
            if (info == null) {
                return LuaValue.NIL;
            }
            String name = info.getWorldName();
            if (name == null) {
                return LuaValue.NIL;
            }
            return LuaValue.valueOf(name);
        }
    }

    private static final class GetWorldInfo extends VarArgFunction {
        private final LuaGameEventCtx owner;

        private GetWorldInfo(LuaGameEventCtx owner) {
            this.owner = owner;
        }

        @Override
        public Varargs invoke(Varargs args) {
            World world = owner.context == null ? null : owner.context.getWorld();
            if (world == null) {
                return LuaValue.NIL;
            }
            WorldInfo info = world.getWorldInfo();
            if (info == null) {
                return LuaValue.NIL;
            }
            return new LuaWorldInfo(info);
        }
    }
}
