package betamoon.instrumentation.registry;

import betamoon.instrumentation.hooks.block.BlockBreakGuardHook;
import betamoon.instrumentation.hooks.block.BlockBrokenHook;
import betamoon.instrumentation.hooks.block.BlockDisplayTickHook;
import betamoon.instrumentation.hooks.block.BlockPlacedHook;
import betamoon.instrumentation.hooks.block.BlockPowerHook;
import betamoon.instrumentation.hooks.block.ContentCallbackOverrideHook;
import betamoon.instrumentation.hooks.item.ItemHarvestHook;
import betamoon.instrumentation.hooks.item.ItemInteractionHook;
import betamoon.instrumentation.hooks.texture.TextureResourceHook;
import betamoon.instrumentation.hooks.model.ModelRenderHook;

/** Registers the hooks shipped in the BetaMoon JAR. */
public final class BuiltinHookModules {
    private BuiltinHookModules() {
    }

    public static void registerAll(HookRegistry registry) {
        registry.registerModule(new BlockBrokenHook());
        registry.registerModule(new BlockDisplayTickHook());
        registry.registerModule(new ContentCallbackOverrideHook());
        registry.registerModule(new ItemHarvestHook());
        registry.registerModule(new BlockPowerHook());
        registry.registerModule(new BlockBreakGuardHook());
        registry.registerModule(new ItemInteractionHook());
        registry.registerModule(new BlockPlacedHook());
        registry.registerModule(new TextureResourceHook());
        registry.registerModule(new ModelRenderHook());
    }
}
