package betamoon.instrumentation.hooks.entity;

import betamoon.instrumentation.api.AroundHookDefinition;
import betamoon.instrumentation.api.ClassRef;
import betamoon.instrumentation.api.HandlerRef;
import betamoon.instrumentation.api.HookModule;
import betamoon.instrumentation.api.HookRegistrar;
import betamoon.instrumentation.api.MethodRef;
import betamoon.instrumentation.api.ValueBinding;

/** Observes native chunk unload and simulation suspension for custom entities. */
public final class EntityLifecycleHook implements HookModule {
    public static final String ID = "betamoon:entity_lifecycle";
    private static final String CALLBACKS =
            "betamoon/instrumentation/hooks/entity/EntityLifecycleCallbacks";

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public void register(HookRegistrar registrar) {
        registrar.register(AroundHookDefinition.builder(ID + ":chunk_unload",
                new MethodRef(new ClassRef("net/minecraft/src/Chunk"), "onChunkUnload", "()V"))
                .capture(HandlerRef.of(CALLBACKS, "entering", "()I"))
                .onReturn(HandlerRef.of(CALLBACKS, "chunkUnloaded", "(Lnet/minecraft/src/Chunk;)V"),
                        ValueBinding.thisValue())
                .build());
        registrar.register(AroundHookDefinition.builder(ID + ":simulation",
                new MethodRef(new ClassRef("net/minecraft/src/World"), "updateEntityWithOptionalForce",
                        "(Lnet/minecraft/src/Entity;Z)V"))
                .capture(HandlerRef.of(CALLBACKS, "beforeUpdate", "(Lnet/minecraft/src/Entity;)I"),
                        ValueBinding.argument(0))
                .onReturn(HandlerRef.of(CALLBACKS, "afterUpdate", "(Lnet/minecraft/src/Entity;ZI)V"),
                        ValueBinding.argument(0), ValueBinding.argument(1), ValueBinding.capturedValue())
                .build());
    }
}
