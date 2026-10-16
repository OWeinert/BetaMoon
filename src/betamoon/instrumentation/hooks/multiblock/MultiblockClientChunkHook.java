package betamoon.instrumentation.hooks.multiblock;

import betamoon.instrumentation.api.AroundHookDefinition;
import betamoon.instrumentation.api.ClassRef;
import betamoon.instrumentation.api.HandlerRef;
import betamoon.instrumentation.api.HookModule;
import betamoon.instrumentation.api.HookRegistrar;
import betamoon.instrumentation.api.MethodRef;
import betamoon.instrumentation.api.ValueBinding;

/**
 * Remote chunk caches report every coordinate as existing; capture their actual
 * loaded chunks instead.
 */
public final class MultiblockClientChunkHook implements HookModule {
    public String getId() {
        return "betamoon:multiblock_remote_chunks";
    }

    public void register(HookRegistrar registrar) {
        String callbacks = "betamoon/instrumentation/hooks/multiblock/MultiblockChunkCallbacks";
        registrar.register(AroundHookDefinition
                .builder(getId(),
                        new MethodRef(new ClassRef("net/minecraft/src/ChunkProviderClient"), "prepareChunk",
                                "(II)Lnet/minecraft/src/Chunk;"))
                .capture(HandlerRef.of(callbacks, "entering", "()I"))
                .onReturn(HandlerRef.of(callbacks, "loaded", "(Lnet/minecraft/src/Chunk;)V"),
                        ValueBinding.returnValue())
                .build());
    }
}
