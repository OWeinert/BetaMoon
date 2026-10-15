package betamoon.luamodloader;

import java.util.Objects;
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
        Objects.requireNonNull(callbackName, "Callback name");
        Objects.requireNonNull(function, "Lua function");
        long startedNanos = System.nanoTime();
        try (ScriptExecutionScope ignored = ScriptExecutionScope.open(source)) {
            return function.invoke(arguments);
        } finally {
            SlowScriptCallbackMonitor.record(source, callbackName, startedNanos);
        }
    }
}
