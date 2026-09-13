package betamoon.luaapi.event;

import betamoon.event.context.WorldEventCtx;
import betamoon.luaapi.world.LuaWorldInfo;
import net.minecraft.src.World;
import net.minecraft.src.WorldInfo;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

public final class LuaWorldEventCtx extends LuaTable {
    private final WorldEventCtx context;

    public LuaWorldEventCtx(WorldEventCtx context) {
        this.context = context;
        World world = context == null ? null : context.getWorld();
        WorldInfo info = world == null ? null : world.getWorldInfo();
        if (info != null) {
            set("name", LuaValue.valueOf(info.getWorldName()));
            set("info", new LuaWorldInfo(info));
        }
    }

    private static final class GetName extends VarArgFunction {
        private final LuaWorldEventCtx owner;

        private GetName(LuaWorldEventCtx owner) {
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

    private static final class GetInfo extends VarArgFunction {
        private final LuaWorldEventCtx owner;

        private GetInfo(LuaWorldEventCtx owner) {
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
