package betamoon.instrumentation.registry;

import betamoon.instrumentation.hooks.block.BlockBreakGuardHook;
import betamoon.instrumentation.hooks.block.BlockBrokenHook;
import betamoon.instrumentation.hooks.block.BlockDisplayTickHook;
import betamoon.instrumentation.hooks.block.BlockPlacedHook;
import betamoon.instrumentation.hooks.block.BlockPowerHook;
import betamoon.instrumentation.hooks.block.ContentCallbackOverrideHook;
import betamoon.instrumentation.hooks.item.ItemHarvestHook;
import betamoon.instrumentation.hooks.item.ItemInteractionHook;
import betamoon.instrumentation.hooks.entity.EntityLifecycleHook;
import betamoon.instrumentation.hooks.entity.EntityNaturalSpawnHook;
import betamoon.instrumentation.hooks.entity.EntityTrackingHook;
import betamoon.instrumentation.hooks.fuel.FuelBurnTimeHook;
import betamoon.instrumentation.hooks.texture.TextureResourceHook;
import betamoon.instrumentation.hooks.model.ModelRenderHook;
import betamoon.runtime.RuntimeSide;

/** Registers the hooks shipped in the BetaMoon JAR. */
public final class BuiltinHookModules {
    private BuiltinHookModules() {
    }

    public static void registerAll(HookRegistry registry) {
        registerCommon(registry);
        registerClient(registry);
    }

    public static void registerForSide(HookRegistry registry, RuntimeSide side) {
        registerCommon(registry);
        if (side == RuntimeSide.DEDICATED_SERVER) {
            registerServer(registry);
        } else {
            registerClient(registry);
        }
    }

    private static void registerCommon(HookRegistry registry) {
        registry.registerModule(new ContentCallbackOverrideHook());
        registry.registerModule(new ItemHarvestHook());
        registry.registerModule(new BlockPowerHook());
        registry.registerModule(new EntityLifecycleHook());
        registry.registerModule(new EntityNaturalSpawnHook());
    }

    private static void registerClient(HookRegistry registry) {
        registry.registerModule(new BlockBrokenHook());
        registry.registerModule(new BlockDisplayTickHook());
        registry.registerModule(new BlockBreakGuardHook());
        registry.registerModule(new ItemInteractionHook());
        registry.registerModule(new BlockPlacedHook());
        registry.registerModule(new TextureResourceHook());
        registry.registerModule(new ModelRenderHook());
        registry.registerModule(new FuelBurnTimeHook());
    }

    private static void registerServer(HookRegistry registry) {
        registry.registerModule(new EntityTrackingHook());
    }
}
