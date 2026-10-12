package betamoon.instrumentation.hooks.worldgen;

import betamoon.instrumentation.api.AroundHookDefinition;
import betamoon.instrumentation.api.ClassRef;
import betamoon.instrumentation.api.HandlerRef;
import betamoon.instrumentation.api.HookModule;
import betamoon.instrumentation.api.HookRegistrar;
import betamoon.instrumentation.api.MethodRef;
import betamoon.instrumentation.api.ValueBinding;

/** Retries saved regional structure slices whenever their chunk becomes available. */
public final class RegionalStructureChunkHook implements HookModule {
    public static final String ID = "betamoon:regional_structure_chunk";
    private static final String CALLBACKS =
            "betamoon/instrumentation/hooks/worldgen/RegionalStructureChunkCallbacks";

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public void register(HookRegistrar registrar) {
        registrar.register(AroundHookDefinition.builder(ID,
                new MethodRef(new ClassRef("net/minecraft/src/Chunk"), "onChunkLoad", "()V"))
                .capture(HandlerRef.of(CALLBACKS, "entering", "()I"))
                .onReturn(HandlerRef.of(CALLBACKS, "loaded", "(Lnet/minecraft/src/Chunk;)V"),
                        ValueBinding.thisValue())
                .build());
    }
}
