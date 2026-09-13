package betamoon.luaapi;

import betamoon.luaapi.chat.ChatApi;
import betamoon.luaapi.event.EventsApi;
import betamoon.luaapi.material.ArmorMaterialApi;
import betamoon.luaapi.material.ToolMaterialApi;
import betamoon.luaapi.minecraft.MinecraftApi;
import betamoon.luaapi.module.ModuleApi;
import betamoon.luaapi.recipe.RecipeApi;
import betamoon.luaapi.recipe.RecipeTypesApi;
import betamoon.luaapi.resource.RecipeRegistryApi;
import betamoon.luaapi.resource.ResourceApi;
import betamoon.luaapi.tileentity.TileEntityApi;
import betamoon.luaapi.utils.PositionF;
import betamoon.luaapi.utils.PositionI;
import betamoon.luaapi.world.WorldGenApi;
import betamoon.recipes.custom.RecipeBindings;
import betamoon.recipes.custom.RecipeMatcherRegistry;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.TwoArgFunction;

public final class BetaMoonModule extends TwoArgFunction {
    /**
     * Installs the BetaMoon Lua API into the given environment and returns the
     * module table.
     *
     * @param modname
     *            unused module name provided by Lua's require/load mechanism
     * @param env
     *            Lua environment to receive the betamoon module table
     * @return the populated module table
     */
    @Override
    public LuaValue call(LuaValue modname, LuaValue env) {
        LuaTable module = new LuaTable();
        EventsApi.attach(module);

        LuaTable materials = new LuaTable();
        ToolMaterialApi.attach(materials);
        ArmorMaterialApi.attach(materials);
        module.set("materials", materials);

        LuaTable recipes = new LuaTable();
        module.set("recipes", recipes);

        WorldGenApi.attach(module);
        MinecraftApi.attach(module);

        ChatApi.attach(module);
        ModuleApi.attach(module, env);

        LuaTable positions = new LuaTable();
        PositionI.attach(positions);
        PositionF.attach(positions);
        module.set("positions", positions);

        ResourceApi.attach(module);
        RecipeApi.attach(module);
        RecipeRegistryApi.attach(module);
        RecipeTypesApi.attach(module);
        RecipeBindings.attach(module);
        RecipeMatcherRegistry.attach(module);
        TileEntityApi.attach(module);

        // Module Registration
        env.set("betamoon", module);
        LuaValue packageTable = env.get("package");
        if (packageTable.istable()) {
            packageTable.get("loaded").set("betamoon", module);
        }
        return module;
    }
}
