package betamoon.luaapi.player;

import betamoon.luaapi.entity.EntityModifiersApi;
import betamoon.interop.runtime.NativeEventAccess;
import betamoon.luaapi.entity.LuaEntityActionAccess;
import betamoon.luaapi.utils.LuaCallbackScope;
import betamoon.luaapi.utils.LuaDeclarationValues;
import betamoon.luamodloader.LuaScriptRegistry;
import betamoon.luamodloader.ScriptExecution;
import betamoon.player.LocalPlayerAccess;
import betamoon.runtime.RuntimeEnvironment;
import net.minecraft.src.EntityPlayer;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

/**
 * Scoped local-player access; nested calls inherit existing read-only
 * authority.
 */
public final class PlayersApi {
    private PlayersApi() {
    }

    public static void attach(LuaTable module) {
        final LuaTable players = new LuaTable();
        players.set("modifyLocal", new VarArgFunction() {
            public Varargs invoke(Varargs args) {
                return EntityModifiersApi.registerLocal(args.arg(args.arg1() == players ? 2 : 1));
            }
        });
        players.set("withLocal", new VarArgFunction() {
            public Varargs invoke(Varargs args) {
                LuaValue callback = args.arg(args.arg1() == players ? 2 : 1);
                if (!callback.isfunction()) {
                    throw LuaDeclarationValues.error("players.withLocal", "expected a function");
                }
                if (!RuntimeEnvironment.isClient()) {
                    return unavailable("unsupported_environment");
                }
                LocalPlayerAccess.Resolution resolution;
                try {
                    resolution = LocalPlayerAccess.resolve();
                } catch (IllegalStateException error) {
                    throw LuaDeclarationValues.error("players.withLocal", error.getMessage());
                }
                if (resolution.player == null) {
                    return unavailable(resolution.reason);
                }
                EntityPlayer player = resolution.player;
                String owner = LuaScriptRegistry.getCurrentScriptFile();
                try (LuaCallbackScope scope = new LuaCallbackScope(true)) {
                    NativeEventAccess.configure(scope, player.worldObj, player);
                    ScriptExecution.invokeScoped(owner, "players.withLocal", callback,
                            LuaEntityActionAccess.create(scope, player, player), scope);
                }
                return LuaValue.TRUE;
            }
        });
        module.set("players", players);
    }

    private static Varargs unavailable(String reason) {
        return LuaValue.varargsOf(LuaValue.FALSE, LuaValue.valueOf(reason));
    }
}
