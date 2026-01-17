package betamoon.luaapi;

import betamoon.luaapi.block.BlockApi;
import betamoon.luaapi.item.ItemApi;
import betamoon.luaapi.item.ItemArmorApi;
import betamoon.luaapi.item.ItemToolApi;
import betamoon.luaapi.material.ArmorMaterialApi;
import betamoon.luaapi.material.ToolMaterialApi;
import betamoon.luaapi.module.ModuleApi;
import betamoon.luaapi.query.QueryApi;
import betamoon.luaapi.recipe.RecipeApi;
import betamoon.luaapi.worldgen.WorldGenApi;
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
        QueryApi.attach(module);
        ModuleApi.attach(module, env);
        env.set("betamoon", module);
        LuaValue packageTable = env.get("package");
        if (packageTable.istable()) {
            packageTable.get("loaded").set("betamoon", module);
        }
        return module;
    }
}
