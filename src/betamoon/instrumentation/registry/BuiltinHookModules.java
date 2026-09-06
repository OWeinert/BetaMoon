package betamoon.instrumentation.registry;

import betamoon.instrumentation.hooks.block.BlockBrokenHook;
import betamoon.instrumentation.hooks.block.BlockPlacedHook;

/** Registers the hooks shipped in the BetaMoon JAR. */
public final class BuiltinHookModules {
    private BuiltinHookModules() {
    }

    public static void registerAll(HookRegistry registry) {
        registry.registerModule(new BlockBrokenHook());
        registry.registerModule(new BlockPlacedHook());
    }
}
