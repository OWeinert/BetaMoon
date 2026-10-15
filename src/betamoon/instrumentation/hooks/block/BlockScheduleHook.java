package betamoon.instrumentation.hooks.block;

import betamoon.instrumentation.api.AroundHookDefinition;
import betamoon.instrumentation.api.ClassRef;
import betamoon.instrumentation.api.HandlerRef;
import betamoon.instrumentation.api.HookModule;
import betamoon.instrumentation.api.HookRegistrar;
import betamoon.instrumentation.api.MethodRef;
import betamoon.instrumentation.api.ValueBinding;

/**
 * Restores repeating block schedules and retries native admission around chunk
 * transitions.
 */
public final class BlockScheduleHook implements HookModule {
    public static final String ID = "betamoon:block_schedule";
    private static final String CALLBACKS = "betamoon/instrumentation/hooks/block/BlockScheduleCallbacks";

    public String getId() {
        return ID;
    }

    public void register(HookRegistrar registrar) {
        for (String operation : new String[]{"loaded", "unloaded"}) {
            registrar.register(AroundHookDefinition
                    .builder(ID + ":" + operation,
                            new MethodRef(new ClassRef("net/minecraft/src/Chunk"),
                                    operation.equals("loaded") ? "onChunkLoad" : "onChunkUnload", "()V"))
                    .capture(HandlerRef.of(CALLBACKS, "entering", "()I"))
                    .onReturn(HandlerRef.of(CALLBACKS, operation, "(Lnet/minecraft/src/Chunk;)V"),
                            ValueBinding.thisValue())
                    .build());
        }
        registrar.register(AroundHookDefinition
                .builder(ID + ":retry", new MethodRef(new ClassRef("net/minecraft/src/World"), "TickUpdates", "(Z)Z"))
                .capture(HandlerRef.of(CALLBACKS, "retry", "(Lnet/minecraft/src/World;)I"), ValueBinding.thisValue())
                .onReturn(HandlerRef.of(CALLBACKS, "afterRetry", "()V")).build());
    }
}
