package betamoon.luaapi.input;

import betamoon.client.control.input.HotkeyEvent;
import betamoon.client.control.input.HotkeyRegistration;
import betamoon.client.control.input.ClientInputRuntime;
import betamoon.interop.runtime.NativeEventAccess;
import betamoon.luaapi.interop.NativeCallbackTable;
import betamoon.luaapi.utils.LuaCallbackScope;
import betamoon.luamodloader.ScriptExecution;
import net.minecraft.client.Minecraft;
import net.minecraft.src.KeyBinding;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

/** Lua handle for a user-configurable native ModLoader hotkey. */
final class LuaHotkey extends LuaTable {
    private final HotkeyRegistration registration;

    LuaHotkey(HotkeyRegistration registration, String owner) {
        this.registration = registration;
        set("key", LuaValue.valueOf(registration.definition().key().toString()));
        set("label", LuaValue.valueOf(registration.definition().label()));
        set("repeatWhileHeld", LuaValue.valueOf(registration.definition().repeat()));
        set("on", on(owner));
        set("getBinding", getBinding());
        set("isActive", isActive());
        set("close", closeHandle());
    }

    private VarArgFunction on(final String owner) {
        return new VarArgFunction() {
            public Varargs invoke(Varargs args) {
                LuaValue callback = LuaInputMap.argument(args, LuaHotkey.this, 1);
                try {
                    if (callback.isnil()) {
                        registration.on(null);
                    } else {
                        if (!callback.isfunction()) {
                            throw new LuaError("Hotkey callback must be a function or nil.");
                        }
                        final LuaValue function = callback.checkfunction();
                        registration.on(event -> invokeCallback(owner, function, event));
                    }
                    return LuaHotkey.this;
                } catch (IllegalStateException error) {
                    throw new LuaError("Hotkey listener: " + error.getMessage());
                }
            }
        };
    }

    private VarArgFunction getBinding() {
        return new VarArgFunction() {
            public Varargs invoke(Varargs args) {
                try {
                    return LuaValue.valueOf(registration.binding());
                } catch (IllegalStateException error) {
                    throw new LuaError("Hotkey binding: " + error.getMessage());
                }
            }
        };
    }

    private VarArgFunction isActive() {
        return new VarArgFunction() {
            public Varargs invoke(Varargs args) {
                return LuaValue.valueOf(registration.isActive());
            }
        };
    }

    private VarArgFunction closeHandle() {
        return new VarArgFunction() {
            public Varargs invoke(Varargs args) {
                registration.close();
                return LuaValue.NIL;
            }
        };
    }

    private static LuaTable event(HotkeyEvent event) {
        LuaTable result = new LuaTable();
        result.set("key", LuaValue.valueOf(event.key().toString()));
        result.set("binding", LuaValue.valueOf(event.binding()));
        result.set("keyCode", LuaValue.valueOf(event.keyCode()));
        return result;
    }

    private static void invokeCallback(String owner, LuaValue callback, HotkeyEvent event) {
        Minecraft client = ClientInputRuntime.client();
        try (LuaCallbackScope scope = new LuaCallbackScope(true)) {
            scope.nativeEntry(NativeEventAccess.entry(scope, client, client == null ? null : client.theWorld,
                    client == null ? null : client.thePlayer));
            ScriptExecution.invoke(owner, "input.hotkey", callback, NativeCallbackTable.wrap(scope, event(event)));
        }
    }

    KeyBinding nativeBinding() {
        return registration.nativeBinding();
    }
}
