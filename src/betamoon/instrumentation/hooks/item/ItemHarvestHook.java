package betamoon.instrumentation.hooks.item;

import betamoon.instrumentation.api.AroundHookDefinition;
import betamoon.instrumentation.api.ClassRef;
import betamoon.instrumentation.api.HandlerRef;
import betamoon.instrumentation.api.HookModule;
import betamoon.instrumentation.api.HookRegistrar;
import betamoon.instrumentation.api.MethodRef;
import betamoon.instrumentation.api.ValueBinding;

/**
 * Applies declared multi-class tool rules before Forge's single-class fallback.
 */
public final class ItemHarvestHook implements HookModule {
    private static final String OWNER = "betamoon/instrumentation/hooks/item/ItemHarvestCallbacks";

    @Override
    public String getId() {
        return "betamoon:item_harvest";
    }

    @Override
    public void register(HookRegistrar registrar) {
        MethodRef target = new MethodRef(new ClassRef("forge/ForgeHooks"), "canHarvestBlock",
                "(Lnet/minecraft/src/Block;Lnet/minecraft/src/EntityPlayer;I)Z");
        registrar.register(AroundHookDefinition.builder(getId(), target)
                .capture(
                        HandlerRef.of(OWNER, "permission",
                                "(Lnet/minecraft/src/Block;Lnet/minecraft/src/EntityPlayer;I)I"),
                        ValueBinding.argument(0), ValueBinding.argument(1), ValueBinding.argument(2))
                .onReturn(HandlerRef.of(OWNER, "result", "(ZI)Z"), ValueBinding.returnValue(),
                        ValueBinding.capturedValue())
                .skipWhenCapturedNonZero().build());
    }

}
