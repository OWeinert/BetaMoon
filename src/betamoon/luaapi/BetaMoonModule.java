package betamoon.luaapi;

import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.TwoArgFunction;

public final class BetaMoonModule extends TwoArgFunction {
    /**
     * Installs the BetaMoon Lua API into the given environment and returns the module table.
     *
     * @param modname unused module name provided by Lua's require/load mechanism
     * @param env Lua environment to receive the betamoon module table
     * @return the populated module table
     */
    public LuaValue call(LuaValue modname, LuaValue env) {
        LuaTable module = new LuaTable();
        BlockApi.attach(module);
        WorldGenApi.attach(module);
        ItemApi.attach(module);
        ItemToolApi.attach(module);
        ToolMaterialApi.attach(module);
        RecipeApi.attach(module);
        ItemArmorApi.attach(module);
        ArmorMaterialApi.attach(module);
        ModuleApi.attach(module, env);
        env.set("betamoon", module);
        LuaValue packageTable = env.get("package");
        if (packageTable.istable()) {
            packageTable.get("loaded").set("betamoon", module);
        }
        return module;
    }
}
