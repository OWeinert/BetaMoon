package betamoon.luamodloader;

import betamoon.luaapi.interop.NativeInvocation;
import betamoon.luaapi.utils.LuaCallbackScope;
import java.util.Objects;
import java.util.function.Supplier;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

/** Runs a Lua-owned Java API function with its source identity available. */
public final class ScriptExecution {
    private ScriptExecution() {
    }

    public static Varargs invoke(String source, LuaValue function, Varargs arguments) {
        Objects.requireNonNull(source, "Lua source");
        return invoke(source, "callback", function, arguments);
    }

    /**
     * Runs a named callback; a null source preserves support for unowned Java
     * subscriptions.
     */
    public static Varargs invoke(String source, String callbackName, LuaValue function, Varargs arguments) {
        return invoke(source, callbackName, function, arguments, null, false);
    }

    /**
     * Explicit scope for callbacks whose first argument is a value rather than a
     * context table.
     */
    public static Varargs invokeScoped(String source, String callbackName, LuaValue function, Varargs arguments,
            LuaCallbackScope scope) {
        return invoke(source, callbackName, function, arguments, scope, false);
    }

    /**
     * Java API attribution preserves the invoking callback's authority instead of
     * opening another callback.
     */
    public static Varargs invokeApi(String source, LuaValue function, Varargs arguments) {
        return invoke(source, "callback", function, arguments, null, true);
    }

    private static Varargs invoke(String source, String callbackName, LuaValue function, Varargs arguments,
            LuaCallbackScope scope, boolean api) {
        Objects.requireNonNull(callbackName, "Callback name");
        Objects.requireNonNull(function, "Lua function");
        long startedNanos = System.nanoTime();
        try (ScriptExecutionScope ignored = ScriptExecutionScope.open(source);
                ScriptDiagnostics.Scope diagnostic = ScriptDiagnostics.scope(null, callbackName);
                NativeInvocation nativeInvocation = api
                        ? null
                        : scope == null
                                ? NativeInvocation.open(source, arguments)
                                : NativeInvocation.open(source, arguments, scope)) {
            try {
                return function.invoke(arguments);
            } catch (RuntimeException | Error error) {
                ScriptDiagnostics.remember(error);
                throw error;
            }
        } finally {
            SlowScriptCallbackMonitor.record(source, callbackName, startedNanos);
        }
    }

    /**
     * Restores a captured owner around a native/base continuation without
     * double-counting it.
     */
    public static <T> T withOwner(String source, Supplier<T> action) {
        Objects.requireNonNull(action, "Owner-scoped action");
        try (ScriptExecutionScope ignored = ScriptExecutionScope.open(source)) {
            return action.get();
        }
    }
}
