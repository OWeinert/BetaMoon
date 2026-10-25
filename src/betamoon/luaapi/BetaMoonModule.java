package betamoon.luaapi;

import betamoon.luaapi.asset.AssetsApi;
import betamoon.luaapi.audio.AudioApi;
import betamoon.luaapi.audio.StepSoundApi;
import betamoon.luaapi.capability.CapabilitiesApi;
import betamoon.luaapi.chat.ChatApi;
import betamoon.luaapi.config.ConfigApi;
import betamoon.luaapi.entity.EntitiesApi;
import betamoon.luaapi.event.EventsApi;
import betamoon.luaapi.fluid.FluidApi;
import betamoon.luaapi.fuel.FuelsApi;
import betamoon.luaapi.input.InputApi;
import betamoon.luaapi.interop.NativeDiscoveryApi;
import betamoon.luaapi.logging.LoggingApi;
import betamoon.luaapi.material.ArmorMaterialApi;
import betamoon.luaapi.material.BlockMaterialApi;
import betamoon.luaapi.material.ToolMaterialApi;
import betamoon.luaapi.minecraft.MinecraftApi;
import betamoon.luaapi.module.ModuleApi;
import betamoon.luaapi.multiblock.MultiblocksApi;
import betamoon.luaapi.networking.LogicalNetworksApi;
import betamoon.luaapi.particle.ParticlesApi;
import betamoon.luaapi.player.PlayersApi;
import betamoon.luaapi.recipe.RecipeApi;
import betamoon.luaapi.recipe.RecipeTypesApi;
import betamoon.luaapi.resource.RecipeRegistryApi;
import betamoon.luaapi.resource.ResourceApi;
import betamoon.luaapi.system.SystemsApi;
import betamoon.luaapi.tileentity.TileEntityApi;
import betamoon.luaapi.utils.PositionF;
import betamoon.luaapi.utils.PositionI;
import betamoon.luaapi.world.AtmospheresApi;
import betamoon.luaapi.world.DimensionsApi;
import betamoon.luaapi.world.LootTablesApi;
import betamoon.luaapi.world.TeleportApi;
import betamoon.luaapi.world.WorldGenApi;
import betamoon.luamodloader.ScriptExecution;
import betamoon.recipes.custom.RecipeBindings;
import betamoon.recipes.custom.RecipeMatcherRegistry;
import java.io.File;
import java.util.IdentityHashMap;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.TwoArgFunction;
import org.luaj.vm2.lib.VarArgFunction;

public final class BetaMoonModule extends TwoArgFunction {
    private final String source;
    private final File configRoot;

    public BetaMoonModule() {
        this(null);
    }

    public BetaMoonModule(String source) {
        this(source, null);
    }

    public BetaMoonModule(String source, File configRoot) {
        this.source = source;
        this.configRoot = configRoot;
    }

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
        LoggingApi.attach(module, source);
        CallbackResultsApi.attach(module);
        ConfigApi.attach(module, configRoot, source);
        EventsApi.attach(module);
        NativeDiscoveryApi.attach(module, source);
        AssetsApi.attach(module);
        ParticlesApi.attach(module);
        MultiblocksApi.attach(module);
        EntitiesApi.attach(module);
        PlayersApi.attach(module);
        FuelsApi.attach(module);
        FluidApi.attach(module);
        AudioApi.attach(module);
        StepSoundApi.attach(module);
        AtmospheresApi.attach(module);
        DimensionsApi.attach(module);
        TeleportApi.attach(module);
        InputApi.attach(module);
        CapabilitiesApi.attach(module);
        SystemsApi.attach(module);
        LogicalNetworksApi.attach(module);

        LuaTable materials = new LuaTable();
        ToolMaterialApi.attach(materials);
        ArmorMaterialApi.attach(materials);
        BlockMaterialApi.attach(materials);
        module.set("materials", materials);

        LuaTable recipes = new LuaTable();
        module.set("recipes", recipes);

        LootTablesApi.attach(module);
        WorldGenApi.attach(module);
        MinecraftApi.attach(module);

        ChatApi.attach(module);
        ModuleApi.attach(module);

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

        if (source != null) {
            bindSource(module, new IdentityHashMap<LuaTable, Boolean>());
        }

        // Module Registration
        env.set("betamoon", module);
        LuaValue packageTable = env.get("package");
        if (packageTable.istable()) {
            packageTable.get("loaded").set("betamoon", module);
        }
        return module;
    }

    private void bindSource(LuaTable table, IdentityHashMap<LuaTable, Boolean> visited) {
        if (visited.put(table, Boolean.TRUE) != null) {
            return;
        }
        LuaValue[] keys = table.keys();
        for (int i = 0; i < keys.length; i++) {
            LuaValue value = table.get(keys[i]);
            if (value.isfunction()) {
                table.set(keys[i], new OwnedFunction(source, value));
            } else if (value.istable()) {
                bindSource(value.checktable(), visited);
            }
        }
    }

    private static final class OwnedFunction extends VarArgFunction {
        private final String source;
        private final LuaValue delegate;

        private OwnedFunction(String source, LuaValue delegate) {
            this.source = source;
            this.delegate = delegate;
        }

        @Override
        public Varargs invoke(Varargs arguments) {
            return ScriptExecution.invoke(source, delegate, arguments);
        }
    }
}
