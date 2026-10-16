package betamoon.instrumentation.hooks.multiblock;

import betamoon.instrumentation.api.AroundHookDefinition;
import betamoon.instrumentation.api.ClassRef;
import betamoon.instrumentation.api.HandlerRef;
import betamoon.instrumentation.api.HookModule;
import betamoon.instrumentation.api.HookRegistrar;
import betamoon.instrumentation.api.MethodRef;
import betamoon.instrumentation.api.ValueBinding;

/**
 * Observes the common setters, including non-notifying changes from explosions,
 * fluids and worldgen.
 */
public final class MultiblockChunkHook implements HookModule {
    private static final String CALLBACKS = "betamoon/instrumentation/hooks/multiblock/MultiblockChunkCallbacks";

    public String getId() {
        return "betamoon:multiblock";
    }

    public void register(HookRegistrar registrar) {
        for (String operation : new String[]{"id", "state", "metadata"}) {
            boolean state = operation.equals("state");
            String method = state
                    ? "setBlockIDWithMetadata"
                    : operation.equals("id") ? "setBlockID" : "setBlockMetadata";
            String descriptor = state ? "(IIIII)Z" : operation.equals("id") ? "(IIII)Z" : "(IIII)V";
            ValueBinding[] capture = state
                    ? new ValueBinding[]{ValueBinding.thisValue(), ValueBinding.argument(0), ValueBinding.argument(1),
                            ValueBinding.argument(2), ValueBinding.argument(3), ValueBinding.argument(4)}
                    : new ValueBinding[]{ValueBinding.thisValue(), ValueBinding.argument(0), ValueBinding.argument(1),
                            ValueBinding.argument(2), ValueBinding.argument(3)};
            registrar.register(AroundHookDefinition
                    .builder(getId() + ":" + operation,
                            new MethodRef(new ClassRef("net/minecraft/src/Chunk"), method, descriptor))
                    .capture(
                            HandlerRef.of(CALLBACKS, operation,
                                    state ? "(Lnet/minecraft/src/Chunk;IIIII)I" : "(Lnet/minecraft/src/Chunk;IIII)I"),
                            capture)
                    .onReturn(HandlerRef.of(CALLBACKS, "changed", "(Lnet/minecraft/src/Chunk;IIII)V"),
                            ValueBinding.thisValue(), ValueBinding.argument(0), ValueBinding.argument(1),
                            ValueBinding.argument(2), ValueBinding.capturedValue())
                    .build());
        }
        for (String operation : new String[]{"loaded", "unloaded"}) {
            registrar.register(AroundHookDefinition
                    .builder(getId() + ":" + operation,
                            new MethodRef(new ClassRef("net/minecraft/src/Chunk"),
                                    operation.equals("loaded") ? "onChunkLoad" : "onChunkUnload", "()V"))
                    .capture(HandlerRef.of(CALLBACKS, "entering", "()I"))
                    .onReturn(HandlerRef.of(CALLBACKS, operation, "(Lnet/minecraft/src/Chunk;)V"),
                            ValueBinding.thisValue())
                    .build());
        }
    }
}
