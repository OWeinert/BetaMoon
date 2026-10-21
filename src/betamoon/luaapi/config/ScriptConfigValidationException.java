package betamoon.luaapi.config;

import org.luaj.vm2.LuaError;

/** A rejected config value with the declared field path that caused the error. */
public final class ScriptConfigValidationException extends LuaError {
    private final String path;

    ScriptConfigValidationException(String path, String problem) {
        super(path + " " + problem);
        this.path = path;
    }

    public String path() {
        return path;
    }
}
