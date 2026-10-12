package betamoon.instrumentation.hooks.fluid;

import betamoon.instrumentation.api.AroundHookDefinition;
import betamoon.instrumentation.api.ClassRef;
import betamoon.instrumentation.api.HandlerRef;
import betamoon.instrumentation.api.HookModule;
import betamoon.instrumentation.api.HookRegistrar;
import betamoon.instrumentation.api.MethodRef;
import betamoon.instrumentation.api.ValueBinding;

/** Routes vanilla fluid hardening through BetaMoon's identity-aware evaluator. */
public final class FluidInteractionHook implements HookModule {
    public static final String ID = "betamoon:fluid_interactions";
    private static final String CALLBACKS = "betamoon/instrumentation/hooks/fluid/FluidInteractionCallbacks";

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public void register(HookRegistrar registrar) {
        MethodRef target = new MethodRef(new ClassRef("net/minecraft/src/BlockFluid"), "checkForHarden",
                "(Lnet/minecraft/src/World;III)V");
        registrar.register(AroundHookDefinition.builder(ID, target)
                .capture(HandlerRef.of(CALLBACKS, "beforeHarden",
                                "(Lnet/minecraft/src/BlockFluid;Lnet/minecraft/src/World;III)I"),
                        ValueBinding.thisValue(), ValueBinding.argument(0), ValueBinding.argument(1),
                        ValueBinding.argument(2), ValueBinding.argument(3))
                .onReturn(HandlerRef.of(CALLBACKS, "complete", "()V"))
                .skipWhenCapturedNonZero().build());
    }
}
