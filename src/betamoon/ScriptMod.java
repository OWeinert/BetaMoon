package betamoon;

import java.util.List;
import org.luaj.vm2.LuaValue;

final class ScriptMod {
    final String name;
    final List dependencies;
    final LuaValue modInit;

    /**
     * Creates a parsed Lua mod definition with its name, dependencies, and init function.
     *
     * @param name declared mod name used for dependency ordering
     * @param dependencies list of mod names this mod depends on
     * @param modInit Lua function to invoke during mod initialization
     */
    ScriptMod(String name, List dependencies, LuaValue modInit) {
        this.name = name;
        this.dependencies = dependencies;
        this.modInit = modInit;
    }
}
