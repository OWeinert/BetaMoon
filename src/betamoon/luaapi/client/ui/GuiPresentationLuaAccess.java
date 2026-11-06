package betamoon.luaapi.client.ui;

import betamoon.gui.screen.GuiClientUiRuntime;
import betamoon.gui.screen.GuiPresentationActivation;
import betamoon.gui.screen.GuiScreenActivation;
import betamoon.luaapi.utils.LuaCallbackScope;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

/**
 * Owned native-host claim handles share the same scope and instance contracts.
 */
final class GuiPresentationLuaAccess {
    static LuaTable activation(String owner, GuiPresentationActivation activation) {
        LuaTable value = new LuaTable();
        value.set("key", activation.key);
        value.set("status", function(owner, null, false, args -> LuaValue.valueOf(activation.status())));
        value.set("reason", function(owner, null, false,
                args -> activation.reason() == null ? LuaValue.NIL : LuaValue.valueOf(activation.reason())));
        value.set("close", function(owner, null, true, args -> {
            activation.close();
            return LuaValue.NIL;
        }));
        value.set("instance", function(owner, null, false, args -> {
            GuiScreenActivation instance = activation.instance();
            return instance == null
                    ? LuaValue.NIL
                    : GuiScreenLuaAccess.instance(owner, instance, null, GuiClientUiRuntime.screens()::getRequired);
        }));
        return value;
    }

    static LuaValue function(String owner, LuaCallbackScope scope, boolean mutable, GuiUiApi.Method method) {
        LuaValue operation = GuiUiApi.owned(owner, method);
        return new VarArgFunction() {
            public Varargs invoke(Varargs arguments) {
                GuiScreenLuaAccess.guard(owner, scope, mutable);
                return operation.invoke(arguments);
            }
        };
    }

    private GuiPresentationLuaAccess() {
    }
}
