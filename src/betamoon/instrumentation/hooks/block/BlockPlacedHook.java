package betamoon.instrumentation.hooks.block;

import betamoon.instrumentation.api.AroundHookDefinition;
import betamoon.instrumentation.api.HandlerRef;
import betamoon.instrumentation.api.HookModule;
import betamoon.instrumentation.api.HookRegistrar;
import betamoon.instrumentation.api.ValueBinding;

/** Registers observation of successful player-controller block placements. */
public final class BlockPlacedHook implements HookModule {
    public static final String ID = "betamoon:block_placed";
    private static final String CALLBACK_OWNER =
        "betamoon/instrumentation/hooks/block/BlockPlacedCallbacks";
    private static final String SNAPSHOT_DESCRIPTOR =
        "Lbetamoon/instrumentation/hooks/block/BlockSnapshot;";

    public String getId() {
        return ID;
    }

    public void register(HookRegistrar registrar) {
        HandlerRef capture = HandlerRef.of(CALLBACK_OWNER, "beforeBlockPlaced",
            "(Lnet/minecraft/client/Minecraft;Lnet/minecraft/src/World;IIII)" + SNAPSHOT_DESCRIPTOR);
        HandlerRef completed = HandlerRef.of(CALLBACK_OWNER, "afterBlockPlaced",
            "(Z" + SNAPSHOT_DESCRIPTOR + ")V");

        registrar.register(AroundHookDefinition.builder(ID, BlockHookTargets.SEND_PLACE_BLOCK)
            .capture(capture,
                ValueBinding.instanceField(BlockHookTargets.PLAYER_CONTROLLER_MC),
                ValueBinding.argument(1),
                ValueBinding.argument(3),
                ValueBinding.argument(4),
                ValueBinding.argument(5),
                ValueBinding.argument(6))
            .onReturn(completed,
                ValueBinding.returnValue(),
                ValueBinding.capturedValue())
            .build());
    }
}
