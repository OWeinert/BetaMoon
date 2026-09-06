package betamoon.luaapi;

import betamoon.luaapi.block.BlockApi;
import betamoon.luaapi.item.ItemApi;
import betamoon.luaapi.item.ItemArmorApi;
import betamoon.luaapi.item.ItemToolApi;
import betamoon.luaapi.material.ArmorMaterialApi;
import betamoon.luaapi.material.ToolMaterialApi;
import betamoon.luaapi.chat.ChatApi;
import betamoon.luaapi.event.EventsApi;
import betamoon.luaapi.module.ModuleApi;
import betamoon.luaapi.recipe.RecipeApi;
import betamoon.luaapi.utils.PositionF;
import betamoon.luaapi.utils.PositionI;
import betamoon.luaapi.world.WorldGenApi;
import betamoon.luaapi.resource.ResourceApi;
import betamoon.luaapi.resource.RecipeRegistryApi;
import betamoon.luaapi.v2.DeclarativeApi;
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
        // Legacy bindings remain private implementation details for the declarative facade.
        LuaTable backend = new LuaTable();
        BlockApi.attach(backend);
        WorldGenApi.attach(backend);
        ItemApi.attach(backend);
        ItemToolApi.attach(backend);
        ToolMaterialApi.attach(backend);
        RecipeApi.attach(backend);
        ItemArmorApi.attach(backend);
        ArmorMaterialApi.attach(backend);
        ModuleApi.attach(backend, env);
        ChatApi.attach(backend);
        PositionF.attach(backend);
        PositionI.attach(backend);

        LuaTable module = new LuaTable();
        EventsApi.attach(module);

        LuaTable recipes = new LuaTable();
        module.set("recipes", recipes);

        LuaTable worldgen = new LuaTable();
        module.set("worldgen", worldgen);

        LuaTable chat = new LuaTable();
        chat.set("send", backend.get("chat"));
        chat.set("broadcast", backend.get("broadcast"));
        module.set("chat", chat);

        LuaTable modules = new LuaTable();
        modules.set("export", backend.get("exportModule"));
        modules.set("require", backend.get("requireModule"));
        module.set("modules", modules);

        LuaTable positions = new LuaTable();
        positions.set("integer", backend.get("PositionI"));
        positions.set("float", backend.get("PositionF"));
        module.set("positions", positions);

        ResourceApi.attach(module, backend);
        DeclarativeApi.attach(module, backend);
        RecipeRegistryApi.attach(module);

        // Module Registration
        env.set("betamoon", module);
        LuaValue packageTable = env.get("package");
        if (packageTable.istable()) {
            packageTable.get("loaded").set("betamoon", module);
        }
        return module;
    }
}
