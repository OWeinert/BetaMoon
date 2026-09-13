package betamoon.luamodloader;

/** Associates registrations and diagnostics with one executing Lua script. */
final class ScriptExecutionScope implements AutoCloseable {
    private final String previousScript;
    private boolean closed;

    private ScriptExecutionScope(String scriptFile) {
        previousScript = LuaScriptRegistry.getCurrentScriptFile();
        LuaScriptRegistry.setCurrentScriptFile(scriptFile);
    }

    static ScriptExecutionScope open(String scriptFile) {
        return new ScriptExecutionScope(scriptFile);
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }

        closed = true;
        LuaScriptRegistry.setCurrentScriptFile(previousScript);
    }
}
