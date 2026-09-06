package betamoon.instrumentation.hooks.block;

import betamoon.instrumentation.api.AroundHookDefinition;
import betamoon.instrumentation.api.HandlerRef;
import betamoon.instrumentation.api.HookModule;
import betamoon.instrumentation.api.HookRegistrar;
import betamoon.instrumentation.api.ValueBinding;

/** Registers observation of successful player-controller block removals. */
public final class BlockBrokenHook implements HookModule {
    public static final String ID = "betamoon:block_broken";
    private static final String CALLBACK_OWNER =
        "betamoon/instrumentation/hooks/block/BlockBrokenCallbacks";
    private static final String SNAPSHOT_DESCRIPTOR =
        "Lbetamoon/instrumentation/hooks/block/BlockSnapshot;";

    public String getId() {
        return ID;
    }

    public void register(HookRegistrar registrar) {
        HandlerRef capture = HandlerRef.of(CALLBACK_OWNER, "beforeBlockBroken",
            "(Lnet/minecraft/client/Minecraft;IIII)" + SNAPSHOT_DESCRIPTOR);
        HandlerRef completed = HandlerRef.of(CALLBACK_OWNER, "afterBlockBroken",
            "(Z" + SNAPSHOT_DESCRIPTOR + ")V");

        registrar.register(AroundHookDefinition.builder(ID, BlockHookTargets.SEND_BLOCK_REMOVED)
            .capture(capture,
                ValueBinding.instanceField(BlockHookTargets.PLAYER_CONTROLLER_MC),
                ValueBinding.argument(0),
                ValueBinding.argument(1),
                ValueBinding.argument(2),
                ValueBinding.argument(3))
            .onReturn(completed,
                ValueBinding.returnValue(),
                ValueBinding.capturedValue())
            .build());
    }
}
