package betamoon.instrumentation.hooks.block;

import betamoon.instrumentation.api.AroundHookDefinition;
import betamoon.instrumentation.api.ClassRef;
import betamoon.instrumentation.api.HandlerRef;
import betamoon.instrumentation.api.HookModule;
import betamoon.instrumentation.api.HookRegistrar;
import betamoon.instrumentation.api.MethodRef;
import betamoon.instrumentation.api.ValueBinding;

/**
 * Allows a custom solid machine to emit weak power as well as conduct incoming
 * power.
 */
public final class BlockPowerHook implements HookModule {
    private static final String OWNER = "betamoon/instrumentation/hooks/block/BlockPowerCallbacks";

    @Override
    public String getId() {
        return "betamoon:block_power";
    }

    @Override
    public void register(HookRegistrar registrar) {
        MethodRef target = new MethodRef(new ClassRef("net/minecraft/src/World"), "isBlockIndirectlyProvidingPowerTo",
                "(IIII)Z");
        registrar.register(AroundHookDefinition.builder(getId(), target)
                .capture(HandlerRef.of(OWNER, "emission", "(Lnet/minecraft/src/World;IIII)I"), ValueBinding.thisValue(),
                        ValueBinding.argument(0), ValueBinding.argument(1), ValueBinding.argument(2),
                        ValueBinding.argument(3))
                .onReturn(HandlerRef.of(OWNER, "result", "(ZI)Z"), ValueBinding.returnValue(),
                        ValueBinding.capturedValue())
                .skipWhenCapturedNonZero().build());
    }

}
