package betamoon.luaapi.event;

import betamoon.event.context.PlayerEventCtx;
import net.minecraft.src.EntityPlayer;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

public final class LuaPlayerEventCtx extends LuaTable {
    private final PlayerEventCtx context;

    public LuaPlayerEventCtx(PlayerEventCtx context) {
        this.context = context;
        set("getName", new GetName(this));
    }

    private static final class GetName extends VarArgFunction {
        private final LuaPlayerEventCtx owner;

        private GetName(LuaPlayerEventCtx owner) {
            this.owner = owner;
        }

        @Override
        public Varargs invoke(Varargs args) {
            EntityPlayer player = owner.context == null ? null : owner.context.getPlayer();
            if (player == null || player.username == null) {
                return LuaValue.NIL;
            }
            return LuaValue.valueOf(player.username);
        }
    }
}
