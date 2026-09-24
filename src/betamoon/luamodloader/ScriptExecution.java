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
        Objects.requireNonNull(function, "Lua function");
        try (ScriptExecutionScope ignored = ScriptExecutionScope.open(source)) {
            return function.invoke(arguments);
        }
    }
}
