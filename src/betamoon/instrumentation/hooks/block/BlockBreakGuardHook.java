package betamoon.instrumentation.hooks.block;

import betamoon.instrumentation.api.AroundHookDefinition;
import betamoon.instrumentation.api.ClassRef;
import betamoon.instrumentation.api.HandlerRef;
import betamoon.instrumentation.api.HookModule;
import betamoon.instrumentation.api.HookRegistrar;
import betamoon.instrumentation.api.MethodRef;
import betamoon.instrumentation.api.ValueBinding;

/**
 * Checks break permission before the survival controller spends durability or
 * drops items.
 */
public final class BlockBreakGuardHook implements HookModule {
    private static final String OWNER = "betamoon/instrumentation/hooks/block/BlockBreakGuardCallbacks";

    @Override
    public String getId() {
        return "betamoon:block_break_guard";
    }

    @Override
    public void register(HookRegistrar registrar) {
        MethodRef target = BlockHookTargets.SEND_BLOCK_REMOVED
                .implementedBy(new ClassRef("net/minecraft/src/PlayerControllerSP"));
        registrar.register(AroundHookDefinition.builder(getId(), target)
                .capture(HandlerRef.of(OWNER, "before", "(Lnet/minecraft/client/Minecraft;IIII)I"),
                        ValueBinding.instanceField(BlockHookTargets.PLAYER_CONTROLLER_MC), ValueBinding.argument(0),
                        ValueBinding.argument(1), ValueBinding.argument(2), ValueBinding.argument(3))
                .onReturn(HandlerRef.of(OWNER, "after", "(ZI)Z"), ValueBinding.returnValue(),
                        ValueBinding.capturedValue())
                .skipWhenCapturedNonZero().build());
    }

}
