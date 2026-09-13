package betamoon.instrumentation.hooks.block;

import betamoon.instrumentation.api.CallRedirectHookDefinition;
import betamoon.instrumentation.api.ClassRef;
import betamoon.instrumentation.api.HandlerRef;
import betamoon.instrumentation.api.HookModule;
import betamoon.instrumentation.api.HookRegistrar;
import betamoon.instrumentation.api.MethodRef;

/**
 * Intercepts world display ticks while retaining virtual dispatch for the
 * original block behavior.
 */
public final class BlockDisplayTickHook implements HookModule {
    @Override
    public String getId() {
        return "betamoon:block_display_tick";
    }

    @Override
    public void register(HookRegistrar registrar) {
        registrar.register(new CallRedirectHookDefinition(getId(),
                new MethodRef(new ClassRef("net/minecraft/src/World"), "randomDisplayUpdates", "(III)V"),
                new MethodRef(new ClassRef("net/minecraft/src/Block"), "randomDisplayTick",
                        "(Lnet/minecraft/src/World;IIILjava/util/Random;)V"),
                HandlerRef.of("betamoon/luaapi/block/BlockDisplayTickOverrides", "display",
                        "(Lnet/minecraft/src/Block;Lnet/minecraft/src/World;IIILjava/util/Random;)V")));
    }
}
